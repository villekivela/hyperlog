package com.hyperlog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.today.TodayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HyperlogTheme {
                // Settings arrives with the gyms and equipment ticket.
                TodayScreen(onSettings = {})
            }
        }
    }
}
