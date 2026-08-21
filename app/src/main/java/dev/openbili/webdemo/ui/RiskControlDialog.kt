package dev.openbili.webdemo.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.WebViewConfigurator
import dev.openbili.webdemo.api.GeetestChallenge
import dev.openbili.webdemo.api.RiskChallenge
import dev.openbili.webdemo.api.RiskControlManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun RiskControlDialog(
  challenge: RiskChallenge,
  onVerified: () -> Unit,
) {
  var config by remember(challenge.voucher) { mutableStateOf<GeetestChallenge?>(null) }
  var error by remember(challenge.voucher) { mutableStateOf<String?>(null) }
  var attempt by remember(challenge.voucher) { mutableIntStateOf(0) }
  var validating by remember(challenge.voucher) { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val controlMode = LocalControlMode.current
  val dismissFocusRequester = remember { FocusRequester() }

  LaunchedEffect(controlMode, challenge.voucher) {
    if (controlMode) {
      androidx.compose.runtime.withFrameNanos {}
      runCatching { dismissFocusRequester.requestFocus() }
    }
  }

  LaunchedEffect(challenge.voucher, attempt) {
    config = null
    error = null
    runCatching { withContext(Dispatchers.IO) { RiskControlManager.register(challenge) } }
      .onSuccess { config = it }
      .onFailure { error = it.message ?: "验证码准备失败，请稍后再试" }
  }

  Dialog(
    onDismissRequest = { RiskControlManager.dismiss(challenge) },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(.62f).heightIn(min = 360.dp, max = 560.dp),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      shadowElevation = 0.dp,
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("需要确认一下不是机器人 ( •̀ ω •́ )✧", style = MaterialTheme.typography.titleLarge)
        Text(
          "完成下面的哔哩哔哩验证后，刚才没有加载出的内容会自动重试。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (controlMode) {
          Text(
            "请在手机或电脑上完成验证码；若在当前设备操作，请使用触控或鼠标。此情况极少出现。",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
        ) {
          when {
            config != null -> {
              key(config!!.token) {
                CaptchaWebView(
                  config = config!!,
                  enabled = !validating,
                  allowFocus = !controlMode,
                  onSolved = { validate, seccode ->
                    if (validating) return@CaptchaWebView
                    validating = true
                    error = null
                    scope.launch {
                      runCatching {
                          withContext(Dispatchers.IO) {
                            RiskControlManager.validate(config!!, validate, seccode)
                          }
                        }
                        .onSuccess {
                          validating = false
                          onVerified()
                        }
                        .onFailure {
                          validating = false
                          error = it.message ?: "验证没有通过，请再试一次"
                        }
                    }
                  },
                  onError = { error = it },
                )
              }
              if (validating) {
                Box(
                  Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = .28f)),
                  contentAlignment = Alignment.Center,
                ) {
                  CircularProgressIndicator()
                }
              }
            }
            error != null -> {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(
                  onClick = { attempt++ },
                  modifier =
                    Modifier.controlFocusOutline(
                      shape = RoundedCornerShape(20.dp),
                      color = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                  Text("重新加载")
                }
              }
            }
            else -> CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
          }
        }
        error
          ?.takeIf { config != null }
          ?.let {
            Text(
              it,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          OutlinedButton(
            onClick = { RiskControlManager.dismiss(challenge) },
            modifier =
              Modifier.focusRequester(dismissFocusRequester)
                .controlFocusOutline(
                  shape = RoundedCornerShape(20.dp),
                  color = MaterialTheme.colorScheme.primary,
                ),
          ) {
            Text("稍后再说")
          }
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun CaptchaWebView(
  config: GeetestChallenge,
  enabled: Boolean,
  allowFocus: Boolean,
  onSolved: (validate: String, seccode: String) -> Unit,
  onError: (String) -> Unit,
) {
  val mainHandler = remember { Handler(Looper.getMainLooper()) }
  val bridge =
    remember(config.token) {
      CaptchaBridge(
        onSolved = { validate, seccode -> mainHandler.post { onSolved(validate, seccode) } },
        onError = { message -> mainHandler.post { onError(message) } },
      )
    }
  AndroidView(
    modifier = Modifier.fillMaxSize().focusProperties { canFocus = allowFocus },
    factory = { context ->
      WebView(context).apply {
        WebViewConfigurator.configure(this, BuildConfig.DEBUG)
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webChromeClient = WebChromeClient()
        addJavascriptInterface(bridge, "BiliCaptchaBridge")
        loadDataWithBaseURL(
          "https://www.bilibili.com/",
          captchaHtml(config),
          "text/html",
          "UTF-8",
          null,
        )
      }
    },
    update = { it.isEnabled = enabled },
    onRelease = { webView ->
      webView.removeJavascriptInterface("BiliCaptchaBridge")
      webView.stopLoading()
      webView.destroy()
    },
  )
}

private class CaptchaBridge(
  private val onSolved: (String, String) -> Unit,
  private val onError: (String) -> Unit,
) {
  @JavascriptInterface
  fun solved(validate: String, seccode: String) {
    onSolved(validate, seccode)
  }

  @JavascriptInterface
  fun failed(message: String) {
    onError(message)
  }
}

private fun captchaHtml(config: GeetestChallenge): String {
  val gt = JSONObject.quote(config.gt)
  val challenge = JSONObject.quote(config.challenge)
  return """
    <!doctype html>
    <html lang="zh-CN">
      <head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
          html,body{height:100%;margin:0;background:transparent}
          body{display:flex;align-items:center;justify-content:center;padding:24px;box-sizing:border-box}
          #captcha{width:min(420px,100%)}
        </style>
        <script src="https://static.geetest.com/static/tools/gt.js"></script>
      </head>
      <body>
        <div id="captcha"></div>
        <script>
          var captchaReady = false;
          setTimeout(function() {
            if (!captchaReady) BiliCaptchaBridge.failed('验证码加载超时，请重新加载');
          }, 12000);
          try {
            initGeetest({
              gt: $gt,
              challenge: $challenge,
              new_captcha: true,
              offline: false,
              product: 'bind',
              width: '100%',
              https: true
            }, function(captcha) {
              captchaReady = true;
              captcha.appendTo('#captcha');
              captcha.onSuccess(function() {
                var result = captcha.getValidate();
                if (!result) {
                  BiliCaptchaBridge.failed('没有取得验证结果，请再试一次');
                  return;
                }
                BiliCaptchaBridge.solved(result.geetest_validate, result.geetest_seccode);
              });
              captcha.onError(function() { BiliCaptchaBridge.failed('验证码加载失败，请重新加载'); });
            });
          } catch (e) {
            BiliCaptchaBridge.failed('验证码初始化失败：' + e.message);
          }
        </script>
      </body>
    </html>
  """
    .trimIndent()
}
