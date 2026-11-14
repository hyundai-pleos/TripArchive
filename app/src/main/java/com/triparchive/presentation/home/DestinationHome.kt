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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import com.triparchive.R
import com.triparchive.data.navigation.DestinationCategory
import com.triparchive.data.navigation.DestinationState
import com.triparchive.data.navigation.PleosDestinationSource
import com.triparchive.data.profile.Profile
import com.triparchive.presentation.LocalDrivingRestricted
import com.triparchive.presentation.home.components.ArrivalDialog
import com.triparchive.presentation.home.components.CategorySelectionDialog
import com.triparchive.presentation.home.components.HomeDestinationSheet
import com.triparchive.presentation.management.ActionButton
import com.triparchive.presentation.management.Gray
import com.triparchive.presentation.management.Ink
import com.triparchive.presentation.management.LightGray
import com.triparchive.presentation.management.Pink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
internal fun DestinationHome(
    profile: Profile,
    onProfileClick: () -> Unit,
    onFinish: () -> Unit,
    source: Flow<DestinationState>? = null,
    modifier: Modifier = Modifier,
    renderCharacter: Boolean = true,
    drawBackground: Boolean = true,
    tripSessionId: String? = null,
    onCompleteTrip: suspend (String) -> Boolean = { true },
    onMapClick: () -> Unit = {},
    onArrival: (String) -> Unit = {},
    saveTrip: suspend (String?) -> Boolean = { true },
    onDestinationCategoryChange: (DestinationCategory?) -> Unit = {},
    categorySelectionState: CategorySelectionState? = null,
    homeActive: Boolean = true,
    onDemoOverrideChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val drivingRestricted = LocalDrivingRestricted.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val selection = categorySelectionState ?: remember(profile.id) { CategorySelectionState() }
    var destination by remember(profile.id) { mutableStateOf(DestinationState()) }
    var acknowledged by remember(profile.id) { mutableStateOf<String?>(null) }
    var confirm by remember(profile.id) { mutableStateOf(false) }
    var finishing by remember(profile.id) { mutableStateOf(false) }
    var pendingFinishSessionId by remember(profile.id) { mutableStateOf<String?>(null) }
    var finishError by remember(profile.id) { mutableStateOf(false) }
    var saving by remember(profile.id) { mutableStateOf(false) }
    var saveError by remember(profile.id) { mutableStateOf(false) }
    val demoInput = rememberDestinationDemoInput(profile.id)
    val demoDestination = demoInput.destination.value
    val effectiveDestination = demoDestination ?: destination
    LaunchedEffect(demoDestination != null) { onDemoOverrideChange(demoDestination != null) }
    DisposableEffect(Unit) { onDispose { onDemoOverrideChange(false) } }
    val latestCategoryChange by rememberUpdatedState(onDestinationCategoryChange)
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile.id, finishing) {
        if (finishing) {
            val completed = pendingFinishSessionId?.let { onCompleteTrip(it) } ?: true
            if (completed) {
                onFinish()
            } else {
                finishing = false
                confirm = true
                finishError = true
            }
        }
    }
    LaunchedEffect(profile.id, lifecycle, source, finishing, demoDestination == null) {
        if (finishing || demoDestination != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            (source ?: PleosDestinationSource(context.applicationContext).observe()).collect {
                if (it.routing && it.name != null) acknowledged = null
                destination = it
            }
        }
    }
    LaunchedEffect(profile.id, demoDestination?.name, demoDestination?.routing) {
        if (demoDestination?.routing == true && demoDestination.name != null) acknowledged = null
    }
    var demoArrival by remember(profile.id) { mutableStateOf<String?>(null) }
    val arrival =
        if (finishing) {
            null
        } else {
            demoArrival ?: effectiveDestination.arrived?.takeUnless { it == acknowledged }
        }
    LaunchedEffect(effectiveDestination) { selection.update(effectiveDestination) }
    val arrivalCategory =
        when {
            arrival == null -> {
                null
            }

            demoArrival != null -> {
                selection.categoryFor(effectiveDestination).takeIf {
                    effectiveDestination.name == arrival
                }
            }

            else -> {
                selection.arrivalCategoryFor(effectiveDestination, arrival)
            }
        }
    val destinationCategory =
        if (arrival != null) {
            arrivalCategory
        } else {
            selection.categoryFor(effectiveDestination).takeIf {
                effectiveDestination.routing && effectiveDestination.name != null
            }
        }
    val acknowledgeArrival = {
        if (!confirm && !finishing) {
            arrival?.let(onArrival)
            acknowledged = arrival
            if (demoDestination != null) demoInput.acknowledge()
            demoArrival = null
        }
    }
    LaunchedEffect(profile.id, destinationCategory, finishing) {
        latestCategoryChange(if (finishing) null else destinationCategory)
    }
    DisposableEffect(profile.id) {
        onDispose { latestCategoryChange(null) }
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
    val showCategoryDialog =
        selection.pending && effectiveDestination.routing && effectiveDestination.name != null &&
            arrival == null && !confirm && !finishing && !drivingRestricted && homeActive &&
            lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(showCategoryDialog, lifecycle) {
        if (showCategoryDialog) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (selection.pending) {
                    delay(100)
                    selection.tick()
                }
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
                if (arrival != null || confirm || showCategoryDialog) drawRect(Color.Black.copy(alpha = 0.2f))
            }.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        val s = minOf(maxWidth.value / 840f, maxHeight.value / 1184f)
        HomeScreen(
            profile = profile,
            onProfileClick = onProfileClick,
            onMapClick = onMapClick,
            renderCharacter = renderCharacter,
            destinationCategory = destinationCategory,
            background = if (drawBackground) painterResource(R.drawable.home_street_background) else null,
        )
        HomeDestinationSheet(
            nickname = profile.nickname,
            destination =
                arrival
                    ?: effectiveDestination.name?.takeIf { effectiveDestination.routing && it.isNotBlank() },
            scale = s,
            onFinish = { if (!finishing) confirm = true },
            modifier = Modifier.align(Alignment.BottomCenter),
            category = destinationCategory,
        )
        if (showCategoryDialog) {
            CategorySelectionDialog(
                selected = selection.selected,
                secondsRemaining = selection.secondsRemaining,
                scale = s,
                onSelect = selection::select,
                onConfirm = selection::confirm,
                onDismiss = selection::dismiss,
            )
        }
        if (arrival != null && !confirm) {
            ArrivalDialog(
                destination = arrival,
                category = arrivalCategory,
                secondsRemaining = secondsRemaining,
                scale = s,
                isDemo = demoArrival != null || demoDestination != null,
                onContinue = acknowledgeArrival,
                onFinish = { if (!finishing) confirm = true },
            )
        }
        ArrivalDemoControls(
            destination = effectiveDestination,
            demoInput = demoInput,
            isSimulating = demoArrival != null || (arrival != null && demoDestination != null),
            canSimulate = !finishing && arrival == null && !confirm,
            onArrive = { demoArrival = it },
            onReset = { demoArrival = null },
            modifier = Modifier.align(Alignment.TopStart).padding(start = (16 * s).dp, top = (144 * s).dp),
        )
        if (confirm) {
            Dialog(
                onDismissRequest = { if (!saving && !finishing) confirm = false },
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
                    if (saveError) {
                        Text("여행 기록을 저장하지 못했어요. 다시 시도해 주세요.", color = Ink, fontSize = (24 * s).sp)
                    }
                    if (finishError) {
                        Text("여행 착장을 저장하지 못했어요. 다시 시도해 주세요.", color = Pink, fontSize = (24 * s).sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((30 * s).dp)) {
                        ActionButton(if (saving) "저장 중" else "마치기", Pink, s, !saving && !drivingRestricted, {
                            if (!drivingRestricted && !finishing && !saving) {
                                saving = true
                                saveError = false
                                finishError = false
                                val pendingArrival = arrival
                                scope.launch {
                                    if (saveTrip(pendingArrival)) {
                                        pendingFinishSessionId = tripSessionId
                                        confirm = false
                                        demoArrival = null
                                        demoInput.reset()
                                        destination = DestinationState()
                                        selection.clear()
                                        latestCategoryChange(null)
                                        finishing = true
                                    } else {
                                        saveError = true
                                    }
                                    saving = false
                                }
                            }
                        }, Modifier.weight(1f))
                        ActionButton("취소", LightGray, s, !saving && !finishing, {
                            confirm = false
                            saveError = false
                            finishError = false
                        }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
