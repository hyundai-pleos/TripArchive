package com.triparchive.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.triparchive.presentation.onboarding.OnboardingScreen
import com.triparchive.presentation.onboarding.ProfileScreen
import kotlinx.serialization.Serializable

@Serializable
private data object Welcome : NavKey

@Serializable
private data object Nickname : NavKey

@Serializable
private data object Relationship : NavKey

@Composable
fun OnboardingNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Welcome)
    var nickname by rememberSaveable { mutableStateOf("사용자1") }
    var relationship by rememberSaveable { mutableStateOf("부모님") }
    var created by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Box(modifier) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryProvider =
                entryProvider {
                    entry<Welcome> {
                        OnboardingScreen(onStart = { if (backStack.lastOrNull() == Welcome) backStack.add(Nickname) })
                    }
                    entry<Nickname> {
                        ProfileScreen(
                            false,
                            nickname,
                            relationship,
                            { nickname = it },
                            { relationship = it },
                            { backStack.removeLastOrNull() },
                            {
                                if (nickname.isNotBlank() &&
                                    backStack.lastOrNull() == Nickname
                                ) {
                                    backStack.add(Relationship)
                                }
                            },
                        )
                    }
                    entry<Relationship> {
                        ProfileScreen(
                            true,
                            nickname,
                            relationship,
                            { nickname = it },
                            { relationship = it },
                            { backStack.removeLastOrNull() },
                            {
                                context
                                    .getSharedPreferences("profile", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putString(
                                        "nickname",
                                        nickname.trim(),
                                    ).putString("relationship", relationship)
                                    .apply()
                                created = true
                            },
                        )
                    }
                },
        )
        if (created) {
            AlertDialog(
                onDismissRequest = { created = false },
                title = { Text("프로필을 만들었어요") },
                text = { Text("${nickname.trim()} · $relationship") },
                confirmButton = { TextButton(onClick = { created = false }) { Text("확인") } },
            )
        }
    }
}
