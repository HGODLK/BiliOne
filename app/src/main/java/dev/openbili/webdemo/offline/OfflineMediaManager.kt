package dev.openbili.webdemo.offline

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import dev.openbili.webdemo.api.BiliDanmakuApi
import dev.openbili.webdemo.api.BiliHttpClient
import dev.openbili.webdemo.api.BiliSubtitleApi
import dev.openbili.webdemo.api.BiliVideoApi
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.UserInfo
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(UnstableApi::class)
class OfflineMediaManager private constructor(context: Context) {
  private val appContext = context.applicationContext
  private val store = OfflineMediaStore(appContext)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val preparationJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
  private val databaseProvider = StandaloneDatabaseProvider(appContext)
  private val componentLock = Any()
  private val migrationMutex = Mutex()
  private val migrationInProgress = AtomicBoolean(false)
  private val downloaderExecutor = Executors.newFixedThreadPool(2)
  private val upstreamFactory =
    DefaultDataSource.Factory(
      appContext,
      DefaultHttpDataSource.Factory()
        .setUserAgent(
          "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
        )
        .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com/")),
    )

  @Volatile private var activeRootDirectory: File = initialRootDirectory()
  @Volatile private var cacheBackendRoot: File = usableBackendRoot(activeRootDirectory)
  @Volatile private var mediaCache: SimpleCache = createMediaCache(cacheBackendRoot)
  @Volatile private var activeDownloadManager: DownloadManager = createDownloadManager(mediaCache)

  val rootDirectory: File
    get() = activeRootDirectory

  val cacheOnlyDataSourceFactory: CacheDataSource.Factory
    get() =
      CacheDataSource.Factory()
        .setCache(mediaCache)
        .setCacheKeyFactory { dataSpec -> dataSpec.key ?: dataSpec.uri.toString() }
        .setUpstreamDataSourceFactory(null)
        .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)

  internal val downloadManager: DownloadManager
    get() = activeDownloadManager

  private fun createMediaCache(root: File): SimpleCache =
    SimpleCache(
      File(root, "cache").apply { mkdirs() },
      NoOpCacheEvictor(),
      databaseProvider,
    )

  private fun createDownloadManager(cache: SimpleCache): DownloadManager =
    DownloadManager(
        appContext,
        DefaultDownloadIndex(databaseProvider, "offline_media"),
        DefaultDownloaderFactory(
          CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheKeyFactory { dataSpec -> dataSpec.key ?: dataSpec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
          downloaderExecutor,
        ),
      )
      .apply {
        maxParallelDownloads = 2
        minRetryCount = 4
        requirements = currentRequirements()
      }

  init {
    if (storageAvailable()) scope.launch { recoverPersistedWork() }
  }

  val wifiOnly: Boolean
    get() = store.wifiOnly

  val currentStorageLocationId: String
    get() = store.storageLocationId

  fun storageAvailable(): Boolean {
    if (store.storageLocationId == OfflineMediaStore.INTERNAL_STORAGE_ID) return true
    val root = activeRootDirectory
    val parent = root.parentFile ?: return false
    return parent.exists() && parent.canWrite()
  }

  fun currentStorageLabel(): String {
    val currentId = store.storageLocationId
    if (currentId == OfflineMediaStore.INTERNAL_STORAGE_ID) return "本机存储"
    return availableStorageLocations().firstOrNull { it.id == currentId }?.label ?: "SD 卡不可用"
  }

  fun availableStorageLocations(): List<OfflineStorageLocation> {
    val selectedId = store.storageLocationId
    val internalRoot = File(appContext.filesDir, OFFLINE_DIRECTORY_NAME)
    val locations =
      mutableListOf(
        OfflineStorageLocation(
          id = OfflineMediaStore.INTERNAL_STORAGE_ID,
          label = "本机存储",
          rootPath = internalRoot.absolutePath,
          removable = false,
          availableBytes = availableBytes(internalRoot),
          selected = selectedId == OfflineMediaStore.INTERNAL_STORAGE_ID,
        )
      )
    val storageManager = appContext.getSystemService(StorageManager::class.java)
    appContext
      .getExternalFilesDirs(null)
      .asSequence()
      .filterNotNull()
      .filter { directory ->
        runCatching { Environment.isExternalStorageRemovable(directory) }.getOrDefault(false) &&
          directory.exists() &&
          directory.canWrite()
      }
      .distinctBy(File::getAbsolutePath)
      .forEachIndexed { index, directory ->
        val volume =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { storageManager?.getStorageVolume(directory) }.getOrNull()
          } else {
            null
          }
        val volumeKey =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            volume?.uuid?.takeIf(String::isNotBlank)
          } else {
            null
          } ?: directory.absolutePath.hashCode().toString()
        val id = "sd:$volumeKey"
        val root = File(directory, OFFLINE_DIRECTORY_NAME)
        val description =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { volume?.getDescription(appContext) }.getOrNull().orEmpty().trim()
          } else {
            ""
          }
        locations +=
          OfflineStorageLocation(
            id = id,
            label = description.ifBlank { if (index == 0) "SD 卡" else "SD 卡 ${index + 1}" },
            rootPath = root.absolutePath,
            removable = true,
            availableBytes = availableBytes(directory),
            selected = selectedId == id,
          )
      }
    return locations
  }

  suspend fun migrateStorage(
    target: OfflineStorageLocation,
    onProgress: (OfflineStorageMigrationProgress) -> Unit,
  ): Result<Unit> = migrationMutex.withLock {
    withContext(Dispatchers.IO) {
      runCatching {
        check(target.id == OfflineMediaStore.INTERNAL_STORAGE_ID || target.removable) {
          "不支持的缓存位置"
        }
        val knownTarget =
          availableStorageLocations().firstOrNull { it.id == target.id } ?: error("目标 SD 卡当前不可用")
        val sourceRoot = activeRootDirectory
        val targetRoot = File(knownTarget.rootPath)
        if (sourceRoot.absolutePath == targetRoot.absolutePath) return@runCatching
        check(migrationInProgress.compareAndSet(false, true)) { "缓存正在迁移" }
        var componentsReleased = false
        var targetActivated = false
        val temporaryRoot =
          File(targetRoot.parentFile ?: error("目标路径无效"), "${OFFLINE_DIRECTORY_NAME}_migrating")
        try {
          val sourceBytes = directoryBytes(sourceRoot)
          check(knownTarget.availableBytes > sourceBytes + MIGRATION_FREE_SPACE_RESERVE_BYTES) {
            "目标存储空间不足"
          }
          activeDownloadManager.pauseDownloads()
          preparationJobs.values.forEach(Job::cancel)
          preparationJobs.clear()
          appContext.stopService(Intent(appContext, OfflineDownloadService::class.java))
          delay(MIGRATION_QUIET_PERIOD_MS)
          synchronized(componentLock) {
            activeDownloadManager.release()
            mediaCache.release()
            componentsReleased = true
          }
          if (temporaryRoot.exists()) temporaryRoot.deleteRecursively()
          check(temporaryRoot.mkdirs()) { "无法创建迁移目录" }
          copyDirectoryWithProgress(sourceRoot, temporaryRoot, sourceBytes, onProgress)
          check(directoryBytes(temporaryRoot) == sourceBytes) { "缓存校验失败" }
          if (targetRoot.exists()) {
            check(targetRoot.listFiles().isNullOrEmpty()) { "目标位置已有缓存内容" }
            check(targetRoot.delete()) { "无法准备目标缓存目录" }
          }
          check(temporaryRoot.renameTo(targetRoot)) { "无法完成缓存迁移" }
          store.storageLocationId = knownTarget.id
          store.storageRootPath = targetRoot.absolutePath
          synchronized(componentLock) {
            activeRootDirectory = targetRoot
            cacheBackendRoot = targetRoot
            mediaCache = createMediaCache(targetRoot)
            activeDownloadManager = createDownloadManager(mediaCache)
            targetActivated = true
          }
          if (sourceRoot.exists()) sourceRoot.deleteRecursively()
          onProgress(OfflineStorageMigrationProgress(sourceBytes, sourceBytes))
          recoverPersistedWork()
        } catch (error: Throwable) {
          if (componentsReleased && !targetActivated) {
            synchronized(componentLock) {
              cacheBackendRoot = usableBackendRoot(sourceRoot)
              mediaCache = createMediaCache(cacheBackendRoot)
              activeDownloadManager = createDownloadManager(mediaCache)
            }
            recoverPersistedWork()
          }
          if (!targetActivated && temporaryRoot.exists()) temporaryRoot.deleteRecursively()
          throw error
        } finally {
          migrationInProgress.set(false)
        }
      }
    }
  }

  fun entries(): List<OfflineMediaEntry> = store.entries()

  fun entry(id: String): OfflineMediaEntry? = store.entry(id)

  fun entryFromPlaybackUri(uri: String): OfflineMediaEntry? {
    if (!isOfflineUri(uri)) return null
    val id =
      runCatching { Uri.parse(uri).lastPathSegment?.let(Uri::decode) }.getOrNull() ?: return null
    return store.entry(id)
  }

  fun loadDanmaku(entry: OfflineMediaEntry): List<DanmakuItem> {
    val file =
      entry.danmakuRelativePath.takeIf(String::isNotBlank)?.let { File(rootDirectory, it) }
        ?: return emptyList()
    if (!file.isFile) return emptyList()
    return runCatching {
        val array = JSONArray(file.readText(Charsets.UTF_8))
        buildList {
          for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
              DanmakuItem(
                timeMs = item.optLong("timeMs"),
                type = item.optInt("type", 1),
                fontSize = item.optInt("fontSize", 25),
                color = item.optInt("color", 0xFFFFFF),
                content = item.optString("content"),
                sourceId = item.optString("sourceId").takeIf { it.isNotBlank() && it != "null" },
                colorful = item.optInt("colorful"),
              )
            )
          }
        }
      }
      .getOrDefault(emptyList())
  }

  fun snapshots(): List<OfflineMediaSnapshot> {
    ensureStorageBackend()
    if (!storageAvailable()) {
      return store.entries().map { entry ->
        OfflineMediaSnapshot(
          entry = entry,
          state = OfflineTransferState.UNAVAILABLE,
          progressPercent = 0f,
          bytesDownloaded = 0L,
          totalBytes = 0L,
          failureReason = "缓存所在的 SD 卡当前不可用",
        )
      }
    }
    val networkRequirementsMet = requirementsMet()
    return store.entries().map { entry ->
      if (entry.entitlementState == OfflineEntitlementState.REVOKED) {
        OfflineMediaSnapshot(entry, OfflineTransferState.UNAVAILABLE, 0f, 0L, 0L)
      } else if (entry.videoUrl.isBlank()) {
        OfflineMediaSnapshot(
          entry,
          when {
            entry.preparationPaused -> OfflineTransferState.PAUSED
            entry.preparationError.isBlank() && networkRequirementsMet ->
              OfflineTransferState.PREPARING
            entry.preparationError.isBlank() -> OfflineTransferState.QUEUED
            else -> OfflineTransferState.FAILED
          },
          0f,
          0L,
          0L,
          entry.preparationError,
        )
      } else {
        snapshotForDownloads(entry)
      }
    }
  }

  fun enqueue(request: OfflineMediaRequest): Boolean {
    if (!storageAvailable() || migrationInProgress.get()) return false
    if (request.requiresVip && request.accountMid <= 0L) return false
    val id = offlineMediaId(request.kind, request.bvid, request.cid, request.episodeId)
    val preparing =
      OfflineMediaEntry(
        id = id,
        kind = request.kind,
        accountMid = request.accountMid,
        title = request.title,
        partTitle = request.partTitle,
        coverUrl = request.coverUrl,
        coverRelativePath = "",
        bvid = request.bvid,
        aid = request.aid,
        cid = request.cid,
        pageNumber = request.pageNumber,
        collectionId = request.collectionId,
        seasonId = request.seasonId,
        episodeId = request.episodeId,
        durationMs = request.durationMs,
        qualityId = request.qualityId,
        qualityLabel = request.qualityLabel.ifBlank { "清晰度 ${request.qualityId}" },
        includeDanmaku = request.includeDanmaku,
        includeSubtitles = request.includeSubtitles,
        requiresVip = request.requiresVip,
        entitlementState =
          if (request.requiresVip) OfflineEntitlementState.ACTIVE else OfflineEntitlementState.FREE,
        entitlementValidUntilMs =
          if (request.requiresVip) System.currentTimeMillis() + VIP_AUTHORIZATION_LEASE_MS
          else Long.MAX_VALUE,
        createdAtMs = System.currentTimeMillis(),
      )
    if (!store.insertIfAbsent(preparing)) return false
    startPreparation(preparing)
    return true
  }

  fun pause(id: String) {
    val entry = store.entry(id) ?: return
    if (entry.videoUrl.isBlank()) {
      preparationJobs.remove(id)?.cancel()
      store.upsert(entry.copy(preparationPaused = true, preparationError = ""))
      return
    }
    trackIds(id).forEach { trackId ->
      DownloadService.sendSetStopReason(
        appContext,
        OfflineDownloadService::class.java,
        trackId,
        USER_PAUSE_STOP_REASON,
        false,
      )
    }
  }

  fun resume(id: String) {
    if (!storageAvailable() || migrationInProgress.get()) return
    val entry = store.entry(id) ?: return
    if (entry.entitlementState == OfflineEntitlementState.REVOKED) return
    if (entry.videoUrl.isBlank() && entry.preparationPaused) {
      val resumed = entry.copy(preparationPaused = false, preparationError = "")
      store.upsert(resumed)
      startPreparation(resumed)
      return
    }
    val downloads = trackIds(id).mapNotNull(::download)
    if (
      downloads.any { it.state == Download.STATE_FAILED } ||
        downloads.size < requiredTrackCount(entry)
    ) {
      store.upsert(entry.copy(preparationError = "", videoUrl = "", audioUrl = ""))
      startPreparation(entry.copy(preparationError = ""))
      return
    }
    trackIds(id).forEach { trackId ->
      DownloadService.sendSetStopReason(
        appContext,
        OfflineDownloadService::class.java,
        trackId,
        Download.STOP_REASON_NONE,
        false,
      )
    }
  }

  fun remove(id: String) {
    preparationJobs.remove(id)?.cancel()
    trackIds(id).forEach { trackId ->
      DownloadService.sendRemoveDownload(
        appContext,
        OfflineDownloadService::class.java,
        trackId,
        false,
      )
    }
    store.remove(id)
    scope.launch { File(rootDirectory, "metadata/$id").deleteRecursively() }
  }

  fun setWifiOnly(enabled: Boolean) {
    store.wifiOnly = enabled
    DownloadService.sendSetRequirements(
      appContext,
      OfflineDownloadService::class.java,
      currentRequirements(),
      false,
    )
  }

  /**
   * 只用已确认的在线账号响应来核对本地缓存的会员媒体。登出和切换账号会锁定卡片而
   * 不删除字节；同一账号被确认过期时撤销媒体字节，但保留卡片元数据和封面。
   */
  fun reconcileEntitlements(userInfo: UserInfo?) {
    val entries = store.entries().filter(OfflineMediaEntry::requiresVip)
    if (entries.isEmpty()) return
    entries.forEach { entry ->
      when {
        userInfo == null || !userInfo.isLogin || userInfo.mid != entry.accountMid -> {
          if (entry.entitlementState != OfflineEntitlementState.REVOKED) {
            store.upsert(entry.copy(entitlementState = OfflineEntitlementState.LOCKED))
          }
        }
        !userInfo.vipActive -> revokePremiumMedia(entry)
        entry.entitlementState != OfflineEntitlementState.REVOKED -> {
          val active =
            entry.copy(
              entitlementState = OfflineEntitlementState.ACTIVE,
              entitlementValidUntilMs = System.currentTimeMillis() + VIP_AUTHORIZATION_LEASE_MS,
            )
          store.upsert(active)
          if (entry.entitlementState == OfflineEntitlementState.LOCKED) restartLockedWork(active)
        }
      }
    }
  }

  fun canPlay(entry: OfflineMediaEntry, currentAccountMid: Long, vipActive: Boolean): Boolean =
    when {
      !storageAvailable() -> false
      entry.entitlementState == OfflineEntitlementState.REVOKED -> false
      !entry.requiresVip -> true
      currentAccountMid != entry.accountMid || !vipActive -> false
      entry.entitlementValidUntilMs < System.currentTimeMillis() -> false
      else -> true
    }

  fun totalBytes(): Long =
    runCatching { mediaCache.cacheSpace }.getOrDefault(0L) +
      File(rootDirectory, "metadata").walkTopDown().filter(File::isFile).sumOf { file ->
        runCatching { file.length() }.getOrDefault(0L)
      }

  private suspend fun prepareAndQueue(seed: OfflineMediaEntry) {
    runCatching {
        while (!requirementsMet() || !storageAvailable()) delay(REQUIREMENTS_RECHECK_MS)
        currentCoroutineContext().ensureActive()
        val rawPlayData =
          when (seed.kind) {
            OfflineMediaKind.VIDEO -> BiliVideoApi.getPlayUrl(seed.bvid, seed.cid)
            OfflineMediaKind.BANGUMI -> BiliVideoApi.getBangumiPlayUrl(seed.episodeId, seed.cid)
          } ?: error("当前账号无法获取该视频的播放地址")
        val stream = selectStream(rawPlayData, seed.qualityId)
        val audio = rawPlayData.dashAudio
        val metadataDirectory = File(rootDirectory, "metadata/${seed.id}").apply { mkdirs() }
        val coverPath = downloadCover(seed.coverUrl, metadataDirectory)
        val danmakuPath =
          if (seed.includeDanmaku)
            runCatching { saveDanmaku(seed, metadataDirectory) }.getOrDefault("")
          else ""
        val subtitles =
          if (seed.includeSubtitles) saveSubtitles(seed, metadataDirectory) else emptyList()
        currentCoroutineContext().ensureActive()
        val latest = store.entry(seed.id) ?: return@runCatching
        if (latest.entitlementState == OfflineEntitlementState.REVOKED || latest.preparationPaused)
          return@runCatching
        val entry =
          seed.copy(
            coverRelativePath = coverPath.ifBlank { latest.coverRelativePath },
            qualityId = stream.id,
            qualityLabel = stream.quality,
            videoUrl = stream.url,
            audioUrl = audio?.url.orEmpty(),
            videoCacheKey = "offline:${seed.id}:video",
            audioCacheKey = audio?.let { "offline:${seed.id}:audio" }.orEmpty(),
            videoMimeType = stream.mimeType.ifBlank { "video/mp4" },
            audioMimeType = audio?.mimeType?.ifBlank { "audio/mp4" } ?: "audio/mp4",
            danmakuRelativePath =
              if (seed.includeDanmaku) danmakuPath.ifBlank { latest.danmakuRelativePath } else "",
            subtitles =
              if (seed.includeSubtitles) subtitles.ifEmpty { latest.subtitles } else emptyList(),
            entitlementState = latest.entitlementState,
            entitlementValidUntilMs = latest.entitlementValidUntilMs,
            preparationPaused = false,
            preparationError = "",
          )
        store.upsert(entry)
        removeTrackDownloads(seed.id)
        queueTrack(
          id = videoTrackId(seed.id),
          uri = entry.videoUrl,
          mimeType = entry.videoMimeType,
          cacheKey = entry.videoCacheKey,
        )
        if (entry.audioUrl.isNotBlank()) {
          queueTrack(
            id = audioTrackId(seed.id),
            uri = entry.audioUrl,
            mimeType = entry.audioMimeType,
            cacheKey = entry.audioCacheKey,
          )
        }
      }
      .onFailure { error ->
        if (error is CancellationException) throw error
        // 删除或撤销权益可能与进行中的元数据请求竞争。终局性本地操作之后绝不
        // 重建卡片。
        val latest = store.entry(seed.id) ?: return@onFailure
        if (latest.entitlementState == OfflineEntitlementState.REVOKED) return@onFailure
        removeTrackDownloads(seed.id)
        store.upsert(
          latest.copy(
            preparationPaused = false,
            preparationError = error.message ?: "缓存准备失败",
            videoUrl = "",
            audioUrl = "",
          )
        )
      }
  }

  private fun selectStream(data: PlayUrlData, qualityId: Int) =
    data.streams.firstOrNull { it.id == qualityId }
      ?: data.streams.filter { it.id <= qualityId }.maxByOrNull { it.id }
      ?: data.streams.minByOrNull { it.id }
      ?: error("没有可缓存的清晰度")

  private fun queueTrack(id: String, uri: String, mimeType: String, cacheKey: String) {
    val request =
      DownloadRequest.Builder(id, Uri.parse(uri))
        .setMimeType(mimeType)
        .setCustomCacheKey(cacheKey)
        .build()
    DownloadService.sendAddDownload(
      appContext,
      OfflineDownloadService::class.java,
      request,
      true,
    )
  }

  private fun startPreparation(entry: OfflineMediaEntry) {
    val next = scope.launch(start = CoroutineStart.LAZY) { prepareAndQueue(entry) }
    preparationJobs.put(entry.id, next)?.cancel()
    next.invokeOnCompletion { preparationJobs.remove(entry.id, next) }
    next.start()
  }

  private fun recoverPersistedWork() {
    store.entries().forEach { entry ->
      if (
        entry.entitlementState == OfflineEntitlementState.REVOKED ||
          entry.entitlementState == OfflineEntitlementState.LOCKED ||
          entry.preparationPaused
      ) {
        return@forEach
      }
      if (entry.videoUrl.isBlank()) {
        if (entry.preparationError.isBlank()) startPreparation(entry)
        return@forEach
      }
      if (download(videoTrackId(entry.id)) == null) {
        queueTrack(videoTrackId(entry.id), entry.videoUrl, entry.videoMimeType, entry.videoCacheKey)
      }
      if (entry.audioUrl.isNotBlank() && download(audioTrackId(entry.id)) == null) {
        queueTrack(audioTrackId(entry.id), entry.audioUrl, entry.audioMimeType, entry.audioCacheKey)
      }
    }
  }

  private fun restartLockedWork(entry: OfflineMediaEntry) {
    if (entry.preparationPaused) return
    if (entry.videoUrl.isBlank()) {
      if (entry.preparationError.isBlank()) startPreparation(entry)
      return
    }
    val downloads = trackIds(entry.id).mapNotNull(::download)
    if (downloads.size < requiredTrackCount(entry)) {
      val preparing = entry.copy(videoUrl = "", audioUrl = "", preparationError = "")
      store.upsert(preparing)
      startPreparation(preparing)
    }
  }

  private fun removeTrackDownloads(id: String) {
    trackIds(id).forEach { trackId ->
      DownloadService.sendRemoveDownload(
        appContext,
        OfflineDownloadService::class.java,
        trackId,
        false,
      )
    }
  }

  private fun snapshotForDownloads(entry: OfflineMediaEntry): OfflineMediaSnapshot {
    val downloads = trackIds(entry.id).mapNotNull(::download)
    if (downloads.isEmpty()) {
      return OfflineMediaSnapshot(entry, OfflineTransferState.QUEUED, 0f, 0L, 0L)
    }
    val bytes = downloads.sumOf { it.bytesDownloaded.coerceAtLeast(0L) }
    val total = downloads.sumOf { it.contentLength.coerceAtLeast(0L) }
    val progress =
      if (total > 0L) (bytes.toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)
      else
        downloads
          .map { it.percentDownloaded }
          .filter { it >= 0f }
          .average()
          .toFloat()
          .coerceAtLeast(0f)
    val state =
      when {
        downloads.any { it.state == Download.STATE_FAILED } -> OfflineTransferState.FAILED
        downloads.all { it.state == Download.STATE_COMPLETED } &&
          downloads.size == requiredTrackCount(entry) -> OfflineTransferState.COMPLETED
        downloads.any { it.state == Download.STATE_DOWNLOADING } -> OfflineTransferState.DOWNLOADING
        downloads.any {
          it.state == Download.STATE_STOPPED && it.stopReason == USER_PAUSE_STOP_REASON
        } -> OfflineTransferState.PAUSED
        else -> OfflineTransferState.QUEUED
      }
    return OfflineMediaSnapshot(
      entry = entry,
      state = state,
      progressPercent = progress,
      bytesDownloaded = bytes,
      totalBytes = total,
      failureReason = if (state == OfflineTransferState.FAILED) "下载失败，可点击继续重试" else "",
    )
  }

  private fun download(id: String): Download? =
    runCatching { downloadManager.downloadIndex.getDownload(id) }.getOrNull()

  private fun currentRequirements(): Requirements =
    Requirements(if (store.wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

  private fun requirementsMet(): Boolean =
    currentRequirements().getNotMetRequirements(appContext) == 0

  private fun requiredTrackCount(entry: OfflineMediaEntry): Int =
    if (entry.audioUrl.isBlank()) 1 else 2

  private fun revokePremiumMedia(entry: OfflineMediaEntry) {
    preparationJobs.remove(entry.id)?.cancel()
    removeTrackDownloads(entry.id)
    store.upsert(
      entry.copy(
        videoUrl = "",
        audioUrl = "",
        videoCacheKey = "",
        audioCacheKey = "",
        danmakuRelativePath = "",
        subtitles = emptyList(),
        entitlementState = OfflineEntitlementState.REVOKED,
        entitlementValidUntilMs = 0L,
        preparationPaused = false,
        preparationError = "会员状态已失效，缓存内容已移除",
      )
    )
    scope.launch {
      val directory = File(rootDirectory, "metadata/${entry.id}")
      directory.listFiles()?.filterNot { it.name == "cover.jpg" }?.forEach(File::deleteRecursively)
    }
  }

  private fun downloadCover(url: String, directory: File): String {
    if (url.isBlank()) return ""
    val normalized =
      when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("https://") -> url
        url.startsWith("http://") -> url.replaceFirst("http://", "https://")
        else -> return ""
      }
    val response = BiliHttpClient.getPublic(normalized)
    val length = response.body?.contentLength() ?: -1L
    if (response.code !in 200..299 || length > MAX_COVER_BYTES) {
      response.close()
      return ""
    }
    val bytes = response.body?.bytes() ?: ByteArray(0)
    response.close()
    if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) return ""
    val file = File(directory, "cover.jpg")
    file.writeBytes(bytes)
    return file.relativeTo(rootDirectory).path
  }

  private fun saveDanmaku(entry: OfflineMediaEntry, directory: File): String {
    val items = BiliDanmakuApi.getDanmaku(entry.cid, entry.durationMs / 1_000L)
    val array = JSONArray()
    items.forEach { item -> array.put(encodeDanmaku(item)) }
    val file = File(directory, "danmaku.json")
    file.writeText(array.toString(), Charsets.UTF_8)
    return file.relativeTo(rootDirectory).path
  }

  private fun saveSubtitles(entry: OfflineMediaEntry, directory: File): List<OfflineSubtitle> =
    runCatching { BiliSubtitleApi.getCatalog(entry.aid, entry.cid, entry.bvid) }
      .getOrNull()
      ?.tracks
      .orEmpty()
      .mapNotNull { track ->
        runCatching {
            val cues = BiliSubtitleApi.getDocument(track, entry.bvid, entry.aid, entry.cid)
            val file = File(directory, "subtitle_${safeFileName(track.id)}.vtt")
            file.writeText(BiliSubtitleApi.toWebVtt(cues), Charsets.UTF_8)
            OfflineSubtitle(
              id = track.id,
              label = track.languageLabel,
              language = track.language,
              relativePath = file.relativeTo(rootDirectory).path,
            )
          }
          .getOrNull()
      }

  private fun encodeDanmaku(item: DanmakuItem): JSONObject =
    JSONObject()
      .put("timeMs", item.timeMs)
      .put("type", item.type)
      .put("fontSize", item.fontSize)
      .put("color", item.color)
      .put("content", item.content)
      .put("sourceId", item.sourceId)
      .put("colorful", item.colorful)

  private fun safeFileName(value: String): String =
    value.filter(Char::isLetterOrDigit).take(48).ifBlank { "track" }

  private fun initialRootDirectory(): File {
    if (store.storageLocationId == OfflineMediaStore.INTERNAL_STORAGE_ID) {
      return File(appContext.filesDir, OFFLINE_DIRECTORY_NAME)
    }
    return store.storageRootPath.takeIf(String::isNotBlank)?.let(::File)
      ?: File(appContext.filesDir, OFFLINE_DIRECTORY_NAME)
  }

  private fun usableBackendRoot(requestedRoot: File): File {
    val usable =
      store.storageLocationId == OfflineMediaStore.INTERNAL_STORAGE_ID ||
        requestedRoot.parentFile?.let { it.exists() && it.canWrite() } == true
    return if (usable) requestedRoot else File(appContext.cacheDir, "offline_media_unavailable")
  }

  private fun ensureStorageBackend() {
    if (
      migrationInProgress.get() ||
        !storageAvailable() ||
        cacheBackendRoot.absolutePath == activeRootDirectory.absolutePath
    ) {
      return
    }
    synchronized(componentLock) {
      if (
        migrationInProgress.get() ||
          !storageAvailable() ||
          cacheBackendRoot.absolutePath == activeRootDirectory.absolutePath
      ) {
        return
      }
      appContext.stopService(Intent(appContext, OfflineDownloadService::class.java))
      activeDownloadManager.release()
      mediaCache.release()
      cacheBackendRoot = activeRootDirectory
      mediaCache = createMediaCache(activeRootDirectory)
      activeDownloadManager = createDownloadManager(mediaCache)
      recoverPersistedWork()
    }
  }

  private fun availableBytes(path: File): Long {
    var candidate: File? = path
    while (candidate != null && !candidate.exists()) candidate = candidate.parentFile
    return runCatching { candidate?.let { StatFs(it.absolutePath).availableBytes } ?: 0L }
      .getOrDefault(0L)
  }

  private fun directoryBytes(directory: File): Long =
    if (!directory.exists()) 0L
    else
      directory.walkTopDown().filter(File::isFile).sumOf { file ->
        runCatching { file.length() }.getOrDefault(0L)
      }

  private fun copyDirectoryWithProgress(
    sourceRoot: File,
    targetRoot: File,
    totalBytes: Long,
    onProgress: (OfflineStorageMigrationProgress) -> Unit,
  ) {
    var copiedBytes = 0L
    var lastReportedBytes = 0L
    onProgress(OfflineStorageMigrationProgress(0L, totalBytes))
    if (!sourceRoot.exists()) return
    sourceRoot.walkTopDown().forEach { source ->
      val relative = source.relativeTo(sourceRoot).path
      val target = if (relative.isBlank()) targetRoot else File(targetRoot, relative)
      if (source.isDirectory) {
        check(target.exists() || target.mkdirs()) { "无法创建迁移目录" }
      } else if (source.isFile) {
        target.parentFile?.let { parent ->
          check(parent.exists() || parent.mkdirs()) { "无法创建迁移目录" }
        }
        source.inputStream().use { input ->
          target.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
              val count = input.read(buffer)
              if (count < 0) break
              output.write(buffer, 0, count)
              copiedBytes += count
              if (
                copiedBytes == totalBytes ||
                  copiedBytes - lastReportedBytes >= MIGRATION_PROGRESS_STEP_BYTES
              ) {
                lastReportedBytes = copiedBytes
                onProgress(OfflineStorageMigrationProgress(copiedBytes, totalBytes))
              }
            }
          }
        }
        target.setLastModified(source.lastModified())
      }
    }
  }

  private fun trackIds(id: String): List<String> = listOf(videoTrackId(id), audioTrackId(id))

  private fun videoTrackId(id: String): String = "$id:video"

  private fun audioTrackId(id: String): String = "$id:audio"

  companion object {
    private const val OFFLINE_DIRECTORY_NAME = "offline_media"
    private const val USER_PAUSE_STOP_REASON = 1
    private const val MAX_COVER_BYTES = 8 * 1024 * 1024
    private const val REQUIREMENTS_RECHECK_MS = 1_000L
    private const val MIGRATION_QUIET_PERIOD_MS = 350L
    private const val MIGRATION_FREE_SPACE_RESERVE_BYTES = 32L * 1024L * 1024L
    private const val MIGRATION_PROGRESS_STEP_BYTES = 512L * 1024L
    private const val VIP_AUTHORIZATION_LEASE_MS = 72L * 60L * 60L * 1_000L

    @Volatile private var instance: OfflineMediaManager? = null

    fun get(context: Context): OfflineMediaManager =
      instance
        ?: synchronized(this) {
          instance ?: OfflineMediaManager(context).also { instance = it }
        }

    fun isOfflineUri(uri: String): Boolean = uri.startsWith("bilione-offline://media/")

    fun resolveLoadedEntry(uri: String): OfflineMediaEntry? = instance?.entryFromPlaybackUri(uri)

    fun loadLoadedDanmaku(entry: OfflineMediaEntry): List<DanmakuItem> =
      instance?.loadDanmaku(entry).orEmpty()
  }
}
