package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.room.Room
import com.example.data.PregnancyDatabase
import com.example.data.PregnancyRepository
import com.example.ui.PregnancyApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PregnancyViewModel
import com.example.viewmodel.PregnancyViewModelFactory

class MainActivity : ComponentActivity() {

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            PregnancyDatabase::class.java,
            "pregnancy_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    private val repository by lazy {
        PregnancyRepository(database.pregnancyDao())
    }

    private val viewModel: PregnancyViewModel by viewModels {
        PregnancyViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PregnancyApp(viewModel = viewModel)
            }
        }
    }
}

