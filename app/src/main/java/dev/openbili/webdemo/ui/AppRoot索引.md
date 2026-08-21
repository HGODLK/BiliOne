# AppRoot.kt 导航索引

> 本文件是 `AppRoot.kt`（约 8100 行）的**地图**，不是行为文档。页面行为见 `软件逻辑.md`，模型与状态机见 `AppRootModels.kt`。
> 行号以 2026-08-13 的代码为准，会随改动漂移；每次改完 AppRoot.kt 请顺手更新本索引。

## 1. 整体结构

AppRoot 是单 Activity 应用的全部页面编排器：一个根 Box 里按 zIndex 分层挂载所有页面与覆盖层，不用 Navigation-Compose，靠自定义状态机 + 四类关系栈（视频/个人空间/专栏/直播房间，各上限 8）工作。

| 层 | 内容 | 位置 |
| --- | --- | --- |
| Layer 0 | 根 Pager（HOME/BANGUMI/MY）+ 底部胶囊 | 约 6300-6790 |
| Layer 1 | VideoScreen / LiveRoomScreen / ArticleScreen（同级互斥） | 7017 / 7523 / 7610 |
| 覆盖层 | 个人空间层（zIndex ±1）、封面转场（2/3/4/5）、番剧主题遮罩（1.5）、番剧换季黑屏（240）、全局输入锁（320） | 7689-8110 |
| 输入锁 | 全屏 consume Box，zIndex 320，由 `interactionTransitionActive` 驱动 | 8102 |

内部 8 个分段注释：447 视频页局部状态、1234 登录态同步、2295 进入视频时加载、2550 登录面板、2576 进入转场、4316 退出转场、6177 屏幕共存。

## 2. 关键状态清单（定义于约 300-460 行）

| 组 | 状态 | 行 |
| --- | --- | --- |
| 转场会话 | `transitionSession` / `transitionToken` / `videoExitPrelude` | 380 / 381 / 409 |
| 隐藏封面 | `hidden*CoverItemId` 系列（feed/热门/动态/我的/搜索/番剧/文章/推荐/空间…） | 382-397 |
| 关系栈 | `videoStack` / `articleStack` / `liveRoomParentStack` / `profileState` 栈 | 410 / 411 / 337 / 460 |
| 直播 | `activeLiveRoom` / `liveTransitionSession` / `liveExitPrelude` / `liveFullscreenTransitionActive` 等 | 334-356 |
| 搜索转场 | `searchTransitionDirection` / `searchTransitionProgress` / `searchTransitionJob` | 364-370 |
| 番剧索引转场 | `bangumiIndexTransitionProgress` / `...Job` / `defer*PageComposition` | 359-363, 406-408 |
| 文章 | `articleTransitionSession` / `articleSuspendedVideo` / `articleEntryToken` | 412-423 |
| 派生门控 | `navigationLocked` / `interactionTransitionActive` / `preparingRootEnter` | 6171 / 6178 / 6195 |
| 其它 | `musicEntryInputLocked`（音乐页进入触摸锁）/ `videoFullscreenTransitionActive` | 353 / 352 |

状态机与守卫的纯函数在 `AppRootModels.kt`：`SessionPhase`（311-320）、`shouldDisplayCardTransitionOverlay`（322-332）、`shouldHideVideoPageBehindExitCover`（347-352）、`profileTransitionInputLocked`（219-228）。转场屏障在 `TransitionPreparation.kt`，播放会话状态在 `AppRootPlayer.kt`，覆盖层绘制在 `AppTransitions.kt`。

## 3. 函数索引（100 个成员函数）

### 3.1 播放器与转场基础设施
| 函数 | 行 | 职责 |
| --- | --- | --- |
| `prepareCardTransition` / `prepareExitTransition` | 804 / 883 | 转场准备：位图就绪 + 目标边界连续两帧稳定 + 450ms 屏障 |
| `revealTransitionSession` | 1178 | ENTER 落位揭示（coverAlpha 170/90ms 交叉淡化，EXIT 落位的范式来源） |
| `cachedCardTransitionBitmap` | 788 | 转场位图缓存 |
| `awaitStablePlayerBounds` | 2559 | 等播放器边界稳定 |
| `previewSeek` / `setTemporarySpeedBoost` / `setPlaybackSpeed` / `cancelSeekPreview` / `commitSeek` | 1166-1175 | 进度条拖动节流与倍速代理 |
| `launchTransition` | 523 | 统一转场协程入口（中断/替换守卫） |
| `animateToRootTab` | 530 | 根标签切换 |
| `commitPlaybackProgress` | 646 | 进度落盘/上报 |
| `obtainPlayerView` / `unbindPlayerView` / `prewarmPlayerInfrastructure` | 745-783 | 根级 PlayerView 宿主管理 |

### 3.2 视频进入/退出（2576-5000 区）
| 函数 | 行 | 职责 |
| --- | --- | --- |
| `startEnterVideo` | 2577 | 根入口进视频（六步转场 + 延迟挂载） |
| `startRecommendedVideo` | 4842 | 推荐卡进子视频（栈内） |
| `startProfileVideo` | 2825 | 空间投稿进视频 |
| `startExitVideo` | 4317 | 返回首页（EXIT_ROOT，落位含 coverAlpha 交叉淡化） |
| `startBackToPreviousVideo` | 4461 | 返回上一视频（EXIT_RECOMMENDATION） |
| `startExitVideoToProfile` | 4135 | 返回个人空间（EXIT_PROFILE） |
| `cancelPreparingProfileVideo` | 4286 | 取消空间视频准备 |
| `reverseActiveEnter` | 4601 | 进入转场中断反向 |
| `cancelPreparingRootEnter` | 4807 | 取消根进入准备 |
| `returnDirectlyHome` | 4970 | 房子直达首页（清上下文） |
| 视频页数据 | `snapshotEntry`/`cacheEntry`/`restoreEntry`/`ensureVideoPageData`/`selectVideoPage`/`selectCollectionEpisode`/`clearVisibleVideoData` | 2160-2251 | 8 页缓存与切分 P/合集 |

### 3.3 个人空间（1400-2100 区）
`loadSpacePage`/`loadSpaceDynamics`/`newProfileEntry`/`prepareProfile`/`loadProfile`/`restoreProfile`/`activeProfileEntry`（1406-1455）、`prepareProfileTransition`（1459）、`prepareBoundsTransition`（1497）、`openProfile`（1519）、`openAvatarProfileFrom`/`openAvatarProfile`（1528/1684）、`openCommentProfileFrom`/`openCommentProfile`/`openArticleCommentProfile`（1691/1861/1867）、`resumeVideoUnderProfile`（1882）、`completeProfileReturnToArticle`（1899）、`closeProfile`（1907，返回转场）。8 层栈与 returnsToVideo 临时段见 `AppRootProfileState.kt`。

### 3.4 番剧（2954-3951 区）
`selectBangumiEpisode`/`selectBangumiSeason`（2954/2996）、`toggleBangumiFollow`（3044）、`postBangumiShortReview`（3091）、`loadActiveBangumiMetadata`（3119）、`startRootBangumi`/`startHistoryBangumi`/`startSearchBangumi`/`startProfileBangumi`（3250/3322/3326/3490）、`startExitRootBangumi`（3736）、`startExitBangumi`（3951）、`fadeBangumiPageDirectly`（3715）、`restoredBangumiCard`（633）。

### 3.5 退出前奏（3635-3736）
`beginVideoExitPrelude`（3635，静止封面+页面淡出的前奏数据结构）、`animateVideoExitPrelude`（3662：封面 140ms 淡入 → 提交帧 → 页面 200ms 淡出）、`fadeOutVideoExitPrelude`（3707）。三段式返回纪律：封面先接管、再飞行、最后真实封面交叉淡入——见 `软件逻辑.md`。

### 3.6 专栏（5037-5459 区）
`loadArticleDetail`（5037）、`awaitStableArticleHeroBounds`（5061）、`articleSourceBounds`/`hideArticleSource`（5078/5088）、`suspendVideoForArticle`/`restoreVideoSuspendedByArticle`（5102/5130）、`startEnterArticle`（5160）、`startExitArticle`（5356）、`openInteractionTarget`（5244，评论目标导航）。

### 3.7 搜索与番剧索引（5459-5756）
`openSearchResultsAnimated`/`closeSearchResultsAnimated`（5459/5515，主题遮罩 + 空 Surface 缩放）、`openBangumiIndexAnimated`/`closeBangumiIndexAnimated`（5611/5655）。

### 3.8 直播（5756-6118）
`liveTransitionItem`/`currentLiveTransitionBounds`/`setLiveSourceCoverHidden`/`liveSourceBounds`（5756-5794）、`startEnterLive`（5801）、`startEnterRecommendedLive`（5898）、`startBackToPreviousLive`（5989）、`startExitLive`（6066）。独立 `LiveRoomParentFrame` 栈，上限 8。

### 3.9 其它
`currentPreferredResolutionMode`（217）、`showVideoPreview`（2091，长按预览）、关注分组 `loadFollowingGroups`/`selectFollowingGroup`/`unfollow`（2103-2121）、`loadMentionSuggestions`（2156）、`retainedPlaybackPage`（1873，分 P 原位切集恢复）、`selectCommentSort`（2173）。

## 4. 覆盖层 zIndex 契约

| zIndex | 内容 | 行 |
| --- | --- | --- |
| 320 | 全局输入锁（`interactionTransitionActive`，含音乐页进入锁 `musicEntryInputLocked`） | 8102 |
| 240 | 番剧换季退出黑屏淡出 | 7986 |
| 5 | 某 prelude 覆盖层 | 7934 |
| 4 | 转场覆盖层（CardTransitionOverlay 组） | 7918 |
| 3 | 退出前奏静止封面 | 7979 |
| 2 | 飞行封面覆盖层 | 7957 |
| 1.5 | 番剧主题遮罩 | 7900 |
| 1 / -1 | 个人空间层（抑制时 -1 退出命中） | 7689 |
| 0/1 | 搜索番剧来源页（需要时抬到视频之上） | 6320 / 6861 |
| 80/90/100 | 全屏三件套（黑底/播放器/控制层，契约在 `VideoScreen.kt` 937/987/1813） | — |

## 5. 返回分发（BackHandler 优先级：后注册先响应）

| 位置 | 条件 | 动作 |
| --- | --- | --- |
| 5707-5735 | 视频页 4 个 BackHandler（转场中反向/取消准备、退出视频栈等） | 最高优先级 |
| 587 | 非 HOME 根标签 | 回 HOME |
| 6679 | 番剧索引保留覆盖层 | 关索引 |
| `MainActivity.kt:108` | 兜底 | 双击退出应用 |

## 6. 修改注意事项（血泪教训）

1. **落位交接必须交叉淡化**：覆盖层（672×378 新解码位图）与真实卡片（448×252 位图）不同源，同帧硬切换必闪一帧。EXIT_ROOT/PROFILE/RECOMMENDATION 已按 `revealTransitionSession` 范式加 coverAlpha 淡出（2026-08-13），新增退出路径时照抄。
2. **先提交一帧再读边界**：返回落点用 `StableBoundsTracker` 连续两帧稳定（1px 容差）才放行；恢复真实卡片后必须 `withFrameNanos` 再淡出覆盖层。
3. **token/代次三重守卫**：每个转场协程都校验 `transitionToken`/`dataCommitAllowedId`/页面身份，被替换立即 `return@launch`，清锁责任交给新 owner。
4. **输入锁的 OR 链**：新增任何转场都要考虑是否进 `interactionTransitionActive`（6178）；纯判定逻辑下沉到 `AppRootModels.kt` 并配 `ProfileTransitionInputLockTest` 式单测。
5. **`interactionTransitionActive` 只在 8102 消费**，别的地方（根 Pager 拖拽等）另有自己的门控，改锁前先 grep 确认消费点。
6. **同帧同步块是原子收尾**：`transitionSession=null` + `transitionPhase` 切换 + 隐藏封面恢复必须同一 snapshot 写入，中间差一帧就会闪。
7. `AppRootPlayer.kt`、`AppRootVideoState.kt`、`AppRootProfileState.kt` 是 2026-07 从本文件拆出去的部分，改播放/视频数据/空间逻辑先看那三个文件，别把 AppRoot 再养肥。
