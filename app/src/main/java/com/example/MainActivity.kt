package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.room.Room
import com.example.data.ALL_MIGRATIONS
import com.example.data.PregnancyDatabase
import com.example.data.PregnancyRepository
import com.example.ui.PregnancyApp
import com.example.ui.theme.PregaTheme
import com.example.viewmodel.PregnancyViewModel
import com.example.viewmodel.PregnancyViewModelFactory

class MainActivity : ComponentActivity() {

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            PregnancyDatabase::class.java,
            "pregnancy_db",
        )
            // Explicit migrations only. `fallbackToDestructiveMigration()` used
            // to be here, which meant every schema bump silently wiped the
            // user's profile, daily logs and kick history. Kick logs are a
            // medical record and the journey data is irreplaceable, so we
            // migrate properly instead — see data/Migrations.kt.
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    private val repository by lazy { PregnancyRepository(database.pregnancyDao()) }

    private val viewModel: PregnancyViewModel by viewModels {
        PregnancyViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PregaTheme {
                PregnancyApp(viewModel = viewModel)
            }
        }
    }
}
