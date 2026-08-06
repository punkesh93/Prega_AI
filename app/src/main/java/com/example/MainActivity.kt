package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.billing.BillingManager
import com.example.data.ALL_MIGRATIONS
import com.example.data.PregnancyDatabase
import com.example.data.PregnancyRepository
import com.example.notifications.NotificationChannels
import com.example.notifications.NotificationScheduler
import com.example.ui.PregnancyApp
import com.example.ui.theme.PregaTheme
import com.example.viewmodel.PregnancyViewModel
import com.example.viewmodel.PregnancyViewModelFactory
import kotlinx.coroutines.launch

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
            // medical record and the journey data is irreplaceable.
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    private val repository by lazy { PregnancyRepository(database.pregnancyDao()) }

    private val viewModel: PregnancyViewModel by viewModels {
        PregnancyViewModelFactory(repository)
    }

    private val billing by lazy { BillingManager(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationChannels.registerAll(this)

        billing.connect {
            lifecycleScope.launch { billing.loadOffer() }
            // Reconciles entitlement with Play on every launch, so a
            // subscription bought elsewhere — or cancelled outside the app — is
            // reflected without the user doing anything.
            billing.refreshEntitlement { active ->
                viewModel.syncPremiumEntitlement(active)
            }
        }

        setContent {
            PregaTheme {
                val billingState by billing.connectionState.collectAsStateWithLifecycle()
                val offer by billing.offer.collectAsStateWithLifecycle()
                val billingError by billing.lastError.collectAsStateWithLifecycle()
                val isPremium by billing.isPremium.collectAsStateWithLifecycle()
                val profile by viewModel.profile.collectAsStateWithLifecycle()

                LaunchedEffect(isPremium) { viewModel.syncPremiumEntitlement(isPremium) }

                // Streak resolution, quest generation and the daily insight all
                // hang off a single "app opened" call once the profile exists.
                LaunchedEffect(profile?.onboardingComplete) {
                    if (profile?.onboardingComplete == true) viewModel.onAppOpened()
                }

                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    // Declining is fine — everything still works, she just gets
                    // no reminders. Nothing is gated on this.
                    if (granted) NotificationScheduler.scheduleAll(this@MainActivity)
                }

                PregnancyApp(
                    viewModel = viewModel,
                    billingState = billingState,
                    premiumPrice = billing.formattedPrice(offer),
                    billingError = billingError,
                    onSubscribe = {
                        offer?.let { billing.launchPurchase(this@MainActivity, it) }
                    },
                    onManageSubscription = ::openPlaySubscriptions,
                    onNotificationsToggled = { enabled ->
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                NotificationScheduler.scheduleAll(this@MainActivity)
                            }
                        } else {
                            NotificationScheduler.cancelAll(this@MainActivity)
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches subscription changes made in the Play Store while the app was
        // backgrounded.
        if (billing.connectionState.value == BillingManager.State.Ready) {
            billing.refreshEntitlement { viewModel.syncPremiumEntitlement(it) }
        }
    }

    override fun onDestroy() {
        billing.release()
        super.onDestroy()
    }

    /** Deep link straight to Play's subscription management. No retention funnel. */
    private fun openPlaySubscriptions() {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${BillingManager.PREMIUM_SUBSCRIPTION_ID}&package=$packageName"
        )
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}
