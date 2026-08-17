package com.proxyapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.proxyapp.ui.theme.ProxyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 开屏页：展示开屏底图，首次使用需勾选同意才能进入。
 * 已同意过则短暂展示后自动进入主界面。
 */
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("agreement", MODE_PRIVATE)
        val agreed = prefs.getBoolean("agreed", false)

        setContent {
            ProxyTheme {
                SplashScreen(
                    needAgree = !agreed,
                    onAgree = {
                        prefs.edit().putBoolean("agreed", true).apply()
                        goToMain()
                    },
                    onDisagree = {
                        finishAffinity()
                    }
                )
            }
        }

        if (agreed) {
            lifecycleScope.launch {
                delay(1200)
                goToMain()
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
private fun SplashScreen(
    needAgree: Boolean,
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.splash_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // 底部压暗，保证文字可读
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
        )

        if (needAgree) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White)
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "欢迎使用代理助手",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF232B33)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "请在阅读并同意《用户协议》和《隐私政策》后进入使用。\n" +
                        "App 支持添加自己的订阅链接，连接后流量将经过你选择的节点转发。",
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = Color(0xFF6B7F93)
                )
                Spacer(Modifier.height(14.dp))

                var agreed by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { agreed = !agreed }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = agreed,
                        onCheckedChange = { agreed = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "我已阅读并同意《用户协议》和《隐私政策》",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2B3A4A)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onAgree,
                    enabled = agreed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90D9))
                ) {
                    Text(
                        text = "同意并进入",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                TextButton(
                    onClick = onDisagree,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "不同意，退出",
                        fontSize = 13.sp,
                        color = Color(0xFF9AA7B4),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
