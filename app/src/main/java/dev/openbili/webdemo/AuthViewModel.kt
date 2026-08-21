package dev.openbili.webdemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.BiliAuthApi
import dev.openbili.webdemo.api.BiliHttpClient
import dev.openbili.webdemo.api.QrCodeInfo
import dev.openbili.webdemo.api.UserInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface LoginState {
  data object Idle : LoginState

  data class QrReady(val qrInfo: QrCodeInfo) : LoginState

  data class Waiting(val qrInfo: QrCodeInfo, val message: String) : LoginState

  data class AppQrReady(val qrInfo: QrCodeInfo) : LoginState

  data class AppWaiting(val qrInfo: QrCodeInfo, val message: String) : LoginState

  data class Success(val user: UserInfo) : LoginState

  data object AppAuthorized : LoginState

  data class Failed(val message: String) : LoginState

  data class AppFailed(val message: String) : LoginState
}

class AuthViewModel : ViewModel() {
  private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
  val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

  private val _userInfo = MutableStateFlow(UserInfo(mid = 0, name = "", face = "", isLogin = false))
  val userInfo: StateFlow<UserInfo> = _userInfo.asStateFlow()

  private val _appAccessAuthorized = MutableStateFlow(BiliHttpClient.hasValidAppAccessToken())
  val appAccessAuthorized: StateFlow<Boolean> = _appAccessAuthorized.asStateFlow()

  private var pollJob: Job? = null
  private var appAuthorizationFlow = false

  fun checkLoginStatus() {
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      try {
        val info = BiliAuthApi.getUserInfo()
        _userInfo.value = info
      } catch (_: Exception) {}
    }
  }

  fun startLogin() {
    appAuthorizationFlow = false
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      try {
        val qr = BiliAuthApi.generateQrCode()
        if (qr == null) {
          _loginState.value = LoginState.Failed("获取二维码失败，请重试")
          return@launch
        }
        _loginState.value = LoginState.QrReady(qr)
        startPolling(qr)
      } catch (e: Exception) {
        _loginState.value = LoginState.Failed(e.message ?: "获取二维码失败")
      }
    }
  }

  fun startAppAuthorization() {
    appAuthorizationFlow = true
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      try {
        val qr = BiliAuthApi.generateAppQrCode()
        if (qr == null) {
          _loginState.value = LoginState.AppFailed("获取移动端授权二维码失败，请重试")
          return@launch
        }
        _loginState.value = LoginState.AppQrReady(qr)
        startAppPolling(qr)
      } catch (e: Exception) {
        _loginState.value = LoginState.AppFailed(e.message ?: "获取移动端授权二维码失败")
      }
    }
  }

  fun retryLogin() {
    if (appAuthorizationFlow) startAppAuthorization() else startLogin()
  }

  fun cancelLogin() {
    pollJob?.cancel()
    pollJob = null
    _loginState.value = LoginState.Idle
  }

  fun logout() {
    pollJob?.cancel()
    pollJob = null
    BiliHttpClient.clearLoginSession()
    _appAccessAuthorized.value = false
    appAuthorizationFlow = false
    _userInfo.value = UserInfo(mid = 0, name = "", face = "", isLogin = false)
    _loginState.value = LoginState.Idle
  }

  private fun startPolling(qrInfo: QrCodeInfo) {
    pollJob?.cancel()
    android.util.Log.d("AuthVM", "startPolling")
    pollJob =
      viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        while (true) {
          delay(2000)
          try {
            val status = BiliAuthApi.pollQrCode(qrInfo.qrcodeKey)
            android.util.Log.d("AuthVM", "poll status: code=${status.code}")
            when (status.code) {
              86101 ->
                _loginState.value =
                  LoginState.Waiting(
                    qrInfo = qrInfo,
                    message = "请使用哔哩哔哩 App 扫码",
                  )
              86090 ->
                _loginState.value =
                  LoginState.Waiting(
                    qrInfo = qrInfo,
                    message = "请在手机上确认登录",
                  )
              0 -> {
                // 登录成功 —— 拉取用户信息
                val info = BiliAuthApi.getUserInfo()
                _userInfo.value = info
                _loginState.value = LoginState.Success(info)
                return@launch
              }
              86038 -> {
                _loginState.value = LoginState.Failed("二维码已过期，请重新扫码")
                return@launch
              }
              else -> {
                _loginState.value = LoginState.Failed(status.message)
                return@launch
              }
            }
          } catch (e: Exception) {
            _loginState.value = LoginState.Failed(e.message ?: "登录失败")
            return@launch
          }
        }
      }
  }

  private fun startAppPolling(qrInfo: QrCodeInfo) {
    pollJob?.cancel()
    pollJob =
      viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        while (true) {
          delay(2_000)
          try {
            val status = BiliAuthApi.pollAppQrCode(qrInfo.qrcodeKey)
            when (status.code) {
              86039 -> _loginState.value = LoginState.AppWaiting(qrInfo, "请使用哔哩哔哩 App 扫码并确认授权")
              0 -> {
                if (status.accessToken.isBlank()) {
                  _loginState.value = LoginState.AppFailed("授权成功，但没有获得访问凭证")
                  return@launch
                }
                val currentMid = _userInfo.value.mid
                if (currentMid > 0L && status.mid > 0L && status.mid != currentMid) {
                  _loginState.value = LoginState.AppFailed("授权账号与当前登录账号不一致，请换用同一账号扫码")
                  return@launch
                }
                BiliHttpClient.saveAppAccessToken(
                  accessToken = status.accessToken,
                  refreshToken = status.refreshToken,
                  expiresInSeconds = status.expiresInSeconds,
                )
                _appAccessAuthorized.value = true
                _loginState.value = LoginState.AppAuthorized
                return@launch
              }
              86038 -> {
                _loginState.value = LoginState.AppFailed("授权二维码已过期，请重新扫码")
                return@launch
              }
              else -> {
                _loginState.value = LoginState.AppFailed(status.message.ifBlank { "移动端授权失败" })
                return@launch
              }
            }
          } catch (e: Exception) {
            _loginState.value = LoginState.AppFailed(e.message ?: "移动端授权失败")
            return@launch
          }
        }
      }
  }

  override fun onCleared() {
    pollJob?.cancel()
  }
}
