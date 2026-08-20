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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.auth.GoogleAuthManager
import com.example.auth.GoogleAuthResult
import com.example.billing.BillingManager
import com.example.data.ALL_MIGRATIONS
import com.example.data.PregnancyDatabase
import com.example.data.PregnancyRepository
import com.example.notifications.NotificationChannels
import com.example.notifications.NotificationScheduler
import com.example.stats.AppStats
import com.example.stats.StatEvent
import com.example.ui.PregnancyApp
import com.example.ui.onboarding.GoogleSignInStatus
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreference
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
    private val googleAuth by lazy { GoogleAuthManager(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Crash flight recorder, part 2 (see PregaApplication) ──
        // If the previous run died with an uncaught exception, show its
        // stack trace INSTEAD of running the app into the same crash, so
        // the exact failing line can be screenshotted and reported. This
        // check runs before every other line of init on purpose.
        val crashFile = java.io.File(filesDir, PregaApplication.CRASH_FILE)
        if (crashFile.exists()) {
            // KPI: crash-affected launches (vs Firebase's automatic total).
            AppStats.init(this)
            AppStats.log(StatEvent.AppCrashRecovered)
            val trace = runCatching { crashFile.readText() }
                .getOrDefault("(crash file unreadable)")
            setContent { CrashReportScreen(trace) { crashFile.delete(); recreate() } }
            return
        }

        enableEdgeToEdge()

        NotificationChannels.registerAll(this)
        // app_open itself is auto-collected by Firebase Analytics; init only
        // prepares the client for the feature events.
        AppStats.init(this)

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
            var themeMode by remember { mutableStateOf(ThemePreference.get(this)) }

            PregaTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            ) {
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

                var googleSignInStatus by remember { mutableStateOf(GoogleSignInStatus.Idle) }

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
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        ThemePreference.set(this@MainActivity, mode)
                    },
                    googleSignInStatus = googleSignInStatus,
                    onGoogleSignIn = onGoogleSignIn@{
                        if (googleSignInStatus == GoogleSignInStatus.InProgress) return@onGoogleSignIn
                        googleSignInStatus = GoogleSignInStatus.InProgress
                        lifecycleScope.launch {
                            when (val result = googleAuth.signIn()) {
                                is GoogleAuthResult.Success -> {
                                    viewModel.signInWithGoogle(
                                        name = result.name,
                                        email = result.email,
                                        photoUrl = result.photoUrl,
                                    )
                                    googleSignInStatus = GoogleSignInStatus.Idle
                                }
                                // Backing out of the picker isn't a failure —
                                // return to idle without any error copy.
                                GoogleAuthResult.Cancelled ->
                                    googleSignInStatus = GoogleSignInStatus.Idle
                                GoogleAuthResult.NotConfigured ->
                                    googleSignInStatus = GoogleSignInStatus.NotConfigured
                                is GoogleAuthResult.Failure ->
                                    googleSignInStatus = GoogleSignInStatus.Failed
                            }
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

/**
 * Full-screen crash trace, shown on the launch AFTER a crash instead of
 * re-running straight into it. Deliberately built from nothing but core
 * Material3 + foundation with zero app components or theme dependencies —
 * the screen that reports failures must be simple enough to never fail.
 */
@Composable
private fun CrashReportScreen(trace: String, onRetry: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
                Text(
                    "The app hit a problem last time",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "This is the technical report. Tap Copy and send it to the developer — " +
                        "it shows the exact line that failed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                SelectionContainer(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        trace,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { clipboard.setText(AnnotatedString(trace)) }) {
                        Text("Copy report")
                    }
                    OutlinedButton(onClick = onRetry) {
                        Text("Try the app again")
                    }
                }
            }
        }
    }
}
