package com.triparchive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.triparchive.core.designsystem.TripArchiveTheme

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TripArchiveTheme {
                TripArchiveApp()
            }
        }
    }
}

@Composable
fun TripArchiveApp(modifier: Modifier = Modifier) {
    com.triparchive.navigation.OnboardingNavigation(modifier)
}

@Preview(name = "Pleos", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun TripArchivePreview() {
    TripArchiveTheme {
        com.triparchive.presentation.onboarding
            .OnboardingScreen({})
    }
}
