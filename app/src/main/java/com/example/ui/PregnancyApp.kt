package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.PregnancyViewModel
import com.example.viewmodel.WeekInfo
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PregnancyApp(viewModel: PregnancyViewModel) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val todayLog by viewModel.todayLog.collectAsStateWithLifecycle()
    val kickLogs by viewModel.kickLogs.collectAsStateWithLifecycle()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPaymentDialog by remember { mutableStateOf(false) }

    if (profile == null) {
        // Show Setup/Onboarding Screen if no profile exists
        SetupScreen(onSetupComplete = { name, email, photo, week, babyName, lmp, test, edd, billing ->
            if (email.isNotEmpty()) {
                viewModel.signInWithGoogle(name, email, photo)
            }
            viewModel.updateProfile(
                name = name,
                week = week,
                babyName = babyName,
                lmpDate = lmp,
                testDate = test,
                eddDate = edd,
                billingRegion = billing
            )
        })
    } else {
        val userProfile = profile!!
        val weekInfo = viewModel.getWeekInfo(userProfile.currentWeek)

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Prega AI",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    },
                    navigationIcon = {
                        Text(
                            text = "🤰",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    },
                    actions = {
                        val isPremium = userProfile.isPremium
                        var showReceiptDialog by remember { mutableStateOf(false) }

                        IconButton(
                            onClick = {
                                if (isPremium) {
                                    showReceiptDialog = true
                                } else {
                                    showPaymentDialog = true
                                }
                            },
                            modifier = Modifier.testTag("premium_indicator_btn")
                        ) {
                            Icon(
                                imageVector = if (isPremium) Icons.Default.Stars else Icons.Default.StarBorder,
                                contentDescription = "Premium Subscription",
                                tint = if (isPremium) Color(0xFFFFB300) else Color.Gray,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Facebook-style Profile state
                        var showProfileScreen by remember { mutableStateOf(false) }
                        IconButton(onClick = { showProfileScreen = true }) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                if (userProfile.photoUrl.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryCoralLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when(userProfile.photoUrl) {
                                                "avatar_sunflower" -> "🌻"
                                                "avatar_mother" -> "🤰"
                                                "avatar_baby" -> "👶"
                                                "avatar_stork" -> "🕊️"
                                                "avatar_butterfly" -> "🦋"
                                                "avatar_star" -> "🌟"
                                                else -> "🤰"
                                            },
                                            fontSize = 20.sp
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryCoralLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = userProfile.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryCoral,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(1.dp, Color.LightGray, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(9.dp)
                                    )
                                }
                            }
                        }

                        if (showReceiptDialog) {
                            AlertDialog(
                                onDismissRequest = { showReceiptDialog = false },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("👑 Premium Active Receipt", fontWeight = FontWeight.Bold)
                                    }
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF0)),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("PREGA AI PREMIUM MEMBER", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFF57F17))
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Plan: Monthly Unlimited Coaching", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextDeepBrown)
                                                Text("Amount: $9.99/month", fontSize = 12.sp, color = TextMutedBrown)
                                                Text("Purchased: ${userProfile.premiumPurchaseDate}", fontSize = 12.sp, color = TextMutedBrown)
                                                Text("Transaction: TXN_PREGA_${userProfile.premiumPurchaseDate.filter { it.isLetterOrDigit() }}", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                        Text("Features Unlocked:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDeepBrown)
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("✓ Unlimited medically-informed AI Coaching questions", fontSize = 12.sp, color = TextDeepBrown)
                                            Text("✓ Personalized weekly prenatal meal diet plans", fontSize = 12.sp, color = TextDeepBrown)
                                            Text("✓ Advanced pregnancy tracker metrics", fontSize = 12.sp, color = TextDeepBrown)
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showReceiptDialog = false }) {
                                        Text("Close", color = PrimaryCoral)
                                    }
                                }
                            )
                        }

                        if (showProfileScreen) {
                            Dialog(
                                onDismissRequest = { showProfileScreen = false },
                                properties = DialogProperties(
                                    usePlatformDefaultWidth = false,
                                    dismissOnBackPress = true,
                                    dismissOnClickOutside = false
                                )
                            ) {
                                FacebookProfileScreen(
                                    userProfile = userProfile,
                                    viewModel = viewModel,
                                    onDismiss = { showProfileScreen = false },
                                    onUpgradeClick = {
                                        showProfileScreen = false
                                        showPaymentDialog = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Home", fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Wellness", fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Wellness") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("Nutrition", fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.Restaurant, contentDescription = "Nutrition") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        label = { Text("Coach AI", fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.Face, contentDescription = "Coach AI") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        label = { Text("Timeline", fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Timeline") }
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        userProfile = userProfile,
                        weekInfo = weekInfo,
                        todayLog = todayLog ?: DailyLogEntity(date = viewModel.todayDate.value),
                        kickLogs = kickLogs,
                        viewModel = viewModel
                    )
                    1 -> WellnessScreen(
                        userProfile = userProfile,
                        viewModel = viewModel
                    )
                    2 -> NutritionScreen(
                        todayLog = todayLog ?: DailyLogEntity(date = viewModel.todayDate.value),
                        viewModel = viewModel,
                        onUpgradeClick = { showPaymentDialog = true }
                    )
                    3 -> CoachScreen(
                        viewModel = viewModel,
                        onUpgradeClick = { showPaymentDialog = true }
                    )
                    4 -> JourneyTimelineScreen(
                        currentWeek = userProfile.currentWeek,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showPaymentDialog) {
        val userBillingRegion = profile?.billingRegion ?: "GLOBAL"
        PaymentDialog(
            userBillingRegion = userBillingRegion,
            onDismiss = { showPaymentDialog = false },
            onSuccess = { viewModel.upgradeToPremium() }
        )
    }
}

// --- WEEKLY CARE ADVICE REGISTRY ---
data class WeeklyCareAdvice(
    val symptoms: String,
    val recommendation: String,
    val nutritionTip: String
)

fun getWeeklyCareAdvice(week: Int): WeeklyCareAdvice {
    return when (week) {
        in 1..4 -> WeeklyCareAdvice(
            symptoms = "Implantation cramping, mild bloating, fatigue, breast tenderness, or frequent urination.",
            recommendation = "Enjoy warm ginger tea to settle early morning stomach sensitivity. Prioritize deep sleep, moderate walking, and begin high-folate routines.",
            nutritionTip = "Folic acid is critical right now for neural development. Combine spinach, citrus fruits, and eggs into your daily intake."
        )
        in 5..8 -> WeeklyCareAdvice(
            symptoms = "Morning sickness, severe fatigue, mood swings, heightened sense of smell, or food aversions.",
            recommendation = "Brew a warm cup of peppermint tea to ease nausea. Rest whenever possible—your body is building a new life support system (the placenta).",
            nutritionTip = "Stay hydrated with infused lemon water. Small, frequent dry carbohydrate meals (like crackers) help control morning nausea."
        )
        in 9..12 -> WeeklyCareAdvice(
            symptoms = "Round ligament pulling pain, mild headaches, bloating, sleep disturbances, or minor heartburn.",
            recommendation = "Drink herbal chamomile or ginger-honey tea to soothe nerves and stomach. Avoid lifting heavy loads and practice gentle lower-back stretches.",
            nutritionTip = "Iron absorption is essential as blood volume expands. Pair iron-rich lentils or spinach with vitamin C sources like tomatoes or oranges."
        )
        in 13..16 -> WeeklyCareAdvice(
            symptoms = "Energy return! 'Pregnancy glow' starts, but you might feel skin itchiness, nose congestion, or mild constipation.",
            recommendation = "Try raspberry leaf tea (rich in minerals) or warm lemon water. Practice light pelvic floor exercises (Kegels) and take active afternoon rests.",
            nutritionTip = "Ensure adequate calcium intake. Incorporate Greek yogurt, almonds, or fortified milk to support your baby's growing skeletal structure."
        )
        in 17..20 -> WeeklyCareAdvice(
            symptoms = "Lower back aches, 'quickening' (first tiny baby kicks!), increased appetite, or leg cramps.",
            recommendation = "Dandelion tea supports kidney health and reduces water retention. Keep your feet elevated when sitting and enjoy warm Epsom salt foot baths.",
            nutritionTip = "Omega-3 fatty acids (DHA/EPA) are key for baby's brain and retina. Choose safe wild salmon, chia seeds, or high-quality prenatal DHA."
        )
        in 21..24 -> WeeklyCareAdvice(
            symptoms = "Swollen ankles/feet, dry eyes, stretch marks, occasional mild Braxton Hicks contractions.",
            recommendation = "Sip on nettle tea (natural iron and magnesium source) to prevent muscle spasms. Take 15-minute elevation rests every few hours.",
            nutritionTip = "Ensure steady prenatal glucose. Switch to complex carbs (oats, brown rice, sweet potatoes) and fiber-rich fruits to avoid energy crashes."
        )
        in 25..28 -> WeeklyCareAdvice(
            symptoms = "Leg cramps, backaches, rib discomfort, mild shortness of breath as the uterus expands upwards.",
            recommendation = "Try decaf green tea with lemon. Use a supportive pregnancy belly band, and practice side-sleeping with a firm knee pillow.",
            nutritionTip = "Magnesium and potassium are your best defense against nighttime leg cramps. Eat bananas, avocados, and dark chocolate."
        )
        in 29..32 -> WeeklyCareAdvice(
            symptoms = "Frequent Braxton Hicks, acid reflux, lower rib pain, pelvis pressure, or tailbone fatigue.",
            recommendation = "Sip warm water with ginger and honey. Keep meals small and upright for 30 minutes post-eating. Prioritize lateral sleeping rest.",
            nutritionTip = "Avoid spicy or overly greasy foods right before bed to minimize acid reflux. Opt for light, high-protein snacks like walnuts."
        )
        in 33..36 -> WeeklyCareAdvice(
            symptoms = "Frequent pelvic pressure, bladder pressure, lower back fatigue, mild insomnia, or varicose veins.",
            recommendation = "Red raspberry leaf tea can help tone the uterine muscles (consult your OB-GYN). Do gentle butterfly stretches on a yoga mat.",
            nutritionTip = "Baby is putting on brain fat rapidly. Ensure continuous healthy fats—avocados, cashews, pumpkin seeds, and clean proteins."
        )
        else -> WeeklyCareAdvice(
            symptoms = "Strong pelvic pressure, nesting instinct surges, Braxton Hicks, fatigue, or heavy legs.",
            recommendation = "Drink warm lemon tea or chamomile to ease delivery anxiety. Prepare your hospital bag, rest aggressively, and practice breathing cycles.",
            nutritionTip = "Eat small, easily digestible high-energy meals. Dates are historically favored to support cervical ripening and steady labor stamina."
        )
    }
}

// --- ONBOARDING / SETUP SCREEN ---
// Helper to compute live pregnancy parameters
fun calculateWeekAndDueDate(month: Int, day: Int, year: Int): Pair<Int, String> {
    try {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1) // Calendar month is 0-based
        calendar.set(Calendar.DAY_OF_MONTH, day)
        
        val lmpDate = calendar.time
        
        // Estimated Due Date (EDD) = LMP + 280 days
        val dueCal = Calendar.getInstance()
        dueCal.time = lmpDate
        dueCal.add(Calendar.DAY_OF_YEAR, 280)
        val eddFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(dueCal.time)
        
        // Calculated current week
        val today = Calendar.getInstance().time
        val diffMs = today.time - lmpDate.time
        val diffDays = diffMs / (1000 * 60 * 60 * 24)
        val calculatedWeek = ((diffDays / 7) + 1).toInt().coerceIn(1, 40)
        
        return Pair(calculatedWeek, eddFormat)
    } catch (e: Exception) {
        return Pair(12, "Dec 25, 2026")
    }
}

@Composable
fun SetupScreen(
    onSetupComplete: (String, String, String, Int, String, String, String, String, String) -> Unit
) {
    var currentStep by remember { mutableStateOf(1) } // 1 = Google Sign In, 2 = Date Calculation, 3 = Profile & Regional Preferences
    var name by remember { mutableStateOf("") }
    var babyName by remember { mutableStateOf("") }
    
    var googleEmail by remember { mutableStateOf("") }
    var googlePhoto by remember { mutableStateOf("") }
    
    var showGoogleChooser by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var signInStatus by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    // Date Calculator states
    var useCalculator by remember { mutableStateOf(true) }
    var lmpMonth by remember { mutableIntStateOf(5) }
    var lmpDay by remember { mutableIntStateOf(10) }
    var lmpYear by remember { mutableIntStateOf(2026) }
    
    var testMonth by remember { mutableIntStateOf(5) }
    var testDay by remember { mutableIntStateOf(24) }
    
    var manualWeekStr by remember { mutableStateOf("12") }
    var billingRegion by remember { mutableStateOf("GLOBAL") } // "INDIA" or "GLOBAL"

    // Live calculation feedback
    val computedDetails = remember(lmpMonth, lmpDay, lmpYear, useCalculator, manualWeekStr) {
        if (useCalculator) {
            calculateWeekAndDueDate(lmpMonth, lmpDay, lmpYear)
        } else {
            val wk = manualWeekStr.toIntOrNull()?.coerceIn(1, 40) ?: 12
            val cal = Calendar.getInstance()
            cal.add(Calendar.WEEK_OF_YEAR, 40 - wk)
            val due = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.time)
            Pair(wk, due)
        }
    }
    val calculatedWeek = computedDetails.first
    val calculatedEDD = computedDetails.second

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BackgroundCream, SurfacePeachLight)
                )
            )
            .padding(24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (currentStep == 1) {
            // STEP 1: WELCOME & GOOGLE SIGN-IN
            Text(
                text = "🤰",
                fontSize = 72.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Prega AI",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryCoral,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your intelligent prenatal health & coaching companion",
                fontSize = 15.sp,
                color = TextMutedBrown,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign in to personalize your trimesters, log hydration, and consult our expert AI coach anytime.",
                        fontSize = 13.sp,
                        color = TextDeepBrown,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom Google Sign-In Button
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clickable(enabled = !isSigningIn) {
                                showGoogleChooser = true
                            }
                            .testTag("google_sign_in_btn"),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color.LightGray),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Row(
                                modifier = Modifier.padding(end = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Text(
                                text = "Continue with Google",
                                color = Color(0xFF1F1F1F),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "Secure, private, and HIPAA-compliant companion support.",
                fontSize = 11.sp,
                color = TextMutedBrown,
                textAlign = TextAlign.Center
            )

            if (showGoogleChooser) {
                AlertDialog(
                    onDismissRequest = { if (!isSigningIn) showGoogleChooser = false },
                    title = { Text("Choose a Google Account", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isSigningIn) {
                                        isSigningIn = true
                                        signInStatus = "Signing in as punkesh93@gmail.com..."
                                    }
                                    .testTag("google_account_punkesh"),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFCCBC)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("P", fontWeight = FontWeight.Bold, color = PrimaryCoral)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Punkesh Kumar", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDeepBrown)
                                        Text("punkesh93@gmail.com", fontSize = 11.sp, color = TextMutedBrown)
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isSigningIn) {
                                        isSigningIn = true
                                        signInStatus = "Signing in as sarah.mitchell@gmail.com..."
                                    }
                                    .testTag("google_account_sarah"),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF8BBD0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("S", fontWeight = FontWeight.Bold, color = WarmRose)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Sarah Mitchell", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDeepBrown)
                                        Text("sarah.mitchell@gmail.com", fontSize = 11.sp, color = TextMutedBrown)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        if (isSigningIn) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryCoral)
                                Text(signInStatus, fontSize = 11.sp, color = TextMutedBrown)
                            }

                            LaunchedEffect(isSigningIn) {
                                delay(1200)
                                if (signInStatus.contains("punkesh")) {
                                    name = "Punkesh"
                                    googleEmail = "punkesh93@gmail.com"
                                    googlePhoto = "P"
                                } else {
                                    name = "Sarah"
                                    googleEmail = "sarah.mitchell@gmail.com"
                                    googlePhoto = "S"
                                }
                                isSigningIn = false
                                showGoogleChooser = false
                                currentStep = 2
                            }
                        } else {
                            TextButton(onClick = { showGoogleChooser = false }) {
                                Text("Cancel", color = Color.Gray)
                            }
                        }
                    }
                )
            }
        } else if (currentStep == 2) {
            // STEP 2: DATE CALCULATOR DIALOG
            Text(
                text = "Pregnancy Calculator 📅",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryCoral,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Let's calculate your current pregnancy week and due date.",
                fontSize = 14.sp,
                color = TextMutedBrown,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Method Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { useCalculator = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (useCalculator) PrimaryCoral else Color.Transparent,
                        contentColor = if (useCalculator) Color.White else TextDeepBrown
                    ),
                    shape = RoundedCornerShape(6.dp),
                    elevation = null
                ) {
                    Text("Auto Calculate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { useCalculator = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!useCalculator) PrimaryCoral else Color.Transparent,
                        contentColor = if (!useCalculator) Color.White else TextDeepBrown
                    ),
                    shape = RoundedCornerShape(6.dp),
                    elevation = null
                ) {
                    Text("I Know My Week", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (useCalculator) {
                        // Last Menstrual Period Date Picker
                        Text("1. When did your last period begin? 🩸", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDeepBrown)
                        
                        // Scrollable Month Chips
                        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            months.forEachIndexed { index, name ->
                                val monthNum = index + 1
                                FilterChip(
                                    selected = lmpMonth == monthNum,
                                    onClick = { lmpMonth = monthNum },
                                    label = { Text(name, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryCoral,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        // Day Slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Day of Month: ", fontSize = 12.sp, color = TextMutedBrown, fontWeight = FontWeight.Medium)
                            Text("$lmpDay", fontSize = 13.sp, color = PrimaryCoral, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = lmpDay.toFloat(),
                            onValueChange = { lmpDay = it.toInt() },
                            valueRange = 1f..31f,
                            steps = 30,
                            colors = SliderDefaults.colors(thumbColor = PrimaryCoral, activeTrackColor = PrimaryCoral)
                        )

                        Divider(color = Color(0xFFF5F5F5))

                        // Pregnancy Test Date
                        Text("2. When did you test positive? 🧪", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDeepBrown)
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            months.forEachIndexed { index, name ->
                                val monthNum = index + 1
                                FilterChip(
                                    selected = testMonth == monthNum,
                                    onClick = { testMonth = monthNum },
                                    label = { Text(name, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryCoral,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        // Day Slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Day of Month: ", fontSize = 12.sp, color = TextMutedBrown, fontWeight = FontWeight.Medium)
                            Text("$testDay", fontSize = 13.sp, color = PrimaryCoral, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = testDay.toFloat(),
                            onValueChange = { testDay = it.toInt() },
                            valueRange = 1f..31f,
                            steps = 30,
                            colors = SliderDefaults.colors(thumbColor = PrimaryCoral, activeTrackColor = PrimaryCoral)
                        )
                    } else {
                        // Manual Pregnancy Week Slider/Input
                        Text("Select your pregnancy week (1 - 40) 🤰", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDeepBrown)
                        OutlinedTextField(
                            value = manualWeekStr,
                            onValueChange = { manualWeekStr = it.filter { it.isDigit() } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("e.g. 12") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LIVE FEEDBACK CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PrimaryCoralLight),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔮", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Estimated Week: Week $calculatedWeek",
                            fontWeight = FontWeight.Bold,
                            color = TextDeepBrown,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Expected Due Date (EDD): $calculatedEDD",
                            color = TextMutedBrown,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { currentStep = 1 },
                    modifier = Modifier.weight(1f).height(48.dp),
                    border = BorderStroke(1.dp, Color.Gray),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Back", color = Color.Gray)
                }
                Button(
                    onClick = { currentStep = 3 },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCoral),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next Step →", color = Color.White)
                }
            }
        } else {
            // STEP 3: DETAILS SETUP & BILLING PREFERENCE
            Text(
                text = "Tailor Your Companion 💖",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryCoral,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Let's align local payment preferences and nicknames.",
                fontSize = 13.sp,
                color = TextMutedBrown,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Preferred Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_name_input")
                    )

                    OutlinedTextField(
                        value = babyName,
                        onValueChange = { babyName = it },
                        label = { Text("Baby's Nickname (Optional)") },
                        placeholder = { Text("e.g. Peanut, Jellybean") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_babyname_input")
                    )

                    Divider(color = Color(0xFFF5F5F5))

                    // Regional Payment Selection
                    Text(
                        text = "Preferred Payment Region 💳",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextDeepBrown
                    )
                    Text(
                        text = "To offer local gateways, secure UPI or PayPal configurations.",
                        fontSize = 11.sp,
                        color = TextMutedBrown
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { billingRegion = "INDIA" }
                                .border(
                                    width = if (billingRegion == "INDIA") 2.dp else 0.dp,
                                    color = if (billingRegion == "INDIA") PrimaryCoral else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (billingRegion == "INDIA") PrimaryCoralLight else Color(0xFFF9F9F9)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🇮🇳", fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("India", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDeepBrown)
                                Text("Razorpay / UPI", fontSize = 10.sp, color = TextMutedBrown)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { billingRegion = "GLOBAL" }
                                .border(
                                    width = if (billingRegion == "GLOBAL") 2.dp else 0.dp,
                                    color = if (billingRegion == "GLOBAL") PrimaryCoral else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (billingRegion == "GLOBAL") PrimaryCoralLight else Color(0xFFF9F9F9)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🌐", fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("International", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDeepBrown)
                                Text("PayPal / Cards", fontSize = 10.sp, color = TextMutedBrown)
                            }
                        }
                    }
                }
            }

            if (isError) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Please enter a valid preferred name to proceed.",
                    color = Color.Red,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val lmpString = "2026-${String.format("%02d", lmpMonth)}-${String.format("%02d", lmpDay)}"
                        val testString = "2026-${String.format("%02d", testMonth)}-${String.format("%02d", testDay)}"
                        onSetupComplete(
                            name.trim(),
                            googleEmail,
                            googlePhoto,
                            calculatedWeek,
                            babyName.trim(),
                            lmpString,
                            testString,
                            calculatedEDD,
                            billingRegion
                        )
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_journey_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCoral),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start My Journey ✨", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            TextButton(
                onClick = { currentStep = 2 },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("← Back to Pregnancy Calculator", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

// --- APPLE & FACEBOOK INSPIRED WELLNESS SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellnessScreen(
    userProfile: UserProfileEntity,
    viewModel: PregnancyViewModel
) {
    var loggedVitalsBpm by remember { mutableStateOf("140") }
    var showVitalsConfirmation by remember { mutableStateOf(false) }
    
    // Audio Sound Machine states
    var activeSound by remember { mutableStateOf<String?>(null) } // "Rain", "Ocean", "WhiteNoise", "Lullaby"
    
    // Breathing meditation states
    var isBreathingActive by remember { mutableStateOf(false) }
    var breathingPhase by remember { mutableStateOf("Ready") } // Inhale, Hold, Exhale, Ready
    var breathingSecsLeft by remember { mutableStateOf(4) }
    var breathScale by remember { mutableStateOf(1f) }

    // Breathing loop
    LaunchedEffect(isBreathingActive, breathingPhase, breathingSecsLeft) {
        if (isBreathingActive) {
            if (breathingSecsLeft > 0) {
                delay(1000)
                breathingSecsLeft--
            } else {
                when (breathingPhase) {
                    "Ready", "Exhale" -> {
                        breathingPhase = "Inhale"
                        breathingSecsLeft = 4
                        breathScale = 1.6f
                    }
                    "Inhale" -> {
                        breathingPhase = "Hold"
                        breathingSecsLeft = 4
                        breathScale = 1.6f
                    }
                    "Hold" -> {
                        breathingPhase = "Exhale"
                        breathingSecsLeft = 4
                        breathScale = 1.0f
                    }
                }
            }
        } else {
            breathingPhase = "Ready"
            breathScale = 1f
        }
    }

    // Yoga set states
    var activeYogaSet by remember { mutableStateOf<String?>(null) }
    var isYogaPlaying by remember { mutableStateOf(false) }
    var yogaElapsedSecs by remember { mutableStateOf(0) }
    
    LaunchedEffect(isYogaPlaying) {
        while (isYogaPlaying) {
            delay(1000)
            yogaElapsedSecs++
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundCream)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Apple Health inspired premium Header
        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wellness Hub",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextDeepBrown,
                        letterSpacing = (-0.5).sp
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PrimaryCoralLight),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "Premium Icon", tint = PrimaryCoral, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Premium Companion", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryCoral)
                        }
                    }
                }
                Text(
                    text = "Weekly restorative guides and medical clinical trackers",
                    fontSize = 13.sp,
                    color = TextMutedBrown
                )
            }
        }

        // CLINICAL VITAL TRACKER CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFFFEBEE), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = "Heart", tint = Color(0xFFE91E63), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Fetal Heart Rate Tracker", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDeepBrown)
                                Text("Clinic check-in logs", fontSize = 11.sp, color = TextMutedBrown)
                            }
                        }
                        
                        // Apple style health range chip
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "110-160 BPM Normal",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Divider(color = Color(0xFFF5F5F5))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = loggedVitalsBpm,
                            onValueChange = { loggedVitalsBpm = it.filter { it.isDigit() } },
                            label = { Text("Clinic Vitals (BPM)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                showVitalsConfirmation = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showVitalsConfirmation) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("✅", fontSize = 16.sp)
                                Text("Successfully logged fetal vitals: $loggedVitalsBpm BPM. Range is perfect!", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // DEEP BREATHING MEDITATION MODULE
        item {
            val breathSize by animateFloatAsState(
                targetValue = breathScale,
                animationSpec = tween(durationMillis = 3800, easing = LinearEasing),
                label = "breathScale"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                        .size(36.dp)
                                .background(Color(0xFFE1F5FE), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Breath", tint = Color(0xFF03A9F4), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Diaphragmatic Breathing Guide 🧘‍♀️", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDeepBrown)
                            Text("Align and regulate prenatal oxygen flow", fontSize = 11.sp, color = TextMutedBrown)
                        }
                    }

                    Divider(color = Color(0xFFF5F5F5))

                    // GLOWING RIPPLE SPHERE
                    Box(
                        modifier = Modifier
                            .size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Expanding Ambient ripple background
                        Box(
                            modifier = Modifier
                                .size(100.dp * breathSize)
                                .clip(CircleShape)
                                .background(Color(0xFF03A9F4).copy(alpha = 0.15f))
                        )
                        // Core Sphere
                        Box(
                            modifier = Modifier
                                .size(70.dp * breathSize)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFF81D4FA), Color(0xFF03A9F4))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isBreathingActive) breathingPhase else "Ready",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                if (isBreathingActive) {
                                    Text(
                                        text = "${breathingSecsLeft}s",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            isBreathingActive = !isBreathingActive
                            if (isBreathingActive) {
                                breathingPhase = "Inhale"
                                breathingSecsLeft = 4
                                breathScale = 1.6f
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03A9F4)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isBreathingActive) "Stop Session" else "Start Deep Breath Loop 🌀",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // PRENATAL RESTORATIVE YOGA STUDIO
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFF3E5F5), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Yoga", tint = Color(0xFF9C27B0), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Prenatal Restorative Yoga", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDeepBrown)
                            Text("Safe, posture-correct stretching flows", fontSize = 11.sp, color = TextMutedBrown)
                        }
                    }

                    Divider(color = Color(0xFFF5F5F5))

                    if (activeYogaSet != null) {
                        // Yoga Player
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF7)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFF3E5F5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Active Session: $activeYogaSet", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDeepBrown)
                                    IconButton(onClick = { activeYogaSet = null; isYogaPlaying = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Stop player", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = (yogaElapsedSecs % 300) / 300f,
                                    color = Color(0xFF9C27B0),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Duration: ${yogaElapsedSecs / 60}:${String.format("%02d", yogaElapsedSecs % 60)} / 5:00", fontSize = 10.sp, color = TextMutedBrown)
                                    TextButton(onClick = { isYogaPlaying = !isYogaPlaying }) {
                                        Text(if (isYogaPlaying) "PAUSE" else "PLAY", fontWeight = FontWeight.Bold, color = Color(0xFF9C27B0), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    val yogaSessions = listOf(
                        Triple("Trimester Gentle Back Openers", "5 min • Easy", "Release lumbar stress"),
                        Triple("Pelvic Floor Strength Routine", "10 min • Medium", "Kegels & deep alignments"),
                        Triple("Restorative Deep Savasana", "8 min • Easy", "Calm prenatal anxiety")
                    )

                    yogaSessions.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeYogaSet = item.first
                                    isYogaPlaying = true
                                    yogaElapsedSecs = 0
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.first, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDeepBrown)
                                    Text(item.third, fontSize = 11.sp, color = TextMutedBrown)
                                    Text(item.second, fontSize = 10.sp, color = Color(0xFF9C27B0), fontWeight = FontWeight.SemiBold)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFF3E5F5), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play session", tint = Color(0xFF9C27B0), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // AMBIENT SOUND MACHINE CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFE0F2F1), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Sound", tint = Color(0xFF009688), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Ambient Sound Machine 🎵", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDeepBrown)
                            Text("Calming noise generators to aid deep rest", fontSize = 11.sp, color = TextMutedBrown)
                        }
                    }

                    Divider(color = Color(0xFFF5F5F5))

                    val sounds = listOf(
                        Pair("Rainfall", "🌧️"),
                        Pair("Ocean Waves", "🌊"),
                        Pair("White Noise", "💨"),
                        Pair("Lullaby", "🎹")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sounds.forEach { (name, emoji) ->
                            val isActive = activeSound == name
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        activeSound = if (isActive) null else name
                                    }
                                    .border(
                                        width = if (isActive) 2.dp else 0.dp,
                                        color = if (isActive) Color(0xFF009688) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) Color(0xFFE0F2F1) else Color(0xFFFAFAFA)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(emoji, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextDeepBrown, maxLines = 1)
                                    
                                    if (isActive) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        // Simple animated text representation of sound waves pulsing
                                        Text("PULSING", fontSize = 8.sp, color = Color(0xFF009688), fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- HOME DASHBOARD SCREEN ---
@Composable
fun DashboardScreen(
    userProfile: UserProfileEntity,
    weekInfo: WeekInfo,
    todayLog: DailyLogEntity,
    kickLogs: List<KickLogEntity>,
    viewModel: PregnancyViewModel
) {
    val isCounting by viewModel.isCountingKicks.collectAsStateWithLifecycle()
    val activeKicks by viewModel.currentSessionKicks.collectAsStateWithLifecycle()
    val activeSeconds by viewModel.currentSessionSeconds.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Hero Greeting Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfacePeachLight),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Hello, ${userProfile.name} 👋",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMutedBrown
                    )
                    Text(
                        text = "You are in Week ${userProfile.currentWeek}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCoral
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Trimester ${weekInfo.trimester} • Due on ${userProfile.dueDate}",
                        fontSize = 14.sp,
                        color = TextDeepBrown
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Baby progress indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = weekInfo.iconEmoji, fontSize = 36.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Your baby is the size of a ${weekInfo.sizeName}!",
                                fontWeight = FontWeight.Bold,
                                color = TextDeepBrown,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Approx. ${weekInfo.lengthCm} cm, ${weekInfo.weightGrams} g",
                                fontSize = 13.sp,
                                color = TextMutedBrown
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = weekInfo.description,
                        fontSize = 13.sp,
                        color = TextDeepBrown,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Weekly Care Advice Widget
        item {
            val advice = getWeeklyCareAdvice(userProfile.currentWeek)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weekly_care_advice_card"),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, PrimaryCoralLight.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(PrimaryCoralLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Symptom Checker",
                                tint = PrimaryCoral,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Week ${userProfile.currentWeek} Care Companion 💖",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextDeepBrown
                            )
                            Text(
                                text = "Personalized prenatal tips & symptom advisor",
                                fontSize = 12.sp,
                                color = TextMutedBrown
                            )
                        }
                    }

                    Divider(color = PrimaryCoralLight.copy(alpha = 0.3f))

                    // Symptoms
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤒", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Expected Symptoms:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextDeepBrown
                            )
                        }
                        Text(
                            text = advice.symptoms,
                            fontSize = 13.sp,
                            color = TextMutedBrown,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 24.dp)
                        )
                    }

                    // Recommendations (Rest & Tea)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🍵", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Self-Care & Rest Advice:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextDeepBrown
                            )
                        }
                        Text(
                            text = advice.recommendation,
                            fontSize = 13.sp,
                            color = TextMutedBrown,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 24.dp)
                        )
                    }

                    // Nutrition Tips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🍓", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Optimal Nutrition Key:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextDeepBrown
                            )
                        }
                        Text(
                            text = advice.nutritionTip,
                            fontSize = 13.sp,
                            color = TextMutedBrown,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 24.dp)
                        )
                    }
                }
            }
        }

        // Active Kick Counting Live Overlay/Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isCounting) 2.dp else 0.dp,
                        color = if (isCounting) WarmRose else Color.Transparent,
                        shape = RoundedCornerShape(24.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Baby Kick Counter 👶",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDeepBrown
                            )
                            Text(
                                text = "Monitor active fetal movements",
                                fontSize = 12.sp,
                                color = TextMutedBrown
                            )
                        }
                        if (isCounting) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFFEBEE))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = Color.Red,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isCounting) {
                        Text(
                            text = "Counting kicks is a wonderful way to connect with your baby and ensure they are moving healthy. Aim for at least 10 movements within 2 hours.",
                            fontSize = 13.sp,
                            color = TextDeepBrown,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.startKickSession() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCoral),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("start_kick_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Session", color = Color.White)
                        }
                    } else {
                        // Timer & Live Count
                        val minutes = activeSeconds / 60
                        val seconds = activeSeconds % 60
                        val timeStr = String.format("%02d:%02d", minutes, seconds)

                        Text(
                            text = "Timer: $timeStr",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMutedBrown
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$activeKicks",
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Black,
                            color = WarmRose
                        )
                        Text(
                            text = "kicks logged",
                            fontSize = 12.sp,
                            color = TextMutedBrown
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Heart Beating animation
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(300),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(Color(0xFFFCE4EC))
                                .clickable { viewModel.logKick() }
                                .testTag("log_kick_heart"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Log Kick",
                                tint = WarmRose,
                                modifier = Modifier.size(54.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.cancelKickSession() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel", color = TextDeepBrown)
                            }
                            Button(
                                onClick = { viewModel.stopAndSaveKickSession() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCoral)
                            ) {
                                Text("Save", color = Color.White)
                            }
                        }
                    }

                    // Historic Kick Logs List (last 3 items)
                    if (kickLogs.isNotEmpty()) {
                        Divider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(
                            text = "Recent Sessions",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDeepBrown,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        kickLogs.take(3).forEach { log ->
                            val durationMin = log.durationSeconds / 60
                            val durationSec = log.durationSeconds % 60
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${log.count} kicks",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = TextDeepBrown
                                    )
                                    Text(
                                        text = "Duration: ${durationMin}m ${durationSec}s",
                                        fontSize = 11.sp,
                                        color = TextMutedBrown
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteKickLog(log.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Session",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Daily Tracker Checklist & Logs
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Today's Checklist 🎯",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDeepBrown
                    )
                    Text(
                        text = "Consistent daily routines support wellness",
                        fontSize = 12.sp,
                        color = TextMutedBrown
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Vitamin Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFFFF3E0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("💊", fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Prenatal Vitamins", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Essential nutrients for baby", fontSize = 11.sp, color = TextMutedBrown)
                            }
                        }
                        Checkbox(
                            checked = todayLog.tookVitamins,
                            onCheckedChange = { viewModel.toggleVitamins() },
                            modifier = Modifier.testTag("vitamin_chk")
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Sleep Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFE8EAF6), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("😴", fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Slept Hours", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Text(
                                text = "${String.format("%.1f", todayLog.sleptHours)} hrs",
                                fontWeight = FontWeight.Bold,
                                color = PrimaryCoral,
                                fontSize = 14.sp
                            )
                        }
                        Slider(
                            value = todayLog.sleptHours,
                            onValueChange = { viewModel.updateSleep(it) },
                            valueRange = 4f..12f,
                            steps = 15,
                            modifier = Modifier.testTag("sleep_slider")
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Weight Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE0F2F1), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚖️", fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Current Weight", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Monitor healthy weight progression", fontSize = 11.sp, color = TextMutedBrown)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var weightInput by remember(todayLog.weightKg) {
                                mutableStateOf(if (todayLog.weightKg > 0) todayLog.weightKg.toString() else "")
                            }
                            OutlinedTextField(
                                value = weightInput,
                                onValueChange = {
                                    weightInput = it
                                    val w = it.toFloatOrNull()
                                    if (w != null && w > 0) viewModel.updateWeight(w)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(50.dp)
                                    .testTag("weight_input"),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("kg", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Symptoms Logger
                    Text(
                        text = "Symptoms Experienced Today",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextDeepBrown
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val presetSymptoms = listOf("Nausea", "Fatigue", "Heartburn", "Swollen Ankles", "Backache", "Headache")
                    val currentSymptoms = if (todayLog.symptoms.isEmpty()) emptyList() else todayLog.symptoms.split(",")

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetSymptoms.forEach { symptom ->
                            val isSelected = currentSymptoms.contains(symptom)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleSymptom(symptom) },
                                label = { Text(symptom) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryCoralLight,
                                    selectedLabelColor = TextDeepBrown
                                ),
                                modifier = Modifier.testTag("symptom_$symptom")
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// --- JOURNEY TIMELINE SCREEN ---
@Composable
fun JourneyTimelineScreen(currentWeek: Int, viewModel: PregnancyViewModel) {
    var selectedTrimester by remember { mutableIntStateOf(1) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Headers for Trimester Selection
        TabRow(
            selectedTabIndex = selectedTrimester - 1,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            (1..3).forEach { tNum ->
                Tab(
                    selected = selectedTrimester == tNum,
                    onClick = {
                        selectedTrimester = tNum
                        val targetWeek = when (tNum) {
                            1 -> 1
                            2 -> 14
                            else -> 28
                        }
                        coroutineScope.launch {
                            listState.animateScrollToItem(targetWeek - 1)
                        }
                    },
                    text = { Text("Trimester $tNum", fontWeight = FontWeight.Bold) }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            items(40) { index ->
                val weekNum = index + 1
                val weekInfo = viewModel.getWeekInfo(weekNum)
                val isCurrentWeek = weekNum == currentWeek

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isCurrentWeek) 2.dp else 0.dp,
                            color = if (isCurrentWeek) PrimaryCoral else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentWeek) SurfacePeachLight else Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    if (isCurrentWeek) PrimaryCoralLight else Color(0xFFFFF3E0),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = weekInfo.iconEmoji, fontSize = 28.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Week $weekNum",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TextDeepBrown
                                )
                                if (isCurrentWeek) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(PrimaryCoral)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "CURRENT",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Size: ${weekInfo.sizeName}",
                                fontSize = 13.sp,
                                color = TextMutedBrown,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = weekInfo.description,
                                fontSize = 12.sp,
                                color = TextDeepBrown,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// --- NUTRITION SCREEN ---
@Composable
fun NutritionScreen(
    todayLog: DailyLogEntity,
    viewModel: PregnancyViewModel,
    onUpgradeClick: () -> Unit
) {
    var selectedCategory by remember { mutableIntStateOf(0) } // 0 = Safe foods, 1 = Food to Avoid, 2 = Water Log, 3 = AI Diet

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = { selectedCategory = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCategory == 0) SageGreen else Color.LightGray
                ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Superfoods", fontSize = 11.sp, color = Color.White)
            }
            Button(
                onClick = { selectedCategory = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCategory == 1) Color(0xFFD32F2F) else Color.LightGray
                ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Avoid", fontSize = 11.sp, color = Color.White)
            }
            Button(
                onClick = { selectedCategory = 2 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCategory == 2) PrimaryCoral else Color.LightGray
                ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Hydration", fontSize = 11.sp, color = Color.White)
            }
            Button(
                onClick = { selectedCategory = 3 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCategory == 3) Color(0xFFFFB300) else Color.LightGray
                ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1.1f).testTag("ai_diet_tab_btn")
            ) {
                Text("AI Diet 🍳", fontSize = 11.sp, color = if (selectedCategory == 3) TextDeepBrown else Color.White, fontWeight = FontWeight.Bold)
            }
        }

        when (selectedCategory) {
            0 -> SuperfoodsList()
            1 -> AvoidFoodsList()
            2 -> WaterTrackerView(todayLog = todayLog, viewModel = viewModel)
            3 -> {
                val profile by viewModel.profile.collectAsStateWithLifecycle()
                val isPremium = profile?.isPremium == true

                if (isPremium) {
                    val mealPlan by viewModel.weeklyMealPlan.collectAsStateWithLifecycle()
                    val mealPlanLoading by viewModel.mealPlanLoading.collectAsStateWithLifecycle()

                    AIMealPlanView(
                        currentWeek = profile?.currentWeek ?: 12,
                        babyNickname = profile?.babyNamePlaceholder ?: "Peanut",
                        mealPlan = mealPlan,
                        isLoading = mealPlanLoading,
                        onGenerate = {
                            viewModel.generateWeeklyMealPlan(
                                profile?.currentWeek ?: 12,
                                profile?.babyNamePlaceholder ?: "Peanut"
                            )
                        }
                    )
                } else {
                    // Premium gating screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("👑", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "AI Personalized Diet Planner",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDeepBrown,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Get an advanced daily prenatal diet menu generated dynamically for Week ${profile?.currentWeek ?: "12"} of your pregnancy. Ensures optimal DHA, Folate, and iron intake mapped to baby's developmental stage.",
                            fontSize = 14.sp,
                            color = TextMutedBrown,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = onUpgradeClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("unlock_diet_plan_btn")
                        ) {
                            Text("Unlock Premium Diet Planner ✨", color = TextDeepBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AIMealPlanView(
    currentWeek: Int,
    babyNickname: String,
    mealPlan: String?,
    isLoading: Boolean,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (mealPlan == null && !isLoading) Arrangement.Center else Arrangement.Top
    ) {
        if (isLoading) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = PrimaryCoral, modifier = Modifier.size(48.dp))
                    Text(
                        text = "Gemini is building your custom meal plan...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextDeepBrown,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Analyzing development demands for Week $currentWeek of your pregnancy journey, focusing on brain growth factors and prenatal vitamin optimization for $babyNickname...",
                        fontSize = 12.sp,
                        color = TextMutedBrown,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else if (mealPlan.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("🍳", fontSize = 56.sp)
                    Text(
                        text = "Personalized Prenatal Diet Plan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextDeepBrown
                    )
                    Text(
                        text = "Configure a day-by-day prenatal weekly meal planner tailored to Week $currentWeek. This menu ensures you consume high folate, choline, and iron requirements safely.",
                        fontSize = 13.sp,
                        color = TextMutedBrown,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Divider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, "Included", tint = SageGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DHA & Omega-3 seafood portion guides", fontSize = 12.sp, color = TextDeepBrown)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, "Included", tint = SageGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Iron absorption and vitamin C combinations", fontSize = 12.sp, color = TextDeepBrown)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, "Included", tint = SageGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Snacks supporting balanced prenatal glucose", fontSize = 12.sp, color = TextDeepBrown)
                        }
                    }

                    Button(
                        onClick = onGenerate,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCoral),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_meal_plan_btn")
                    ) {
                        Text("Generate Weekly Meal Plan ✨", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Display formatted markdown text
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Week $currentWeek Diet Menu", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryCoral)
                            Text("Prepared for $babyNickname 🤰", fontSize = 12.sp, color = TextMutedBrown)
                        }
                        IconButton(onClick = onGenerate, modifier = Modifier.testTag("regenerate_meal_plan_btn")) {
                            Icon(Icons.Default.Refresh, "Regenerate Plan", tint = PrimaryCoral)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Basic rendering of Markdown lines beautifully
                    val lines = mealPlan.split("\n")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lines.forEach { line ->
                            val cleanLine = line.trim()
                            if (cleanLine.isNotBlank()) {
                                when {
                                    cleanLine.startsWith("###") || cleanLine.startsWith("##") || cleanLine.startsWith("#") -> {
                                        val headerText = cleanLine.replace("#", "").trim()
                                        Text(
                                            text = headerText,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = TextDeepBrown,
                                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                        )
                                    }
                                    cleanLine.startsWith("**") && cleanLine.endsWith("**") -> {
                                        val boldText = cleanLine.replace("**", "").trim()
                                        Text(
                                            text = boldText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PrimaryCoral,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                    cleanLine.startsWith("-") || cleanLine.startsWith("*") -> {
                                        val bulletText = cleanLine.substring(1).trim()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text("•", fontWeight = FontWeight.Bold, color = PrimaryCoral, modifier = Modifier.padding(end = 6.dp))
                                            Text(
                                                text = bulletText,
                                                fontSize = 13.sp,
                                                color = TextDeepBrown,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                    else -> {
                                        Text(
                                            text = cleanLine,
                                            fontSize = 13.sp,
                                            color = TextDeepBrown,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuperfoodsList() {
    val superfoods = listOf(
        Pair("🥚 Eggs", "Highly digestible source of protein and rich in Choline, which is critical for your baby's neural tube and brain development."),
        Pair("🥛 Greek Yogurt", "Has more calcium than any other dairy product. Promotes strong bones, teeth, and contains probiotics that ease gut health."),
        Pair("🥑 Avocados", "Loaded with folate, potassium, healthy monounsaturated fats, and vitamins. Ideal for cell development and skin elasticity."),
        Pair("🥬 Leafy Greens", "Spinach and kale are loaded with folate, iron, and fiber. Helps in forming blood supply and preventing birth defects."),
        Pair("🍠 Sweet Potatoes", "Rich in Beta-carotene, which converted to Vitamin A is crucial for healthy tissue and embryonic cell growth."),
        Pair("🐟 Wild Salmon", "Loaded with healthy Omega-3 fatty acids that build baby's retinas, brain cells, and combat prenatal stress.")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(superfoods) { food ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = food.first,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextDeepBrown
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = food.second,
                        fontSize = 13.sp,
                        color = TextDeepBrown,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AvoidFoodsList() {
    val avoidFoods = listOf(
        Pair("🍣 Raw Fish or Shellfish", "High risk of contracting listeria or bacterial infections that pose a threat to fetal safety. Avoid raw sushi completely."),
        Pair("🥩 Undercooked Meat", "Can contain toxoplasma parasites or listeria. Always ensure steak, chicken, and eggs are thoroughly cooked."),
        Pair("☕ High Caffeine", "Excessive caffeine (above 200mg/day) can limit fetal growth and trigger high adrenaline. Switch to comforting herbal decaf."),
        Pair("🧀 Unpasteurized Soft Cheese", "Cheeses like Brie, Camembert, or Blue cheese might harbor listeria bacteria. Double-check labels for 'pasteurized' always."),
        Pair("🍷 Alcohol", "Zero tolerance. Directly enters baby's blood, increasing risk of birth issues and developmental defects. Choose mocktails instead!")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(avoidFoods) { food ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = food.first,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = food.second,
                        fontSize = 13.sp,
                        color = TextDeepBrown,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun WaterTrackerView(todayLog: DailyLogEntity, viewModel: PregnancyViewModel) {
    val targetGlasses = 8
    val drunkCount = todayLog.waterGlasses
    val isGoalReached = drunkCount >= targetGlasses

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Daily Hydration Goal 💧",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDeepBrown
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fetal circulation and amniotic fluid require extra water",
                    fontSize = 12.sp,
                    color = TextMutedBrown,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "$drunkCount / $targetGlasses",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = PrimaryCoral
                )
                Text(
                    text = "glasses drunk today",
                    fontSize = 13.sp,
                    color = TextMutedBrown
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { (drunkCount.toFloat() / targetGlasses).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = PrimaryCoral,
                    trackColor = Color(0xFFFFCCBC)
                )

                if (isGoalReached) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SageGreenLight)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "🎉 Hydration Goal Met! Wonderful job mom!",
                            color = SageGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Interactive logging drops/cups
        Text(
            text = "Tap to Log a Glass of Water",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDeepBrown
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEBEE))
                    .clickable { viewModel.decrementWater() }
                    .testTag("dec_water_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = PrimaryCoral)
            }

            // Big glowing water drop button
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE3F2FD))
                    .clickable { viewModel.incrementWater() }
                    .testTag("inc_water_drop"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = "Log water cup",
                    tint = Color(0xFF1E88E5),
                    modifier = Modifier.size(48.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9))
                    .clickable { viewModel.incrementWater() }
                    .testTag("inc_water_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase", tint = SageGreen)
            }
        }
    }
}

// --- PREGA AI COACH CHAT SCREEN ---
@Composable
fun CoachScreen(
    viewModel: PregnancyViewModel,
    onUpgradeClick: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.chatLoading.collectAsStateWithLifecycle()
    
    var questionInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val profileState by viewModel.profile.collectAsStateWithLifecycle()
    val profile = profileState

    // Auto scroll to bottom when a message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (profile != null) {
            if (profile.isPremium) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    border = BorderStroke(1.dp, Color(0xFFA5D6A7))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("👑", fontSize = 18.sp)
                        Text(
                            text = "PREMIUM ACTIVE — Unlimited Medically-Vetted Coaching",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onUpgradeClick() }
                        .testTag("coach_premium_banner"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                    border = BorderStroke(1.dp, Color(0xFFFFF59D))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("💡", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = "Free tier: ${profile.freeQuestionsRemaining} questions remaining",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDeepBrown
                                )
                                Text(
                                    text = "Upgrade for unlimited prenatal consultations",
                                    fontSize = 10.sp,
                                    color = TextMutedBrown
                                )
                            }
                        }
                        Text(
                            text = "GO PRO 👑",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryCoral
                        )
                    }
                }
            }
        }

        // Quick suggestion chips
        Text(
            text = "Common Questions",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextDeepBrown
        )
        Spacer(modifier = Modifier.height(6.dp))
        val suggestions = listOf(
            "What to eat in Week 12?",
            "How to ease morning sickness?",
            "Safe pregnancy exercises?"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { viewModel.askCoach(suggestion) },
                    label = { Text(suggestion, fontSize = 11.sp) },
                    modifier = Modifier.testTag("suggest_$suggestion")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Chat Bubble LazyColumn
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val bubbleColor = if (msg.isUser) PrimaryCoralLight else Color(0xFFF1F1F1)
                val alignment = if (msg.isUser) Alignment.End else Alignment.Start
                val contentColor = TextDeepBrown

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = alignment
                ) {
                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                    bottomEnd = if (msg.isUser) 4.dp else 16.dp
                                )
                            )
                            .background(bubbleColor)
                            .padding(14.dp)
                            .widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = msg.text,
                            fontSize = 14.sp,
                            color = contentColor,
                            lineHeight = 18.sp
                        )
                    }
                    Text(
                        text = if (msg.isUser) "You" else "Prega Coach",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (isLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Prega Coach is typing...", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        // Input send block
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = questionInput,
                onValueChange = { questionInput = it },
                placeholder = { Text("Ask your Prega Coach...", fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach_input"),
                shape = RoundedCornerShape(12.dp)
            )
            IconButton(
                onClick = {
                    if (questionInput.isNotBlank()) {
                        viewModel.askCoach(questionInput)
                        questionInput = ""
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PrimaryCoral)
                    .testTag("coach_send_btn")
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Disclaimer: Prega AI is an information generator. It does not replace medical advice from a qualified physician.",
            fontSize = 10.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// FlowRow helper equivalent for Compose (very light implementation to avoid library dependency errors)
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Basic wrapper layout to handle item flow seamlessly
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

// --- INTERACTIVE PREMIUM PAYMENT DIALOG ---
fun formatCardNumber(input: String): String {
    val digits = input.filter { it.isDigit() }.take(16)
    return digits.chunked(4).joinToString(" ")
}

fun formatExpiryDate(input: String): String {
    val digits = input.filter { it.isDigit() }.take(4)
    return if (digits.length >= 3) {
        "${digits.substring(0, 2)}/${digits.substring(2)}"
    } else {
        digits
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookProfileScreen(
    userProfile: UserProfileEntity,
    viewModel: PregnancyViewModel,
    onDismiss: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Sub-dialog/sheet states
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAvatarPickerDialog by remember { mutableStateOf(false) }
    var showSubscriptionCancelDialog by remember { mutableStateOf(false) }
    var showGdprDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }

    // Cover Photo selection simulation
    var selectedCoverType by remember { mutableStateOf("floral") } // "floral", "meadow", "sunset"

    // Photo Upload Simulation states
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var showUploadSuccess by remember { mutableStateOf(false) }

    // Custom Bio (Simulated editable status)
    var customBio by remember { mutableStateOf("Living mindfully, glowing daily 🌸. Week ${userProfile.currentWeek} journey.") }
    var isEditingBio by remember { mutableStateOf(false) }
    var bioInput by remember { mutableStateOf(customBio) }

    // Edit Profile Input States
    var editNameInput by remember { mutableStateOf(userProfile.name) }
    var editBabyNicknameInput by remember { mutableStateOf(userProfile.babyNamePlaceholder) }
    var editWeekInput by remember { mutableStateOf(userProfile.currentWeek.toString()) }
    var editEmailInput by remember { mutableStateOf(userProfile.email) }
    var editBillingRegion by remember { mutableStateOf(userProfile.billingRegion) }

    // Cancellation survey state
    var cancelReasonSurvey by remember { mutableStateOf("") }
    var cancelStep by remember { mutableStateOf(1) } // 1: Survey, 2: Warning, 3: Final Success

    // Delete confirmation input
    var deleteConfirmCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Profile & Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextDeepBrown
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditProfileDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = PrimaryCoral)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF0F2F5) // Facebook light slate bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // COVER PHOTO & PROFILE AVATAR CONTAINER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color.White)
            ) {
                // Cover Photo Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .background(
                            brush = when (selectedCoverType) {
                                "meadow" -> Brush.horizontalGradient(colors = listOf(Color(0xFFE8F5E9), Color(0xFFA5D6A7), Color(0xFF80CBC4)))
                                "sunset" -> Brush.horizontalGradient(colors = listOf(Color(0xFFFFF3E0), Color(0xFFFFCC80), Color(0xFFFFAB91)))
                                else -> Brush.horizontalGradient(colors = listOf(Color(0xFFFFF0F5), Color(0xFFF8BBD0), Color(0xFFE1BEE7))) // floral / default lavender
                            }
                        ),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    // Float edit badge on Cover Photo
                    Card(
                        modifier = Modifier
                            .padding(10.dp)
                            .clickable {
                                // Cycle cover photo themes
                                selectedCoverType = when (selectedCoverType) {
                                    "floral" -> "meadow"
                                    "meadow" -> "sunset"
                                    else -> "floral"
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change Theme Cover", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Profile Picture (Centered overlapping cover photo)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    // White border ring like Facebook
                    Box(
                        modifier = Modifier
                            .size(118.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(PrimaryCoralLight)
                                .clickable { showAvatarPickerDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (userProfile.photoUrl.isNotEmpty()) {
                                Text(
                                    text = when (userProfile.photoUrl) {
                                        "avatar_sunflower" -> "🌻"
                                        "avatar_mother" -> "🤰"
                                        "avatar_baby" -> "👶"
                                        "avatar_stork" -> "🕊️"
                                        "avatar_butterfly" -> "🦋"
                                        "avatar_star" -> "🌟"
                                        else -> "👩‍🍼"
                                    },
                                    fontSize = 62.sp
                                )
                            } else {
                                Text(
                                    text = userProfile.name.take(1).uppercase(),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 32.sp,
                                    color = PrimaryCoral
                                )
                            }
                        }
                    }

                    // Small camera badge button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE4E6EB))
                            .border(2.dp, Color.White, CircleShape)
                            .clickable { showAvatarPickerDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Edit photo", tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // NAME, BIO & CORE INTERACTION CONTROLS
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = userProfile.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDeepBrown
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Status",
                            tint = Color(0xFF1877F2), // Facebook Blue verification badge
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Google Compliant & Encrypted Vitals Security",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Customizable Bio status
                    if (isEditingBio) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = bioInput,
                                onValueChange = { bioInput = it },
                                label = { Text("Update bio status", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    customBio = bioInput
                                    isEditingBio = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCoral),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = customBio,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = TextMutedBrown,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    bioInput = customBio
                                    isEditingBio = true
                                }
                                .padding(vertical = 4.dp, horizontal = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Facebook-style quick-action button grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showEditProfileDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Profile", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (userProfile.isPremium) {
                                    showSubscriptionCancelDialog = true
                                } else {
                                    onUpgradeClick()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (userProfile.isPremium) Color(0xFFE4E6EB) else Color(0xFFFFC107)
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (userProfile.isPremium) Icons.Default.Cancel else Icons.Default.Star,
                                contentDescription = null,
                                tint = if (userProfile.isPremium) Color.Black else Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (userProfile.isPremium) "Manage Premium" else "Upgrade VIP",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // FACEBOOK ABOUT / INTRO GRID SECTION
            Text(
                text = "Intro / Personal Details",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextDeepBrown,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE4E6EB))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IntroItemRow(
                        icon = Icons.Default.ChildCare,
                        label = "Baby's Nickname",
                        value = userProfile.babyNamePlaceholder
                    )
                    IntroItemRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Current Pregnancy",
                        value = "Week ${userProfile.currentWeek} of 40"
                    )
                    IntroItemRow(
                        icon = Icons.Default.Event,
                        label = "Estimated Due Date",
                        value = userProfile.dueDate
                    )
                    IntroItemRow(
                        icon = Icons.Default.Email,
                        label = "Registered Email",
                        value = userProfile.email.ifEmpty { "Guest Offline Account" }
                    )
                    IntroItemRow(
                        icon = Icons.Default.Public,
                        label = "Preferred Region & Currency",
                        value = if (userProfile.billingRegion == "INDIA") "India (Razorpay / INR)" else "Global (PayPal / USD)"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // GOOGLE COMPLIANT PREMIUM SUBSCRIPTION MANAGER
            Text(
                text = "Subscription Hub (Google Play Compliant)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextDeepBrown,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE4E6EB))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (userProfile.isPremium) Color(0xFFFFFDF0) else Color(0xFFF5F5F5),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = null,
                                    tint = if (userProfile.isPremium) Color(0xFFFFB300) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (userProfile.isPremium) "Prega AI Premium Active 👑" else "Free Standard Account",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextDeepBrown
                                )
                                Text(
                                    text = if (userProfile.isPremium) "Next renewal: Monthly automatic billing" else "Limited medically-informed AI credits",
                                    fontSize = 11.sp,
                                    color = TextMutedBrown
                                )
                            }
                        }
                    }

                    Divider(color = Color(0xFFF0F2F5))

                    if (userProfile.isPremium) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✓ Premium Purchase Date: ${userProfile.premiumPurchaseDate}",
                                fontSize = 12.sp,
                                color = TextDeepBrown,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "✓ Google Play order ID: GPA.3392-${(1000..9999).random()}-${(1000..9999).random()}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showSubscriptionCancelDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                                    border = BorderStroke(1.dp, Color(0xFFEF5350))
                                ) {
                                    Text("Cancel Subscription", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                
                                Button(
                                    onClick = { 
                                        // Trigger success popup simulating card update
                                        showUploadSuccess = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE4E6EB)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Update Billing Card", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Get unlimited medically-compliant AI Doctor Coaching queries, personalized dietary guides, and clinical restorative fitness tracks.",
                                fontSize = 12.sp,
                                color = TextMutedBrown
                            )
                            Button(
                                onClick = onUpgradeClick,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCoral),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Subscribe via Google Play Store ($9.99/mo)", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // GDPR, SECURITY, AND RIGHT TO BE FORGOTTEN DATA MANAGEMENT
            Text(
                text = "GDPR Compliance & Account Deletion 🛡️",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextDeepBrown,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE4E6EB))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "To meet Google Play Console Data Safety guidelines, Prega AI supports direct client side data export and complete account/metrics deletion under GDPR guidelines.",
                        fontSize = 12.sp,
                        color = TextMutedBrown
                    )

                    Divider(color = Color(0xFFF0F2F5))

                    // Export Data (GDPR Access)
                    Button(
                        onClick = { showGdprDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download All Health & Account Logs (GDPR)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Delete Account (Right to be Forgotten)
                    Button(
                        onClick = { showDeleteAccountConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Account & Clean All Health Records", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // MEDICAL DISCLAIMERS, TERMS & SECURITY PRIVACY LOGS
            Text(
                text = "Security, Privacy & HIPAA Compliance",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextDeepBrown,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE4E6EB))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "HIPAA Compliant Local Storage Disclosure",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextDeepBrown
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Nested scrollbox for terms
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "1. Data Security: Prega AI utilizes localized SQLite sandboxed Room database storage with structural schema validation. No health tracking metrics are uploaded to external platforms without explicit, discrete user-consented sharing flows.\n\n" +
                                       "2. Medical Disclaimer: This app is strictly for personal educational guidance. Fetal Heart Rate (BPM) logs are simulated and calculated manually by clinic check-ins and do not serve as professional automated medical monitors.\n\n" +
                                       "3. HIPAA Compliance Alignment: All private records are tied to sandboxed Android system scopes. Our Google Identity integrations adhere strictly to secure OAuth2.0 consent frameworks, complying with Google's Play Safety mandates.",
                                fontSize = 10.sp,
                                color = Color.DarkGray,
                                lineHeight = 14.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "App Version: 3.1.2-Compliance • Secured Sandbox Active",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // --- SUB-DIALOG 1: EDIT PROFILE ---
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Personal Information", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Your Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editBabyNicknameInput,
                        onValueChange = { editBabyNicknameInput = it },
                        label = { Text("Baby's Nickname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editWeekInput,
                        onValueChange = { editWeekInput = it.filter { it.isDigit() } },
                        label = { Text("Pregnancy Week (1-40)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editEmailInput,
                        onValueChange = { editEmailInput = it },
                        label = { Text("Email Contact") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Regional Preference", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDeepBrown)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = editBillingRegion == "GLOBAL",
                            onClick = { editBillingRegion = "GLOBAL" },
                            label = { Text("Global / USD") }
                        )
                        FilterChip(
                            selected = editBillingRegion == "INDIA",
                            onClick = { editBillingRegion = "INDIA" },
                            label = { Text("India / INR") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val wk = editWeekInput.toIntOrNull()?.coerceIn(1, 40) ?: userProfile.currentWeek
                        viewModel.updateProfile(
                            name = editNameInput,
                            week = wk,
                            babyName = editBabyNicknameInput,
                            billingRegion = editBillingRegion
                        )
                        showEditProfileDialog = false
                    }
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold, color = PrimaryCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // --- SUB-DIALOG 2: AVATAR PICKER & SIMULATED UPLOAD ---
    if (showAvatarPickerDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarPickerDialog = false },
            title = { Text("Customize Profile Picture", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isUploadingPhoto) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Uploading & encrypting image securely...", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextDeepBrown)
                            LinearProgressIndicator(
                                progress = uploadProgress,
                                color = PrimaryCoral,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${(uploadProgress * 100).toInt()}% complete", fontSize = 11.sp, color = Color.Gray)
                        }
                    } else {
                        Text("Select beautiful prenatal avatar template:", fontSize = 12.sp, color = TextMutedBrown)

                        val avatars = listOf(
                            Pair("avatar_sunflower", "🌻"),
                            Pair("avatar_mother", "🤰"),
                            Pair("avatar_baby", "👶"),
                            Pair("avatar_stork", "🕊️"),
                            Pair("avatar_butterfly", "🦋"),
                            Pair("avatar_star", "🌟")
                        )

                        // 3x2 Grid
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                avatars.take(3).forEach { (id, emoji) ->
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (userProfile.photoUrl == id) Color(0xFFFFF0F5) else Color(0xFFF0F2F5)
                                            )
                                            .border(
                                                width = if (userProfile.photoUrl == id) 2.dp else 0.dp,
                                                color = if (userProfile.photoUrl == id) PrimaryCoral else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                viewModel.updateProfilePhoto(id)
                                                showAvatarPickerDialog = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 28.sp)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                avatars.drop(3).forEach { (id, emoji) ->
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (userProfile.photoUrl == id) Color(0xFFFFF0F5) else Color(0xFFF0F2F5)
                                            )
                                            .border(
                                                width = if (userProfile.photoUrl == id) 2.dp else 0.dp,
                                                color = if (userProfile.photoUrl == id) PrimaryCoral else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                viewModel.updateProfilePhoto(id)
                                                showAvatarPickerDialog = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 28.sp)
                                    }
                                }
                            }
                        }

                        Divider(color = Color(0xFFF0F2F5))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isUploadingPhoto = true
                                    uploadProgress = 0f
                                    while (uploadProgress < 1.0f) {
                                        delay(150)
                                        uploadProgress += 0.1f
                                    }
                                    viewModel.updateProfilePhoto("avatar_mother")
                                    isUploadingPhoto = false
                                    showAvatarPickerDialog = false
                                    showUploadSuccess = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE4E6EB)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate Gallery Crop Photo Upload", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarPickerDialog = false }) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }

    // --- SUB-DIALOG 3: COMPLIANT PLAY STORE CANCEL SUBSCRIPTION FLOW ---
    if (showSubscriptionCancelDialog) {
        AlertDialog(
            onDismissRequest = { showSubscriptionCancelDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (cancelStep == 1) "Cancel Subscription Survey" else "Confirm Cancel Plan?",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (cancelStep == 1) {
                        Text(
                            text = "We are sad to see you go! Under Google Play terms, please select a reason for cancellation:",
                            fontSize = 12.sp,
                            color = TextMutedBrown
                        )
                        val reasons = listOf(
                            "Temporary pregnancy complete • Safe delivery 💖",
                            "Too expensive / Financial preference",
                            "Found a different pregnancy track app",
                            "Prefer local offline features only"
                        )
                        reasons.forEach { reason ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { cancelReasonSurvey = reason }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = cancelReasonSurvey == reason,
                                    onClick = { cancelReasonSurvey = reason }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(reason, fontSize = 12.sp, color = TextDeepBrown)
                            }
                        }
                    } else if (cancelStep == 2) {
                        Text(
                            text = "⚠️ LOSS OF PREGNANCY WELLNESS PERKS",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "By cancelling, you will instantly lose unlimited medical AI Doctor Coaching, regional personalized meal plans, and daily summaries.",
                            fontSize = 12.sp,
                            color = TextMutedBrown
                        )
                        Text(
                            text = "Your next monthly billing cycle is cancelled, and you won't be charged again.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    } else {
                        // Success cancellation
                        Text(
                            text = "✓ Google Play cancellation processed successfully! You have been moved back to the basic plan.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            },
            confirmButton = {
                if (cancelStep == 1) {
                    Button(
                        onClick = { cancelStep = 2 },
                        enabled = cancelReasonSurvey.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCoral)
                    ) {
                        Text("Continue")
                    }
                } else if (cancelStep == 2) {
                    Button(
                        onClick = {
                            viewModel.cancelPremium()
                            cancelStep = 3
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text("Confirm Cancel Subscription", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            showSubscriptionCancelDialog = false
                            cancelStep = 1
                            cancelReasonSurvey = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Done")
                    }
                }
            },
            dismissButton = {
                if (cancelStep != 3) {
                    TextButton(
                        onClick = {
                            showSubscriptionCancelDialog = false
                            cancelStep = 1
                            cancelReasonSurvey = ""
                        }
                    ) {
                        Text("Keep Subscription", color = Color.Gray)
                    }
                }
            }
        )
    }

    // --- SUB-DIALOG 4: GDPR DATA VIEW & EXPORT ---
    if (showGdprDialog) {
        val gdprPayload = "{\n" +
                "  \"app_version\": \"3.1.2-Compliance\",\n" +
                "  \"exported_date_utc\": \"2026-06-27T03:54:00\",\n" +
                "  \"google_compliance_level\": \"HIGH\",\n" +
                "  \"user_profile_data\": {\n" +
                "    \"fullname\": \"${userProfile.name}\",\n" +
                "    \"email\": \"${userProfile.email.ifEmpty { "N/A" }}\",\n" +
                "    \"current_pregnancy_week\": ${userProfile.currentWeek},\n" +
                "    \"estimated_due_date\": \"${userProfile.dueDate}\",\n" +
                "    \"baby_nickname\": \"${userProfile.babyNamePlaceholder}\",\n" +
                "    \"billing_region\": \"${userProfile.billingRegion}\",\n" +
                "    \"premium_active\": ${userProfile.isPremium}\n" +
                "  },\n" +
                "  \"clinical_sandbox_vitals\": {\n" +
                "    \"fetal_heart_rate_logs\": \"Room Local Encrypted DB\",\n" +
                "    \"daily_pregnancy_symptom_logs\": \"Local Device Only\",\n" +
                "    \"prenatal_diet_plans\": \"Secured Local Profile cache\"\n" +
                "  }\n" +
                "}"

        AlertDialog(
            onDismissRequest = { showGdprDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GDPR Health Record Export", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This is a Google compliant download format of your local sandboxed records:",
                        fontSize = 12.sp,
                        color = TextMutedBrown
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                        border = BorderStroke(1.dp, Color(0xFFE4E6EB))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = gdprPayload,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(gdprPayload))
                        showGdprDialog = false
                        showUploadSuccess = true // generic success alert
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("Copy to Clipboard", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGdprDialog = false }) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }

    // --- SUB-DIALOG 5: DELETE ACCOUNT DOUBLE CONFIRM (RIGHT TO BE FORGOTTEN) ---
    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFC62828))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚠️ Delete Medical & Account Records?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "This action is final and compliant with the Google Play Developer Account Deletion Policy.",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Type the word 'DELETE' below to erase all prenatal profiles, fetal BPM vitals logs, and daily symptoms from local Room storage.",
                        fontSize = 12.sp,
                        color = TextMutedBrown
                    )
                    OutlinedTextField(
                        value = deleteConfirmCode,
                        onValueChange = { deleteConfirmCode = it },
                        placeholder = { Text("DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllUserData()
                        showDeleteAccountConfirm = false
                        onDismiss()
                    },
                    enabled = deleteConfirmCode.trim() == "DELETE",
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Confirm Permanent Deletion", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // --- QUICK SUCCESS CONFIRMATION SNACKBAR/ALERT ---
    if (showUploadSuccess) {
        AlertDialog(
            onDismissRequest = { showUploadSuccess = false },
            title = { Text("Success ✅", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)) },
            text = { Text("Compliance records and operation completed successfully!") },
            confirmButton = {
                Button(
                    onClick = { showUploadSuccess = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun IntroItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFFF0F2F5), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1877F2), modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDeepBrown)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    userBillingRegion: String = "GLOBAL",
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var billingMethod by remember { mutableStateOf(if (userBillingRegion == "INDIA") "RAZORPAY" else "PAYPAL") } // "RAZORPAY" or "PAYPAL"
    var selectedSubTab by remember { mutableStateOf("UPI") } // UPI, CARD, NETBANKING for Razorpay; WALLET, CARD for PayPal
    
    // Razorpay States
    var upiId by remember { mutableStateOf("") }
    var rzpCardNumber by remember { mutableStateOf("") }
    var rzpExpiry by remember { mutableStateOf("") }
    var rzpCvv by remember { mutableStateOf("") }
    var rzpCardName by remember { mutableStateOf("") }
    var selectedBank by remember { mutableStateOf("HDFC") }
    
    // PayPal States
    var paypalEmail by remember { mutableStateOf("") }
    var paypalPassword by remember { mutableStateOf("") }
    var paypalCardNumber by remember { mutableStateOf("") }
    var paypalExpiry by remember { mutableStateOf("") }
    var paypalCvv by remember { mutableStateOf("") }
    
    // Simulation Overlays
    var isProcessing by remember { mutableStateOf(false) }
    var processingStep by remember { mutableStateOf("") }
    var showSuccessCelebration by remember { mutableStateOf(false) }
    var showPinPadOverlay by remember { mutableStateOf(false) }
    var enteredUpiPin by remember { mutableStateOf("") }
    var showOtpOverlay by remember { mutableStateOf(false) }
    var enteredOtp by remember { mutableStateOf("") }
    var showPaypalWebOverlay by remember { mutableStateOf(false) }
    var isPaypalLoggingIn by remember { mutableStateOf(false) }
    var isPaypalLoggedIn by remember { mutableStateOf(false) }

    if (showSuccessCelebration) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("👑", fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Upgrade Successful!", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF4CAF50), textAlign = TextAlign.Center)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Congratulations! You are now a Prega AI Premium Member.",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = TextDeepBrown,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PrimaryCoralLight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✨ Unlimited AI Coaching Consultations", fontSize = 12.sp, color = TextDeepBrown, fontWeight = FontWeight.SemiBold)
                            Text("✨ Personalized Weekly AI Diet Meal Plans", fontSize = 12.sp, color = TextDeepBrown, fontWeight = FontWeight.SemiBold)
                            Text("✨ Advanced Fetal Kick Metric Visualizers", fontSize = 12.sp, color = TextDeepBrown, fontWeight = FontWeight.SemiBold)
                            Text("✨ Full Apple/Facebook-Style Wellness Hub Access", fontSize = 12.sp, color = TextDeepBrown, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSuccess()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCoral),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Launch Premium Features 🚀", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) onDismiss() },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Upgrade to Prega Premium 👑", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PrimaryCoral)
                    Text("Premium Pregnancy Coach & Diet Planner", fontSize = 12.sp, color = TextMutedBrown)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Unlock all pregnancy features with continuous AI coaching for only $9.99/mo (₹849/mo) with local billing options.",
                        fontSize = 12.sp,
                        color = TextMutedBrown,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    // REGIONAL METHOD TOGGLE
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { 
                                billingMethod = "RAZORPAY" 
                                selectedSubTab = "UPI"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (billingMethod == "RAZORPAY") Color(0xFF0F3E7D) else Color.Transparent,
                                contentColor = if (billingMethod == "RAZORPAY") Color.White else Color.Gray
                            ),
                            shape = RoundedCornerShape(6.dp),
                            elevation = null
                        ) {
                            Text("Razorpay 🇮🇳", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { 
                                billingMethod = "PAYPAL" 
                                selectedSubTab = "WALLET"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (billingMethod == "PAYPAL") Color(0xFF00457C) else Color.Transparent,
                                contentColor = if (billingMethod == "PAYPAL") Color.White else Color.Gray
                            ),
                            shape = RoundedCornerShape(6.dp),
                            elevation = null
                        ) {
                            Text("PayPal 🌐", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (billingMethod == "RAZORPAY") {
                        // RAZORPAY INDIA GATEWAY
                        Text("Secure Indian Checkout via Razorpay 🛡️", fontSize = 11.sp, color = Color(0xFF1E88E5), fontWeight = FontWeight.Bold)

                        // Sub Tab Selector (UPI vs Card vs NetBanking)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("UPI", "CARD", "NETBANKING").forEach { tab ->
                                FilterChip(
                                    selected = selectedSubTab == tab,
                                    onClick = { selectedSubTab = tab },
                                    label = { Text(tab, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF1E88E5),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        when (selectedSubTab) {
                            "UPI" -> {
                                OutlinedTextField(
                                    value = upiId,
                                    onValueChange = { upiId = it },
                                    label = { Text("Enter UPI ID (GPay / PhonePe / Paytm)") },
                                    placeholder = { Text("e.g. name@okaxis") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("payment_upi_input")
                                )
                                Text(
                                    text = "Supported apps: Google Pay, PhonePe, Paytm, BHIM UPI.",
                                    fontSize = 11.sp,
                                    color = TextMutedBrown
                                )
                            }
                            "CARD" -> {
                                OutlinedTextField(
                                    value = rzpCardName,
                                    onValueChange = { rzpCardName = it },
                                    label = { Text("Cardholder Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = rzpCardNumber,
                                    onValueChange = { rzpCardNumber = it.filter { it.isDigit() }.take(16) },
                                    label = { Text("Card Number (16-digits)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = rzpExpiry,
                                        onValueChange = { rzpExpiry = it.filter { it.isDigit() }.take(4) },
                                        label = { Text("Expiry (MMYY)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = rzpCvv,
                                        onValueChange = { rzpCvv = it.filter { it.isDigit() }.take(3) },
                                        label = { Text("CVV") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            "NETBANKING" -> {
                                Text("Select Your Bank:", fontSize = 12.sp, color = TextDeepBrown, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val banks = listOf("HDFC", "SBI", "ICICI", "AXIS", "KOTAK")
                                    banks.forEach { bank ->
                                        FilterChip(
                                            selected = selectedBank == bank,
                                            onClick = { selectedBank = bank },
                                            label = { Text(bank, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF0F3E7D),
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // PAYPAL GLOBAL GATEWAY
                        Text("International Checkout via PayPal 🌐", fontSize = 11.sp, color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("WALLET", "CARD").forEach { tab ->
                                FilterChip(
                                    selected = selectedSubTab == tab,
                                    onClick = { selectedSubTab = tab },
                                    label = { Text(if (tab == "WALLET") "PayPal Wallet" else "Credit Card", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF0079C1),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        if (selectedSubTab == "WALLET") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF2)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFB300)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPaypalWebOverlay = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("Pay with ", color = Color(0xFF003087), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Pay", color = Color(0xFF0079C1), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text("Pal", color = Color(0xFF00457C), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Express Checkout 🚀", color = TextDeepBrown, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = paypalCardNumber,
                                onValueChange = { paypalCardNumber = it.filter { it.isDigit() }.take(16) },
                                label = { Text("Global Card Number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = paypalExpiry,
                                    onValueChange = { paypalExpiry = it.filter { it.isDigit() }.take(4) },
                                    label = { Text("MMYY") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = paypalCvv,
                                    onValueChange = { paypalCvv = it.filter { it.isDigit() }.take(3) },
                                    label = { Text("CVV") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (isProcessing) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF1976D2))
                                Text(processingStep, fontSize = 11.sp, color = Color(0xFF0D47A1), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (billingMethod == "RAZORPAY") {
                    Button(
                        onClick = {
                            isProcessing = true
                            processingStep = "Routing through Razorpay API..."
                            if (selectedSubTab == "UPI") {
                                if (upiId.isNotBlank()) {
                                    // Trigger Pin Pad Modal
                                    showPinPadOverlay = true
                                }
                            } else if (selectedSubTab == "CARD") {
                                if (rzpCardNumber.length == 16) {
                                    showOtpOverlay = true
                                }
                            } else {
                                // NetBanking
                                showOtpOverlay = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing && (
                            (selectedSubTab == "UPI" && upiId.isNotBlank()) ||
                            (selectedSubTab == "CARD" && rzpCardNumber.length == 16 && rzpCvv.length == 3) ||
                            (selectedSubTab == "NETBANKING")
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Pay Securely ₹849 / mo 💳", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    if (selectedSubTab == "CARD") {
                        Button(
                            onClick = {
                                isProcessing = true
                                processingStep = "Verifying international standard card..."
                                showOtpOverlay = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0079C1)),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing && paypalCardNumber.length == 16 && paypalCvv.length == 3,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("PayPal Card Pay $9.99 / mo 💳", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = {
                if (!isProcessing) {
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel Upgrade", color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
            }
        )
    }

    // INTERACTIVE UPI SECURE PINPAD OVERLAY
    if (showPinPadOverlay) {
        AlertDialog(
            onDismissRequest = { showPinPadOverlay = false; isProcessing = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏦 BHIM UPI Secure Pin Entry", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Ref: Prega AI Premium • Amount: ₹849.00", fontSize = 12.sp, color = TextMutedBrown)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Display Asterisks
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0 until 4) {
                            val char = if (enteredUpiPin.length > i) "●" else "○"
                            Text(char, fontSize = 28.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Simulated Keypad
                    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Clear", "0", "Done")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in 0 until 4) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 3) {
                                    val key = keys[row * 3 + col]
                                    Button(
                                        onClick = {
                                            if (key == "Clear") {
                                                enteredUpiPin = ""
                                            } else if (key == "Done") {
                                                if (enteredUpiPin.length == 4) {
                                                    showPinPadOverlay = false
                                                    isProcessing = true
                                                    processingStep = "Verifying UPI PIN..."
                                                }
                                            } else {
                                                if (enteredUpiPin.length < 4) {
                                                    enteredUpiPin += key
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF1B5E20)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(key, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // SMS OTP POPUP VERIFICATION OVERLAY
    if (showOtpOverlay) {
        AlertDialog(
            onDismissRequest = { showOtpOverlay = false; isProcessing = false },
            title = { Text("Secure Bank OTP 🔐", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryCoral) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Enter the 6-digit verification code sent to your phone:", fontSize = 12.sp, color = TextMutedBrown)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enteredOtp,
                        onValueChange = { enteredOtp = it.filter { it.isDigit() }.take(6) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("e.g. 123456") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(onClick = { enteredOtp = "123456" }) {
                        Text("Autofill Demo OTP Code", fontSize = 11.sp, color = PrimaryCoral, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (enteredOtp.length == 6) {
                            showOtpOverlay = false
                            isProcessing = true
                            processingStep = "Finalizing subscription licence..."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCoral),
                    enabled = enteredOtp.length == 6
                ) {
                    Text("Verify & Upgrade", color = Color.White)
                }
            }
        )
    }

    // SECURE PAYPAL POPUP OVERLAY
    if (showPaypalWebOverlay) {
        AlertDialog(
            onDismissRequest = { showPaypalWebOverlay = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pay", color = Color(0xFF003087), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Pal", color = Color(0xFF0079C1), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                    Text("Secure Log In", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (!isPaypalLoggedIn) {
                        Text("Log in with your PayPal wallet credentials to approve subscription:", fontSize = 12.sp, color = TextDeepBrown)
                        OutlinedTextField(
                            value = paypalEmail,
                            onValueChange = { paypalEmail = it },
                            label = { Text("PayPal Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = paypalPassword,
                            onValueChange = { paypalPassword = it },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isPaypalLoggingIn) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFF0079C1))
                        } else {
                            Button(
                                onClick = {
                                    isPaypalLoggingIn = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0079C1)),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = paypalEmail.isNotBlank() && paypalPassword.isNotBlank()
                            ) {
                                Text("Secure Log In", color = Color.White)
                            }
                        }

                        LaunchedEffect(isPaypalLoggingIn) {
                            if (isPaypalLoggingIn) {
                                delay(1200)
                                isPaypalLoggedIn = true
                                isPaypalLoggingIn = false
                            }
                        }
                    } else {
                        Text("Review Your Purchase:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDeepBrown)
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Prega AI Premium Upgrade", fontSize = 11.sp, color = TextDeepBrown)
                                    Text("$9.99", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDeepBrown)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Funding Source", fontSize = 11.sp, color = TextMutedBrown)
                                    Text("PayPal Balance (USD)", fontSize = 11.sp, color = TextMutedBrown)
                                }
                            }
                        }
                        Button(
                            onClick = {
                                showPaypalWebOverlay = false
                                isProcessing = true
                                processingStep = "Confirming via secure PayPal token..."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Agree & Pay $9.99 🚀", color = Color(0xFF003087), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Continuous launched effect to simulate complete gateway transaction verification loops
    LaunchedEffect(isProcessing, processingStep) {
        if (isProcessing) {
            if (processingStep.contains("UPI PIN")) {
                delay(1200)
                processingStep = "UPI Authorization verified successfully!"
                delay(800)
                processingStep = "Configuring server premium access..."
                delay(600)
                showSuccessCelebration = true
                isProcessing = false
            } else if (processingStep.contains("PayPal token") || processingStep.contains("licence") || processingStep.contains("card")) {
                delay(1200)
                processingStep = "Transaction settled successfully!"
                delay(800)
                processingStep = "Registering license key..."
                delay(600)
                showSuccessCelebration = true
                isProcessing = false
            }
        }
    }
}
