package com.recreationalgrade.fattrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.recreationalgrade.fattrack.ui.navigation.FatTrackNavHost
import com.recreationalgrade.fattrack.ui.theme.FatTrackTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FatTrackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    FatTrackNavHost(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}
