package com.triparchive.presentation.home

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.triparchive.R
import com.triparchive.data.navigation.DestinationState
import com.triparchive.data.navigation.PleosDestinationSource
import com.triparchive.data.profile.Profile
import com.triparchive.presentation.home.components.ArrivalDialog
import com.triparchive.presentation.home.components.HomeDestinationSheet
import com.triparchive.presentation.management.ActionButton
import com.triparchive.presentation.management.Gray
import com.triparchive.presentation.management.Ink
import com.triparchive.presentation.management.LightGray
import com.triparchive.presentation.management.Pink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@Composable
fun DestinationHome(
    profile: Profile,
    onProfileClick: () -> Unit,
    onFinish: () -> Unit,
    source: Flow<DestinationState>? = null,
    modifier: Modifier = Modifier,
    renderCharacter: Boolean = true,
    drawBackground: Boolean = true,
    onMapClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var destination by remember(profile.id) { mutableStateOf(DestinationState()) }
    var acknowledged by remember(profile.id) { mutableStateOf<String?>(null) }
    var confirm by remember(profile.id) { mutableStateOf(false) }
    var finishing by remember(profile.id) { mutableStateOf(false) }
    LaunchedEffect(profile.id, finishing) {
        if (finishing) onFinish()
    }
    LaunchedEffect(profile.id, lifecycle, source, finishing) {
        if (finishing) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            (source ?: PleosDestinationSource(context.applicationContext).observe()).collect {
                if (it.routing && it.name != null) acknowledged = null
                destination = it
            }
        }
    }
    var demoArrival by remember(profile.id) { mutableStateOf<String?>(null) }
    val arrival = if (finishing) null else demoArrival ?: destination.arrived?.takeUnless { it == acknowledged }
    val acknowledgeArrival = {
        acknowledged = arrival
        demoArrival = null
    }
    var secondsRemaining by remember(profile.id, arrival) { mutableIntStateOf(5) }
    LaunchedEffect(arrival, confirm, lifecycle) {
        if (arrival != null && !confirm) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (secondsRemaining > 0) {
                    delay(1000)
                    secondsRemaining--
                }
                acknowledgeArrival()
            }
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .testTag("home-surface")
            .background(if (drawBackground) Color.White else Color.Transparent)
            .drawWithContent {
                drawContent()
                if (arrival != null || confirm) drawRect(Color.Black.copy(alpha = 0.2f))
            }.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        val s = minOf(maxWidth.value / 840f, maxHeight.value / 1184f)
        HomeScreen(
            profile = profile,
            onProfileClick = onProfileClick,
            onMapClick = onMapClick,
            renderCharacter = renderCharacter,
            background = if (drawBackground) painterResource(R.drawable.home_street_background) else null,
        )
        HomeDestinationSheet(
            nickname = profile.nickname,
            destination = arrival ?: destination.name?.takeIf { destination.routing && it.isNotBlank() },
            scale = s,
            onFinish = { if (!finishing) confirm = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (arrival != null && !confirm) {
            ArrivalDialog(
                destination = arrival,
                secondsRemaining = secondsRemaining,
                scale = s,
                isDemo = demoArrival != null,
                onContinue = acknowledgeArrival,
                onFinish = { if (!finishing) confirm = true },
            )
        }
        ArrivalDemoControls(
            destination = destination,
            isSimulating = demoArrival != null,
            canSimulate = !finishing && arrival == null && !confirm,
            onArrive = { demoArrival = it },
            onReset = { demoArrival = null },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        if (confirm) {
            Dialog(
                onDismissRequest = { confirm = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                SideEffect {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window?.setDimAmount(0f)
                }
                Column(
                    Modifier
                        .width((758 * s).dp)
                        .background(Color.White, RoundedCornerShape((20 * s).dp))
                        .padding(horizontal = (44 * s).dp, vertical = (80 * s).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy((32 * s).dp),
                ) {
                    Text("정말 여행을 마칠까요?", color = Ink, fontSize = (42 * s).sp, fontWeight = FontWeight.Bold)
                    Text("여행을 마치면 프로필 선택 화면으로 돌아가요", color = Gray, fontSize = (28 * s).sp, textAlign = TextAlign.Center)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((30 * s).dp)) {
                        ActionButton("마치기", Pink, s, true, {
                            if (!finishing) {
                                finishing = true
                                confirm = false
                                demoArrival = null
                                destination = DestinationState()
                            }
                        }, Modifier.weight(1f))
                        ActionButton("취소", LightGray, s, true, { confirm = false }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
