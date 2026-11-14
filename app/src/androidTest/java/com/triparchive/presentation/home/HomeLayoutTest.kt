package com.triparchive.presentation.home

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.presentation.home.components.HomeDestinationSheet
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun captureFullAndSplitLayoutsWithFiveProps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (permission in listOf(
            "android.car.permission.CAR_SPEED",
        )) {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, permission)
        }
        val narrow = mutableStateOf(false)
        val propCount = mutableIntStateOf(5)
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                TripArchiveTheme {
                    BoxWithConstraints(
                        if (narrow.value) Modifier.width(840.dp).fillMaxSize() else Modifier.fillMaxSize(),
                    ) {
                        HomeScreen(
                            profile = Profile(nickname = "시안 검증", relationship = ProfileRelationship.CHILD),
                            modifier = Modifier.fillMaxSize(),
                            destinationCategories =
                                listOf(
                                    DestinationCategory.TOURISM,
                                    DestinationCategory.FOOD,
                                    DestinationCategory.MART,
                                    DestinationCategory.SCHOOL,
                                    DestinationCategory.HOSPITAL,
                                ).take(propCount.intValue),
                        )
                        HomeDestinationSheet("시안 검증", "서울숲", minOf(maxWidth.value / 840f, maxHeight.value / 1184f), {
                        }, Modifier.align(Alignment.BottomCenter), DestinationCategory.TOURISM)
                    }
                }
            }
        }
        // Use real Activity/Choreographer frames: a virtual Compose test clock cannot
        // drive Filament's native render lifecycle.
        for ((split, count) in listOf(false to 2, false to 3, false to 4, false to 5, true to 5)) {
            instrumentation.runOnMainSync {
                narrow.value = split
                propCount.intValue = count
            }
            Thread.sleep(if (count == 2) 10000 else 3000)
            ParcelFileDescriptor
                .AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(
                        "screencap -p /data/local/tmp/home-31-$split-$count.png",
                    ),
                ).use { it.readBytes() }
            check(scenario.state == androidx.lifecycle.Lifecycle.State.RESUMED)
        }
        // A paused renderer must not keep receiving bone uploads from a Compose clock.
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        Thread.sleep(12000)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        Thread.sleep(3000)
        check(scenario.state == androidx.lifecycle.Lifecycle.State.RESUMED)
        scenario.close()
    }
}
