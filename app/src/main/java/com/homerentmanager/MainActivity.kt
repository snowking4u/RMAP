package com.homerentmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.homerentmanager.ui.HrmApp
import com.homerentmanager.ui.theme.HrmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openBackup = intent?.getBooleanExtra(EXTRA_OPEN_BACKUP, false) == true
        setContent {
            HrmTheme {
                HrmApp(
                    container = (application as HrmApplication).container,
                    openBackupInitially = openBackup,
                )
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_BACKUP = "open_backup"
    }
}
