package com.triparchive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.triparchive.core.designsystem.TripArchiveTheme

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
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Preview(name = "Pleos Light", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun TripArchivePreview() {
    TripArchiveTheme(darkTheme = false) {
        TripArchiveApp()
    }
}

@Preview(name = "Pleos Dark", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun TripArchiveDarkPreview() {
    TripArchiveTheme(darkTheme = true) {
        TripArchiveApp()
    }
}
