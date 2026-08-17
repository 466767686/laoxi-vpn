package com.proxyapp

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private val SkyTop = Color(0xFFD5EAFF)
private val SkyBottom = Color(0xFFFDFEFF)
private val PrimaryBlue = Color(0xFF4A90D9)
private val Silhouette = Color(0xFF607F9E)
private val GreenOk = Color(0xFF2E7D32)
private val Amber = Color(0xFFB26A00)

/** 按压缩放回弹动效：按下缩小，松开弹回。 */
private fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.93f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun ProxyScreen(
    currentPage: String,
    onNavigate: (String) -> Unit,
    isRunning: Boolean,
    connecting: Boolean,
    globalMode: Boolean,
    nodeCount: Int,
    groupName: String,
    currentNode: String,
    nodeFetchFailed: Boolean,
    txRate: Long,
    rxRate: Long,
    txBytes: Long,
    rxBytes: Long,
    error: String?,
    versionName: String,
    diagnostics: String?,
    subscriptions: List<String>,
    subscriptionStatus: String?,
    groups: List<ProxyGroup>,
    currentGroup: String,
    nodes: List<String>,
    delays: Map<String, Int>,
    testing: Set<String>,
    panelMsg: String?,
    onToggle: () -> Unit,
    onToggleGlobalMode: (Boolean) -> Unit,
    onRetryNode: () -> Unit,
    onExportLog: () -> Unit,
    onShareLog: () -> Unit,
    onAddSubscription: (String) -> Unit,
    onUpdateSubscription: (String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onRestoreSubscription: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onSelectNode: (String) -> Unit,
    onRetest: () -> Unit
) {
    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.app_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.30f))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .systemBarsPadding()
            ) {
                Row(Modifier.fillMaxSize()) {
                    LeftNavRail(currentPage = currentPage, onNavigate = onNavigate)
                    AnimatedContent(
                        targetState = currentPage,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(260)) +
                                slideInHorizontally(animationSpec = tween(260)) { it / 10 })
                                .togetherWith(fadeOut(animationSpec = tween(120)))
                        },
                        label = "pageContent"
                    ) { page ->
                        when (page) {
                    "proxy" -> ProxyPage(
                        modifier = Modifier.fillMaxSize(),
                        isRunning = isRunning,
                        globalMode = globalMode,
                        groups = groups,
                        currentGroup = currentGroup,
                        nodes = nodes,
                        delays = delays,
                        testing = testing,
                        message = panelMsg,
                        onSelectGroup = onSelectGroup,
                        onSelectNode = onSelectNode,
                        onRetest = onRetest,
                        onToggleGlobalMode = onToggleGlobalMode
                    )
                            "subscription" -> SubscriptionPage(
                                modifier = Modifier.fillMaxSize(),
                                nodeCount = nodeCount,
                                groupName = groupName,
                                currentNode = currentNode,
                                txBytes = txBytes,
                                rxBytes = rxBytes,
                                subscriptions = subscriptions,
                                subscriptionStatus = subscriptionStatus,
                                onAddSubscription = onAddSubscription,
                                onUpdateSubscription = onUpdateSubscription,
                                onDeleteSubscription = onDeleteSubscription,
                                onRestoreSubscription = onRestoreSubscription
                            )
                            "settings" -> SettingsPage(
                                modifier = Modifier.fillMaxSize(),
                                versionName = versionName,
                                diagnostics = diagnostics,
                                onExportLog = onExportLog,
                                onShareLog = onShareLog
                            )
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 2.dp, end = 20.dp, top = 14.dp, bottom = 26.dp)
                    ) {
                        IllustrationCard(Modifier.weight(1.35f).fillMaxWidth())
                        Spacer(Modifier.height(18.dp))
                                    ControlCard(
                                        modifier = Modifier.weight(0.85f).fillMaxWidth(),
                                        isRunning = isRunning,
                                        connecting = connecting,
                                        currentNode = currentNode,
                                    nodeFetchFailed = nodeFetchFailed,
                                    txRate = txRate,
                                    rxRate = rxRate,
                                        error = error,
                                        onToggle = onToggle,
                                        onRetryNode = onRetryNode
                                )
                    }
                }
            }
            }
        }
        }
    }
}

/* ============================ 左侧导航栏 ============================ */

@Composable
private fun LeftNavRail(currentPage: String, onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(70.dp)
            .fillMaxHeight()
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NavItem(
            label = "首页",
            active = currentPage == "home",
            icon = { NavHomeIcon(Modifier.size(22.dp), it) }
        ) { onNavigate("home") }
        Spacer(Modifier.height(18.dp))
        NavItem(
            label = "代理",
            active = currentPage == "proxy",
            icon = { NavRouteIcon(Modifier.size(22.dp), it) }
        ) { onNavigate("proxy") }
        Spacer(Modifier.height(18.dp))
        NavItem(
            label = "订阅",
            active = currentPage == "subscription",
            icon = { NavBoxIcon(Modifier.size(22.dp), it) }
        ) { onNavigate("subscription") }
        Spacer(Modifier.height(18.dp))
        NavItem(
            label = "设置",
            active = currentPage == "settings",
            icon = { NavGearIcon(Modifier.size(22.dp), it) }
        ) { onNavigate("settings") }
    }
}

@Composable
private fun NavItem(
    label: String,
    active: Boolean,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (active) PrimaryBlue else Color.White.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick
                )
                .pressScale(interactionSource),
            contentAlignment = Alignment.Center
        ) {
            icon(if (active) Color.White else PrimaryBlue)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (active) PrimaryBlue else Color(0xFF6B7F93),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NavHomeIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val roof = Path().apply {
            moveTo(w * 0.08f, h * 0.48f)
            lineTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.92f, h * 0.48f)
            close()
        }
        drawPath(roof, tint)
        val body = Path().apply {
            moveTo(w * 0.24f, h * 0.44f)
            lineTo(w * 0.76f, h * 0.44f)
            lineTo(w * 0.76f, h * 0.9f)
            lineTo(w * 0.24f, h * 0.9f)
            close()
        }
        drawPath(body, tint)
        drawRect(
            color = Color.White,
            topLeft = Offset(w * 0.42f, h * 0.58f),
            size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.2f)
        )
    }
}

@Composable
private fun NavRouteIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path()
        p.moveTo(w * 0.18f, h * 0.44f)
        p.lineTo(w * 0.5f, h * 0.18f)
        p.lineTo(w * 0.82f, h * 0.44f)
        p.close()
        p.moveTo(w * 0.18f, h * 0.56f)
        p.lineTo(w * 0.5f, h * 0.82f)
        p.lineTo(w * 0.82f, h * 0.56f)
        p.close()
        drawPath(p, tint)
    }
}

@Composable
private fun NavBoxIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val top = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.9f, h * 0.3f)
            lineTo(w * 0.5f, h * 0.48f)
            lineTo(w * 0.1f, h * 0.3f)
            close()
        }
        val left = Path().apply {
            moveTo(w * 0.1f, h * 0.3f)
            lineTo(w * 0.1f, h * 0.72f)
            lineTo(w * 0.5f, h * 0.9f)
            lineTo(w * 0.5f, h * 0.48f)
            close()
        }
        val right = Path().apply {
            moveTo(w * 0.9f, h * 0.3f)
            lineTo(w * 0.9f, h * 0.72f)
            lineTo(w * 0.5f, h * 0.9f)
            lineTo(w * 0.5f, h * 0.48f)
            close()
        }
        drawPath(left, tint)
        drawPath(right, tint)
        drawPath(top, tint)
    }
}

@Composable
private fun NavGearIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        listOf(0.3f, 0.55f, 0.8f).forEach { y ->
            drawLine(
                color = tint,
                start = Offset(w * 0.12f, h * y),
                end = Offset(w * 0.88f, h * y),
                strokeWidth = h * 0.05f,
                cap = StrokeCap.Round
            )
        }
        listOf(
            Offset(w * 0.3f, h * 0.3f),
            Offset(w * 0.7f, h * 0.55f),
            Offset(w * 0.38f, h * 0.8f)
        ).forEach { c ->
            drawCircle(tint, radius = h * 0.12f, center = c)
            drawCircle(Color.White, radius = h * 0.05f, center = c)
        }
    }
}

/* ============================ 插画卡片 ============================ */

@Composable
private fun IllustrationCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.card_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        TimeBatteryOverlay(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
    }
}

@Composable
private fun TimeBatteryOverlay(modifier: Modifier = Modifier) {
    val (hour, minute) = rememberClockText()
    val battery = rememberBatteryLevel()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.82f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = hour,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp,
                color = Color(0xFF2B3A4A)
            )
            Text(
                text = minute,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
                color = Color(0xFF6B7F93)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BatteryIcon(level = battery, modifier = Modifier.size(26.dp, 13.dp))
            Spacer(Modifier.height(3.dp))
            Text(
                text = "$battery%",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6B7F93)
            )
        }
    }
}

@Composable
private fun rememberClockText(): Pair<String, String> {
    var hour by remember { mutableStateOf(LocalTime.now().hour.toString().padStart(2, '0')) }
    var minute by remember { mutableStateOf(LocalTime.now().minute.toString().padStart(2, '0')) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            hour = now.hour.toString().padStart(2, '0')
            minute = now.minute.toString().padStart(2, '0')
            delay(20_000)
        }
    }
    return hour to minute
}

@Composable
private fun rememberBatteryLevel(): Int {
    val context = LocalContext.current
    var level by remember { mutableStateOf(readBattery(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            level = readBattery(context)
            delay(20_000)
        }
    }
    return level
}

private fun readBattery(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level > 0 && scale > 0) level * 100 / scale else 0
}

@Composable
private fun BatteryIcon(level: Int, modifier: Modifier = Modifier) {
    val fillColor = when {
        level > 50 -> GreenOk
        level > 20 -> Amber
        else -> Color(0xFFC62828)
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = h * 0.12f
        val body = Rect(stroke / 2, stroke / 2, w - stroke / 2 - h * 0.22f, h - stroke / 2)
        drawRoundRect(
            color = Color(0xFFB9C6D2),
            topLeft = body.topLeft,
            size = body.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.2f),
            style = Stroke(width = stroke)
        )
        val capLeft = w - h * 0.22f + h * 0.08f
        drawRoundRect(
            color = Color(0xFFB9C6D2),
            topLeft = Offset(capLeft, h * 0.28f),
            size = androidx.compose.ui.geometry.Size(h * 0.18f, h * 0.44f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.06f)
        )
        val innerInset = stroke * 1.6f
        val fillWidth = (body.width - innerInset * 2) * (level.coerceIn(0, 100) / 100f)
        if (fillWidth > 0) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(body.left + innerInset, body.top + innerInset),
                size = androidx.compose.ui.geometry.Size(fillWidth, body.height - innerInset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.12f)
            )
        }
    }
}

/* ============================ 插画占位（可替换为 AI 插画） ============================ */

@Composable
private fun SceneIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        drawRect(Brush.verticalGradient(listOf(Color(0xFFBEDEFF), Color(0xFFEAF6FF))))

        drawCircle(Color(0xFFFFF1C4), radius = w * 0.17f, center = Offset(w * 0.82f, h * 0.22f))
        drawCircle(Color(0xFFFFF9E0), radius = w * 0.12f, center = Offset(w * 0.82f, h * 0.22f))

        drawCloud(center = Offset(w * 0.24f, h * 0.15f), r = w * 0.10f)
        drawCloud(center = Offset(w * 0.70f, h * 0.42f), r = w * 0.075f)
        drawCloud(center = Offset(w * 0.40f, h * 0.52f), r = w * 0.06f)

        val grass = Color(0xFFA9D6A5)
        val grassDark = Color(0xFF8CC98A)
        drawHill(w * 0.0f, h * 0.72f, w * 0.55f, h * 0.36f, grass)
        drawHill(w * 0.45f, h * 0.74f, w * 0.6f, h * 0.32f, grassDark)

        flower(Offset(w * 0.16f, h * 0.80f), w * 0.035f, Color(0xFFFFB3C1))
        flower(Offset(w * 0.30f, h * 0.88f), w * 0.03f, Color(0xFFFFD9A0))
        flower(Offset(w * 0.86f, h * 0.82f), w * 0.04f, Color(0xFFFFB3C1))
        flower(Offset(w * 0.74f, h * 0.92f), w * 0.03f, Color(0xFFC9B6FF))

        drawGirlSilhouette(w, h)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(center: Offset, r: Float) {
    val c = Color.White.copy(alpha = 0.85f)
    drawCircle(c, radius = r, center = center)
    drawCircle(c, radius = r * 0.72f, center = Offset(center.x - r * 0.75f, center.y + r * 0.18f))
    drawCircle(c, radius = r * 0.78f, center = Offset(center.x + r * 0.78f, center.y + r * 0.16f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHill(x: Float, y: Float, w: Float, h: Float, color: Color) {
    val p = Path().apply {
        moveTo(x, y)
        quadraticTo(x + w / 2, y - h * 1.8f, x + w, y)
        close()
    }
    drawPath(p, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.flower(center: Offset, r: Float, color: Color) {
    val petalR = r * 0.62f
    listOf(0f, 72f, 144f, 216f, 288f).forEach { deg ->
        val rad = Math.toRadians(deg.toDouble())
        val c = Offset(
            center.x + r * cos(rad).toFloat(),
            center.y + r * sin(rad).toFloat()
        )
        drawCircle(color, radius = petalR, center = c)
    }
    drawCircle(Color(0xFFFFD76A), radius = r * 0.5f, center = center)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGirlSilhouette(w: Float, h: Float) {
    val bodyColor = Silhouette
    val dressColor = Color.White.copy(alpha = 0.92f)

    val umbrellaCx = w * 0.52f
    val umbrellaTop = h * 0.30f
    val umbrellaR = w * 0.17f
    drawArc(
        color = bodyColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(umbrellaCx - umbrellaR, umbrellaTop - umbrellaR),
        size = androidx.compose.ui.geometry.Size(umbrellaR * 2, umbrellaR * 2),
        style = Stroke(width = w * 0.018f)
    )
    drawLine(
        color = bodyColor,
        start = Offset(umbrellaCx, umbrellaTop - umbrellaR),
        end = Offset(umbrellaCx, umbrellaTop - umbrellaR - w * 0.025f),
        strokeWidth = w * 0.014f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = bodyColor.copy(alpha = 0.7f),
        start = Offset(umbrellaCx, umbrellaTop),
        end = Offset(umbrellaCx, h * 0.63f),
        strokeWidth = w * 0.014f,
        cap = StrokeCap.Round
    )

    val headC = Offset(w * 0.43f, h * 0.58f)
    val headR = w * 0.062f
    drawCircle(bodyColor, radius = headR, center = headC)
    val ear = Path().apply {
        moveTo(headC.x - headR * 0.75f, headC.y - headR * 0.55f)
        lineTo(headC.x - headR * 0.95f, headC.y - headR * 1.35f)
        lineTo(headC.x - headR * 0.1f, headC.y - headR * 0.9f)
        close()
    }
    drawPath(ear, bodyColor)
    val ear2 = Path().apply {
        moveTo(headC.x + headR * 0.15f, headC.y - headR * 0.92f)
        lineTo(headC.x + headR * 0.95f, headC.y - headR * 1.35f)
        lineTo(headC.x + headR * 0.75f, headC.y - headR * 0.55f)
        close()
    }
    drawPath(ear2, bodyColor)
    drawArc(
        color = bodyColor,
        startAngle = 190f,
        sweepAngle = 160f,
        useCenter = true,
        topLeft = Offset(headC.x - headR, headC.y - headR * 1.1f),
        size = androidx.compose.ui.geometry.Size(headR * 2, headR * 2)
    )

    val dress = Path().apply {
        moveTo(w * 0.36f, h * 0.70f)
        lineTo(w * 0.50f, h * 0.70f)
        lineTo(w * 0.56f, h * 0.93f)
        quadraticTo(w * 0.46f, h * 0.96f, w * 0.30f, h * 0.93f)
        close()
    }
    drawPath(dress, dressColor)

    drawLine(
        color = bodyColor,
        start = Offset(w * 0.46f, h * 0.70f),
        end = Offset(w * 0.52f, h * 0.64f),
        strokeWidth = w * 0.02f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = bodyColor.copy(alpha = 0.55f),
        start = Offset(w * 0.38f, h * 0.76f),
        end = Offset(w * 0.50f, h * 0.76f),
        strokeWidth = w * 0.01f,
        cap = StrokeCap.Round
    )
}

/* ============================ 控制卡片 ============================ */

@Composable
private fun ControlCard(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    connecting: Boolean,
    currentNode: String,
    nodeFetchFailed: Boolean,
    txRate: Long,
    rxRate: Long,
    error: String?,
    onToggle: () -> Unit,
    onRetryNode: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = rememberGreeting(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF9AA7B4)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "中南海专线为您护航",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp,
                        color = Color(0xFF232B33)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) GreenOk else Color(0xFFC0C9D2))
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = when {
                            connecting -> "连接中…"
                            isRunning -> "已连接"
                            else -> "未连接"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            connecting -> Amber
                            isRunning -> GreenOk
                            else -> Color(0xFF7A8794)
                        }
                    )
                }
            }

            Spacer(Modifier.weight(0.7f))

            Text(
                text = "当前节点",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF9AA7B4)
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        isRunning && currentNode.isNotEmpty() -> currentNode
                        isRunning && nodeFetchFailed -> "获取失败"
                        isRunning -> "获取中…"
                        else -> "未连接"
                    },
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isRunning && nodeFetchFailed) Color(0xFFC62828) else Color(0xFF2B3A4A),
                    modifier = Modifier.weight(1f)
                )
                if (isRunning && nodeFetchFailed) {
                    TextButton(onClick = onRetryNode) {
                        Text(
                            text = "重试",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "↑ ${fmtBytes(txRate)}/s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "↓ ${fmtBytes(rxRate)}/s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
            }

            Spacer(Modifier.weight(1f))

            val interactionSource = remember { MutableInteractionSource() }
            val buttonColor by animateColorAsState(
                targetValue = if (isRunning) Color(0xFFD9534F) else PrimaryBlue,
                animationSpec = tween(250),
                label = "buttonColor"
            )
            Button(
                onClick = onToggle,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScale(interactionSource),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                StartStopIcon(isRunning = isRunning, modifier = Modifier.size(24.dp))
            }

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = error,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StartStopIcon(isRunning: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (isRunning) {
            // 停止：圆角方块
            val s = w * 0.78f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset((w - s) / 2f, (h - s) / 2f),
                size = androidx.compose.ui.geometry.Size(s, s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f)
            )
        } else {
            // 启动：播放三角
            val p = Path().apply {
                moveTo(w * 0.30f, h * 0.22f)
                lineTo(w * 0.80f, h * 0.50f)
                lineTo(w * 0.30f, h * 0.78f)
                close()
            }
            drawPath(p, Color.White)
        }
    }
}

@Composable
private fun rememberGreeting(): String {
    var greeting by remember { mutableStateOf(greetingFor(LocalTime.now().hour)) }
    LaunchedEffect(Unit) {
        while (true) {
            greeting = greetingFor(LocalTime.now().hour)
            delay(20_000)
        }
    }
    return greeting
}

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..21 -> "Good evening"
    else -> "Good night"
}

/* ============================ 代理页面 ============================ */

@Composable
private fun ProxyPage(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    globalMode: Boolean,
    groups: List<ProxyGroup>,
    currentGroup: String,
    nodes: List<String>,
    delays: Map<String, Int>,
    testing: Set<String>,
    message: String?,
    onSelectGroup: (String) -> Unit,
    onSelectNode: (String) -> Unit,
    onRetest: () -> Unit,
    onToggleGlobalMode: (Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .padding(start = 2.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)
            .navigationBarsPadding()
    ) {
        // 顶部渐变信息卡
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF7FB2E5), Color(0xFF3E6FA8))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "代理节点",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "选择策略组与节点，点击即可切换",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) Color(0xFF7BE495) else Color(0xFFD9E2EC))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isRunning) "已连接" else "未连接",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "当前节点",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = groups.firstOrNull { it.name == currentGroup }?.now
                            ?.ifEmpty { "未选择" } ?: "未选择",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "全局模式",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "所有流量都走代理节点",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    Switch(
                        checked = globalMode,
                        onCheckedChange = onToggleGlobalMode,
                        enabled = isRunning
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 策略组标签
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            groups.forEach { g ->
                val chipSource = remember { MutableInteractionSource() }
                val selected = g.name == currentGroup
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) PrimaryBlue else Color.White)
                        .clickable(
                            interactionSource = chipSource,
                            indication = ripple(),
                            onClick = { onSelectGroup(g.name) }
                        )
                        .pressScale(chipSource, pressedScale = 0.95f)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = g.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else Color(0xFF4A5560)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${g.all.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White.copy(alpha = 0.85f) else Color(0xFF9AA7B4)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // 节点列表卡片
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "节点列表（${nodes.size}）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2B3A4A),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetest) {
                        Text(
                            text = "重新测速",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }
                if (nodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEAF3FC)),
                                contentAlignment = Alignment.Center
                            ) {
                                NavBoxIcon(Modifier.size(26.dp), PrimaryBlue)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = message ?: "加载中…",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (message != null) MaterialTheme.colorScheme.error else Color(0xFF9AA7B4)
                            )
                        }
                    }
                } else {
                    val sortedNodes = remember(nodes, delays, testing) {
                        nodes.sortedWith { a, b ->
                            val da = delays[a]
                            val db = delays[b]
                            when {
                                da != null && db != null -> da.compareTo(db)
                                da != null -> -1
                                db != null -> 1
                                else -> 0
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        items(sortedNodes) { node ->
                            val selected = node == groups.firstOrNull { it.name == currentGroup }?.now
                            NodeRow(
                                name = node,
                                selected = selected,
                                delay = delays[node],
                                testing = node in testing,
                                onClick = { onSelectNode(node) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(
    name: String,
    selected: Boolean,
    delay: Int?,
    testing: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFFE7F1FB) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .pressScale(interactionSource, pressedScale = 0.97f)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) PrimaryBlue else Color(0xFFEFF3F7)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFC9D4DE))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Color(0xFF2B5B8F) else Color(0xFF2B3A4A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        if (testing) {
            DelayPill(text = "测试中…", bg = Color(0xFFF1F3F5), fg = Color(0xFF7A8794))
        } else if (delay != null) {
            when {
                delay < 300 -> DelayPill(text = "${delay}ms", bg = Color(0xFFE4F4E6), fg = GreenOk)
                delay < 800 -> DelayPill(text = "${delay}ms", bg = Color(0xFFFFF3E0), fg = Amber)
                else -> DelayPill(text = "${delay}ms", bg = Color(0xFFFDEBEA), fg = Color(0xFFC62828))
            }
        } else {
            DelayPill(text = "超时", bg = Color(0xFFF1F3F5), fg = Color(0xFF7A8794))
        }
    }
}

@Composable
private fun DelayPill(text: String, bg: Color, fg: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = fg
    )
}

/* ============================ 订阅 / 设置页面 ============================ */

@Composable
private fun SubscriptionPage(
    modifier: Modifier = Modifier,
    nodeCount: Int,
    groupName: String,
    currentNode: String,
    txBytes: Long,
    rxBytes: Long,
    subscriptions: List<String>,
    subscriptionStatus: String?,
    onAddSubscription: (String) -> Unit,
    onUpdateSubscription: (String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onRestoreSubscription: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(start = 2.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "订阅管理",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        var urlInput by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("粘贴订阅链接（http/https）", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onAddSubscription(urlInput)
                    urlInput = ""
                },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(
                    text = "添加",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        subscriptionStatus?.let { status ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = status,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (status.contains("失败")) Color(0xFFC62828) else GreenOk
            )
        }

        Spacer(Modifier.height(14.dp))
        if (subscriptions.isEmpty()) {
            Text(
                text = "还没有添加订阅链接，请先添加订阅后才能连接。",
                fontSize = 12.sp,
                color = Color(0xFF9AA7B4)
            )
        } else {
            Text(
                text = "我的订阅",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2B3A4A)
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(subscriptions) { url ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = url,
                            fontSize = 12.sp,
                            color = Color(0xFF2B3A4A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onUpdateSubscription(url) }) {
                            Text(
                                text = "更新",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        }
                        TextButton(onClick = { onDeleteSubscription(url) }) {
                            Text(
                                text = "删除",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onRestoreSubscription) {
                Text(
                    text = "清空订阅",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "当前配置信息",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2B3A4A)
        )
        Spacer(Modifier.height(10.dp))
        InfoRow("节点数量", "$nodeCount 个")
        Spacer(Modifier.height(14.dp))
        InfoRow("策略组", groupName)
        Spacer(Modifier.height(14.dp))
        InfoRow("当前节点", currentNode.ifEmpty { "-" })
        Spacer(Modifier.height(14.dp))
        InfoRow("累计上传 / 下载", "${fmtBytes(txBytes)} / ${fmtBytes(rxBytes)}")
    }
}

@Composable
private fun SettingsPage(
    modifier: Modifier = Modifier,
    versionName: String,
    diagnostics: String?,
    onExportLog: () -> Unit,
    onShareLog: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(start = 2.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))
        InfoRow("App 版本", "v$versionName")
        Spacer(Modifier.height(14.dp))
        InfoRow("代理内核", "mihomo v1.19.29")
        Spacer(Modifier.height(14.dp))
        InfoRow("订阅", "自定义添加")
        Spacer(Modifier.height(20.dp))
        Text(
            text = "说明：订阅与节点信息已内置在 App 中，连接后自动加载。",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9AA7B4)
        )
        if (!diagnostics.isNullOrBlank()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "诊断信息",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2B3A4A)
            )
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    text = diagnostics,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    color = Color(0xFF5A6B7A),
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "日志",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2B3A4A)
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                onClick = onExportLog,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(
                    text = "导出到下载",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onShareLog,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B8CBE))
            ) {
                Text(
                    text = "分享日志",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "导出后可在手机文件管理里找到：下载 → 老习VPN → 老习VPN日志.txt，" +
                "也可直接分享给开发者排查问题。",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9AA7B4)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9AA7B4)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun fmtBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    return String.format(java.util.Locale.US, "%.2f GB", mb / 1024)
}
