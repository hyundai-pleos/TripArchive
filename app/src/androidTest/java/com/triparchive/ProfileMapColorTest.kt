package com.triparchive

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileMapColor
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.presentation.LocalDrivingRestricted
import com.triparchive.presentation.management.ProfileManagementScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileMapColorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mapColorOptionsExposeSelectionAndApplyImmediately() {
        var profile by mutableStateOf(Profile(1, "사용자", ProfileRelationship.PARENT))
        var selected: ProfileMapColor? = null
        compose.setContent {
            TripArchiveTheme {
                ProfileManagementScreen(
                    profile = profile,
                    showDeleteDialog = false,
                    busy = false,
                    error = null,
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                    onCancelDelete = {},
                    onConfirmDelete = {},
                    onLogout = {},
                    onMapColorChange = {
                        selected = it
                        profile = profile.copy(mapColor = it.name)
                    },
                )
            }
        }
        compose.onNodeWithText("지도 색상 변경").assertIsDisplayed()
        compose.onNodeWithText("로그아웃").assertIsDisplayed()
        compose.onNodeWithContentDescription("지도 색상 분홍").assertIsSelected()
        compose.onNodeWithContentDescription("지도 색상 파랑").assertIsNotSelected().performClick()
        compose.onNodeWithContentDescription("지도 색상 파랑").assertIsSelected()
        compose.runOnIdle { assertEquals(ProfileMapColor.BLUE, selected) }
    }

    @Test
    fun mapColorOptionsAreDisabledWhenDrivingOrSaving() {
        var restricted by mutableStateOf(true)
        var busy by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDrivingRestricted provides restricted) {
                TripArchiveTheme {
                    ProfileManagementScreen(
                        profile = Profile(1, "사용자", ProfileRelationship.PARENT),
                        showDeleteDialog = false,
                        busy = busy,
                        error = null,
                        onBack = {},
                        onEdit = {},
                        onDelete = {},
                        onCancelDelete = {},
                        onConfirmDelete = {},
                        onLogout = {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("지도 색상 빨강").assertIsNotEnabled()
        compose.runOnIdle {
            restricted = false
            busy = true
        }
        compose.onNodeWithContentDescription("지도 색상 빨강").assertIsNotEnabled()
    }
}
