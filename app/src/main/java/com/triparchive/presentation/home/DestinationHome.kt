package com.triparchive.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.triparchive.presentation.management.ActionButton
import com.triparchive.presentation.management.BackControl
import com.triparchive.presentation.management.Gray
import com.triparchive.presentation.management.Ink
import com.triparchive.presentation.management.LightGray
import com.triparchive.presentation.management.Pink
import kotlinx.coroutines.flow.Flow

@Composable
fun DestinationHome(
    profile: Profile,
    onProfileClick: () -> Unit,
    onFinish: () -> Unit,
    source: Flow<DestinationState>? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var destination by remember(profile.id) { mutableStateOf(DestinationState()) }
    var acknowledged by remember(profile.id) { mutableStateOf<String?>(null) }
    var confirm by remember(profile.id) { mutableStateOf(false) }
    LaunchedEffect(profile.id, lifecycle, source) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            (source ?: PleosDestinationSource(context.applicationContext).observe()).collect {
                if (it.routing && it.name != null) acknowledged = null
                destination = it
            }
        }
    }
    var demoArrival by remember(profile.id) { mutableStateOf<String?>(null) }
    val arrival = demoArrival ?: destination.arrived?.takeUnless { it == acknowledged }
    val acknowledgeArrival = {
        acknowledged = arrival
        demoArrival = null
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
        val s = minOf(maxWidth.value / 840f, maxHeight.value / 1184f)
        if (arrival == null) {
            HomeScreen(profile, onProfileClick)
            if (destination.routing && destination.name != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .width((840 * s).dp)
                        .background(Color.White, RoundedCornerShape(topStart = (40 * s).dp, topEnd = (40 * s).dp))
                        .padding((28 * s).dp),
                    verticalArrangement = Arrangement.spacedBy((24 * s).dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.destination_flag), null, Modifier.size((48 * s).dp))
                        Spacer(Modifier.width((10 * s).dp))
                        Text(
                            destination.name.orEmpty(),
                            color = Ink,
                            fontSize = (28 * s).sp,
                            lineHeight = (42 * s).sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ActionButton("여행 마치기", Pink, s, true, { confirm = true }, Modifier.fillMaxWidth())
                }
            }
        } else {
            BackHandler(enabled = !confirm) { acknowledgeArrival() }
            Column(
                Modifier
                    .align(Alignment.Center)
                    .size((840 * s).dp, (1184 * s).dp)
                    .padding(horizontal = (56 * s).dp)
                    .padding(top = (40 * s).dp, bottom = (48 * s).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((16 * s).dp),
            ) {
                Box(Modifier.fillMaxWidth()) { BackControl(s, true) { acknowledgeArrival() } }
                Image(painterResource(R.drawable.onboarding_character), null, Modifier.size((144 * s).dp, (164 * s).dp))
                Text(
                    "${arrival}에\n도착했어요!",
                    color = Ink,
                    fontSize = (48 * s).sp,
                    lineHeight = (64 * s).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                ActionButton("도착 확인하고 계속하기", Pink, s, true, { acknowledgeArrival() }, Modifier.fillMaxWidth())
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            (96 * s).dp,
                        ).background(Color(0xFFF2F4F6), RoundedCornerShape((24 * s).dp))
                        .clickable(interactionSource = null, indication = null, role = Role.Button) { confirm = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("여기서 여행 마치기", color = Ink, fontSize = (32 * s).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        ArrivalDemoControls(
            destination = destination,
            isSimulating = demoArrival != null,
            canSimulate = arrival == null && !confirm,
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
                SideEffect { window?.setDimAmount(0.2f) }
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
                            confirm = false
                            onFinish()
                        }, Modifier.weight(1f))
                        ActionButton("취소", LightGray, s, true, { confirm = false }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
