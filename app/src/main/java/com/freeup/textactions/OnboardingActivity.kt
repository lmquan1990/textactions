package com.freeup.textactions

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeup.textactions.ui.theme.TextActionsTheme
import com.freeup.textactions.ui.theme.LocalExpressiveColors
import kotlinx.coroutines.launch

class OnboardingActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val settingsManager = SettingsManager(this)
        val dynamicColorEnabled = settingsManager.isDynamicColorEnabled()
        val themeMode = settingsManager.getThemeMode()

        setContent {
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            TextActionsTheme(darkTheme = darkTheme, dynamicColor = dynamicColorEnabled) {
                OnboardingScreen(
                    onFinished = {
                        settingsManager.setBoolean("onboarding_completed", true)
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    }
                )
            }
        }
    }
}

data class OnboardingPage(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val mainColor: Color,
    val illustration: @Composable () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val onboardingGreen = Color(0xFF00838F)
    val pages = listOf(
        OnboardingPage(
            R.string.onboarding_title_1,
            R.string.onboarding_subtitle_1,
            AppIcons.HandTap,
            onboardingGreen,
            { Slide1Illustration(onboardingGreen) }
        ),
        OnboardingPage(
            R.string.onboarding_title_2,
            R.string.onboarding_subtitle_2,
            AppIcons.Camera,
            onboardingGreen,
            { Slide2Illustration(onboardingGreen) }
        ),
        OnboardingPage(
            R.string.onboarding_title_3,
            R.string.onboarding_subtitle_3,
            AppIcons.Gear,
            onboardingGreen,
            { Slide3Illustration(onboardingGreen) }
        )
    )

    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isLastPage) {
                    TextButton(onClick = onFinished) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { index ->
                OnboardingPageUI(page = pages[index])
            }

            // Bottom Navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Indicator
                Row(
                    Modifier
                        .height(24.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { iteration ->
                        val color = if (pagerState.currentPage == iteration)
                            onboardingGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(if (pagerState.currentPage == iteration) 10.dp else 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Primary Button
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onFinished()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onboardingGreen,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage < pages.size - 1) 
                            stringResource(R.string.onboarding_next) 
                        else stringResource(R.string.onboarding_start),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingPageUI(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(300.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            page.illustration()
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = page.mainColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(page.subtitleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

// --- Illustrations ---

@Composable
fun Slide1Illustration(mainColor: Color = Color(0xFF00838F)) {
    val colors = LocalExpressiveColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Selection Mock with Highlight
        Box(contentAlignment = Alignment.BottomEnd) {
            Card(
                modifier = Modifier.width(240.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color.LightGray))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.height(6.dp).width(60.dp).background(Color.LightGray.copy(alpha = 0.5f)))
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.background(mainColor.copy(alpha = 0.15f))) {
                        Text(
                            text = "Select text anywhere",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = mainColor
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.height(6.dp).fillMaxWidth().background(Color.LightGray.copy(alpha = 0.3f)))
                }
            }
            
            // Bottom Sheet Hint
            Surface(
                modifier = Modifier
                    .offset(y = (10).dp, x = (10).dp)
                    .width(160.dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 0.dp),
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 4.dp
            ) {
                Column(Modifier.padding(12.dp)) {
                    repeat(2) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF00838F).copy(alpha = 0.1f)))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.height(4.dp).width(80.dp).background(Color.LightGray))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Icon(
            imageVector = AppIcons.HandTap,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = mainColor
        )
    }
}

private val SparkYellow = Color(0xFFFF8F00)  // Vàng cam sắc nét, rõ trên nền sáng
private val QrPurple = Color(0xFF9C27B0)    // Tím

@Composable
fun Slide2Illustration(mainColor: Color = Color(0xFF00838F)) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Row 1: Camera -> Spark
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(24.dp),
                color = mainColor.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(1.dp, mainColor.copy(alpha = 0.1f))
            ) {
                Icon(AppIcons.Camera, null, Modifier.padding(24.dp), tint = mainColor)
            }
            Icon(AppIcons.ArrowForward, null, Modifier.padding(horizontal = 16.dp), tint = Color.LightGray)
            Surface(
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                color = SparkYellow.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SparkYellow.copy(alpha = 0.6f))
            ) {
                Icon(AppIcons.Sparkle, null, Modifier.padding(14.dp).size(32.dp), tint = SparkYellow)
            }
        }
        // Row 2: QR Code -> Spark
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(24.dp),
                color = QrPurple.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, QrPurple.copy(alpha = 0.2f))
            ) {
                Icon(AppIcons.QrCode, null, Modifier.padding(24.dp), tint = QrPurple)
            }
            Icon(AppIcons.ArrowForward, null, Modifier.padding(horizontal = 16.dp), tint = Color.LightGray)
            Surface(
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                color = SparkYellow.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SparkYellow.copy(alpha = 0.6f))
            ) {
                Icon(AppIcons.Sparkle, null, Modifier.padding(14.dp).size(32.dp), tint = SparkYellow)
            }
        }
    }
}

@Composable
fun Slide3Illustration(mainColor: Color = Color(0xFF00838F)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.width(220.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.ShieldCheck, null, Modifier.size(20.dp), tint = mainColor)
                    Spacer(Modifier.width(12.dp))
                    Text("Privacy & Control", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = mainColor)
                }
                Spacer(Modifier.height(16.dp))
                repeat(3) { i ->
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(Modifier.height(6.dp).width(100.dp).background(Color.LightGray.copy(alpha = 0.4f)))
                        Switch(
                            checked = i != 1, 
                            onCheckedChange = {}, 
                            enabled = false,
                            colors = SwitchDefaults.colors(disabledCheckedTrackColor = mainColor.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }
    }
}
