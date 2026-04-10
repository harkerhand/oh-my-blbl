package cn.harkerhand.ohmyblbl

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import cn.harkerhand.ohmyblbl.ui.theme.BlblBorder
import cn.harkerhand.ohmyblbl.ui.theme.BlblPink
import cn.harkerhand.ohmyblbl.ui.theme.BlblPinkLine
import cn.harkerhand.ohmyblbl.ui.theme.BlblPinkSoft
import cn.harkerhand.ohmyblbl.ui.theme.BlblSurface
import cn.harkerhand.ohmyblbl.ui.theme.BlblText
import cn.harkerhand.ohmyblbl.ui.theme.BlblTextSoft
import org.json.JSONObject

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun LoginScreen(
    state: UiState,
    onSwitchLoginMode: (LoginMode) -> Unit,
    onSmsTelChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onPrepareSmsSend: () -> Unit,
    onSmsLogin: () -> Unit,
    onStartQrLogin: () -> Unit,
    onCancelQrLogin: () -> Unit,
    onSaveQrAndOpenBiliScanner: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val configuration = LocalConfiguration.current
    val topOffset = (configuration.screenHeightDp * 0.11f).dp

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(topOffset))
            Text(
                text = "Oh My BiliBili",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                ),
                color = BlblPink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            ModeSwitcher(
                activeMode = state.loginMode,
                onSwitchLoginMode = onSwitchLoginMode,
            )
            Spacer(Modifier.height(22.dp))
            AnimatedContent(
                targetState = state.loginMode,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally { fullWidth -> fullWidth / 2 } + fadeIn()).togetherWith(
                            slideOutHorizontally { fullWidth -> -fullWidth / 2 } + fadeOut()
                        ).using(SizeTransform(clip = false))
                    } else {
                        (slideInHorizontally { fullWidth -> -fullWidth / 2 } + fadeIn()).togetherWith(
                            slideOutHorizontally { fullWidth -> fullWidth / 2 } + fadeOut()
                        ).using(SizeTransform(clip = false))
                    }
                },
                label = "login-mode-switch",
            ) { mode ->
                when (mode) {
                    LoginMode.Sms -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PinkInput(
                                value = state.smsTel,
                                onValueChange = onSmsTelChange,
                                placeholder = "手机号",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PinkInput(
                                    value = state.smsCode,
                                    onValueChange = onSmsCodeChange,
                                    placeholder = "短信验证码",
                                    modifier = Modifier.weight(1f),
                                )
                                PillButton(
                                    text = if (state.smsCooldown > 0) "${state.smsCooldown}s" else "发送",
                                    onClick = onPrepareSmsSend,
                                    enabled = !state.loading && state.smsCooldown <= 0,
                                    modifier = Modifier
                                        .height(54.dp)
                                        .width(108.dp),
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            PillButton(
                                text = if (state.loading) "登录中..." else "登录",
                                onClick = onSmsLogin,
                                enabled = !state.loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                            )
                        }
                    }

                    LoginMode.Qr -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PillButton(
                                text = if (state.qrUrl.isBlank()) "打开扫码登录" else "刷新二维码",
                                onClick = onStartQrLogin,
                                enabled = !state.loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                leading = {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCode2,
                                        contentDescription = null,
                                        tint = BlblSurface,
                                    )
                                },
                            )
                            if (state.qrUrl.isNotBlank()) {
                                Spacer(Modifier.height(18.dp))
                                Surface(
                                    color = BlblSurface,
                                    shape = RoundedCornerShape(28.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BlblBorder),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        AsyncImage(
                                            model = "https://quickchart.io/qr?size=320&text=${Uri.encode(state.qrUrl)}",
                                            contentDescription = "二维码",
                                            modifier = Modifier
                                                .size(220.dp)
                                                .clip(RoundedCornerShape(20.dp)),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text("请使用 B 站 App 扫码确认", color = BlblText)
                                        Text(
                                            "二维码剩余 ${state.qrRemainSeconds}s",
                                            color = BlblTextSoft,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = onCancelQrLogin) { Text("取消") }
                                            TextButton(onClick = onSaveQrAndOpenBiliScanner) { Text("B站扫码") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSwitcher(
    activeMode: LoginMode,
    onSwitchLoginMode: (LoginMode) -> Unit,
) {
    Surface(
        color = BlblSurface.copy(alpha = 0.84f),
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BlblPinkLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .animateContentSize(),
        ) {
            val segmentWidth = maxWidth / 2
            val capsuleOffset by animateDpAsState(
                targetValue = when (activeMode) {
                    LoginMode.Sms -> 0.dp
                    LoginMode.Qr -> segmentWidth
                },
                animationSpec = tween(durationMillis = 260),
                label = "mode-switch-offset",
            )

            Box(
                modifier = Modifier
                    .offset(x = capsuleOffset)
                    .width(segmentWidth)
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(BlblPinkSoft),
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                ModeButton(
                    text = "短信",
                    active = activeMode == LoginMode.Sms,
                    modifier = Modifier.weight(1f),
                    onClick = { onSwitchLoginMode(LoginMode.Sms) },
                )
                ModeButton(
                    text = "扫码",
                    active = activeMode == LoginMode.Qr,
                    modifier = Modifier.weight(1f),
                    onClick = { onSwitchLoginMode(LoginMode.Qr) },
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.noRippleClickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(999.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (active) BlblText else BlblTextSoft,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun PinkInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 14,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSizeSp.sp),
        placeholder = {
            Text(
                text = placeholder,
                color = BlblTextSoft,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSizeSp.sp),
            )
        },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = BlblSurface.copy(alpha = 0.92f),
            focusedContainerColor = BlblSurface,
            unfocusedBorderColor = BlblPinkLine,
            focusedBorderColor = BlblPink,
            unfocusedTextColor = BlblText,
            focusedTextColor = BlblText,
            cursorColor = BlblPink,
        ),
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BlblPink,
            contentColor = BlblSurface,
            disabledContainerColor = BlblPink.copy(alpha = 0.55f),
        ),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SmsCaptchaDialog(
    gt: String,
    challenge: String,
    onDismiss: () -> Unit,
    onSolved: (challenge: String, validate: String, seccode: String) -> Unit,
) {
    val html = remember(gt, challenge) { geetestHtml(gt, challenge) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            color = BlblSurface,
            shape = RoundedCornerShape(30.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BlblBorder),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "验证码校验",
                        color = BlblText,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("关闭", color = BlblPink) }
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, BlblBorder, RoundedCornerShape(22.dp)),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onSolved(ch: String, validate: String, seccode: String) {
                                        post { onSolved(ch, validate, seccode) }
                                    }
                                },
                                "AndroidBridge"
                            )
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            loadDataWithBaseURL(
                                "https://static.geetest.com",
                                html,
                                "text/html",
                                "utf-8",
                                null,
                            )
                        }
                    },
                )
                Text(
                    text = "完成验证后将自动发送短信验证码",
                    color = BlblTextSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun geetestHtml(gt: String, challenge: String): String {
    val gtValue = JSONObject.quote(gt)
    val chValue = JSONObject.quote(challenge)
    return """
<!doctype html>
<html>
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <style>
    body { font-family: sans-serif; margin: 0; padding: 16px; background: #fff7fa; }
    #captcha { width: 100%; min-height: 180px; }
    .tip { color: #8c7a86; font-size: 13px; margin-top: 10px; }
  </style>
  <script src="https://static.geetest.com/static/tools/gt.js"></script>
  <script>
    function start() {
      if (!window.initGeetest) return;
      initGeetest({
        gt: $gtValue,
        challenge: $chValue,
        protocol: "https://",
        offline: false,
        new_captcha: true,
        product: "embed",
        width: "100%"
      }, function(captchaObj) {
        captchaObj.appendTo("#captcha");
        captchaObj.onSuccess(function() {
          var res = captchaObj.getValidate() || {};
          if (window.AndroidBridge && res.geetest_challenge && res.geetest_validate && res.geetest_seccode) {
            AndroidBridge.onSolved(
              String(res.geetest_challenge),
              String(res.geetest_validate),
              String(res.geetest_seccode)
            );
          }
        });
      });
    }
    window.onload = start;
  </script>
</head>
<body>
  <div id="captcha"></div>
  <div class="tip">完成验证后将自动发送短信验证码</div>
</body>
</html>
""".trimIndent()
}

