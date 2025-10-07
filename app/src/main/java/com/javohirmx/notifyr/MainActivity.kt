package com.javohirmx.notifyr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.javohirmx.notifyr.ui.components.BottomNavigationBar
import com.javohirmx.notifyr.ui.navigation.NotifyrNavigation
import com.javohirmx.notifyr.ui.theme.NotifyrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotifyrTheme {
                NotifyrApp()
            }
        }
    }
}

@Composable
fun NotifyrApp() {
    val navController = rememberNavController()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        NotifyrNavigation(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}