package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiClient
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class WeekInfo(
    val week: Int,
    val sizeName: String,
    val lengthCm: Double,
    val weightGrams: Double,
    val description: String,
    val iconEmoji: String,
    val trimester: Int
)

class PregnancyViewModel(private val repository: PregnancyRepository) : ViewModel() {

    // --- State Observables ---
    val profile: StateFlow<UserProfileEntity?> = repository.getUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _todayDate = MutableStateFlow(getTodayDateString())
    val todayDate: StateFlow<String> = _todayDate.asStateFlow()

    val todayLog: StateFlow<DailyLogEntity?> = _todayDate
        .flatMapLatest { date -> repository.getDailyLog(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val kickLogs: StateFlow<List<KickLogEntity>> = repository.getAllKickLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- AI Coach Chat State ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "Hello! I am your Prega AI Coach. Ask me anything about your trimester, pregnancy nutrition, symptoms, or prenatal exercises. (Remember: I'm here for support, not medical diagnosis!)",
                isUser = false
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    // --- Active Kick Counter Session State ---
    private val _isCountingKicks = MutableStateFlow(false)
    val isCountingKicks: StateFlow<Boolean> = _isCountingKicks.asStateFlow()

    private val _currentSessionKicks = MutableStateFlow(0)
    val currentSessionKicks: StateFlow<Int> = _currentSessionKicks.asStateFlow()

    private val _currentSessionSeconds = MutableStateFlow(0)
    val currentSessionSeconds: StateFlow<Int> = _currentSessionSeconds.asStateFlow()

    private var kickTimerJob: Job? = null

    // --- Daily Date Checker ---
    init {
        viewModelScope.launch {
            while (true) {
                delay(60000) // Check every minute
                val current = getTodayDateString()
                if (_todayDate.value != current) {
                    _todayDate.value = current
                }
            }
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // --- Profile Operations ---
    fun updateProfile(
        name: String,
        week: Int,
        babyName: String,
        lmpDate: String = "",
        testDate: String = "",
        eddDate: String = "",
        billingRegion: String = "GLOBAL"
    ) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val weeksRemaining = 40 - week
            calendar.add(Calendar.WEEK_OF_YEAR, weeksRemaining)
            val dueFormat = eddDate.ifEmpty {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(calendar.time)
            }

            val current = profile.value
            val newProfile = UserProfileEntity(
                id = 1,
                name = name,
                currentWeek = week,
                babyNamePlaceholder = babyName.ifEmpty { "Little One" },
                dueDate = dueFormat,
                email = current?.email ?: "",
                photoUrl = current?.photoUrl ?: "",
                isGoogleSignedIn = current?.isGoogleSignedIn ?: false,
                isPremium = current?.isPremium ?: false,
                premiumPurchaseDate = current?.premiumPurchaseDate ?: "",
                freeQuestionsRemaining = current?.freeQuestionsRemaining ?: 5,
                lmpDate = lmpDate,
                testDate = testDate,
                eddDate = dueFormat,
                billingRegion = billingRegion
            )
            repository.saveUserProfile(newProfile)
        }
    }

    fun signInWithGoogle(name: String, email: String, photoUrl: String) {
        viewModelScope.launch {
            val current = profile.value
            val currentWeek = current?.currentWeek ?: 12
            val babyName = current?.babyNamePlaceholder ?: "Little One"
            val due = current?.dueDate ?: ""
            val isPremium = current?.isPremium ?: false
            val freeQuestions = current?.freeQuestionsRemaining ?: 5
            val lmp = current?.lmpDate ?: ""
            val test = current?.testDate ?: ""
            val edd = current?.eddDate ?: ""
            val billing = current?.billingRegion ?: "GLOBAL"

            val newProfile = UserProfileEntity(
                id = 1,
                name = name,
                currentWeek = currentWeek,
                babyNamePlaceholder = babyName,
                dueDate = due,
                email = email,
                photoUrl = photoUrl,
                isGoogleSignedIn = true,
                isPremium = isPremium,
                freeQuestionsRemaining = freeQuestions,
                lmpDate = lmp,
                testDate = test,
                eddDate = edd,
                billingRegion = billing
            )
            repository.saveUserProfile(newProfile)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.deleteUserProfile()
            // Reset chat messages
            _chatMessages.value = listOf(
                ChatMessage(
                    text = "Hello! I am your Prega AI Coach. Ask me anything about your trimester, pregnancy nutrition, symptoms, or prenatal exercises. (Remember: I'm here for support, not medical diagnosis!)",
                    isUser = false
                )
            )
        }
    }

    fun deleteAllUserData() {
        viewModelScope.launch {
            repository.deleteUserProfile()
            repository.deleteAllDailyLogs()
            repository.deleteAllKickLogs()
            // Reset chat messages
            _chatMessages.value = listOf(
                ChatMessage(
                    text = "Hello! I am your Prega AI Coach. Ask me anything about your trimester, pregnancy nutrition, symptoms, or prenatal exercises. (Remember: I'm here for support, not medical diagnosis!)",
                    isUser = false
                )
            )
        }
    }

    fun updateProfilePhoto(photoUrl: String) {
        viewModelScope.launch {
            val current = profile.value
            if (current != null) {
                val updated = current.copy(photoUrl = photoUrl)
                repository.saveUserProfile(updated)
            }
        }
    }

    fun cancelPremium() {
        viewModelScope.launch {
            val current = profile.value
            if (current != null) {
                val updated = current.copy(
                    isPremium = false,
                    premiumPurchaseDate = "",
                    freeQuestionsRemaining = 5
                )
                repository.saveUserProfile(updated)
            }
        }
    }

    fun upgradeToPremium() {
        viewModelScope.launch {
            val current = profile.value
            if (current != null) {
                val purchaseDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
                val updated = current.copy(
                    isPremium = true,
                    premiumPurchaseDate = purchaseDate,
                    freeQuestionsRemaining = 99999
                )
                repository.saveUserProfile(updated)
            }
        }
    }

    // --- Daily Logs Operations ---
    private fun getOrCreateTodayLog(): DailyLogEntity {
        return todayLog.value ?: DailyLogEntity(date = _todayDate.value)
    }

    fun incrementWater() {
        viewModelScope.launch {
            val log = getOrCreateTodayLog()
            repository.saveDailyLog(log.copy(waterGlasses = log.waterGlasses + 1))
        }
    }

    fun decrementWater() {
        viewModelScope.launch {
            val log = getOrCreateTodayLog()
            if (log.waterGlasses > 0) {
                repository.saveDailyLog(log.copy(waterGlasses = log.waterGlasses - 1))
            }
        }
    }

    fun toggleVitamins() {
        viewModelScope.launch {
            val log = getOrCreateTodayLog()
            repository.saveDailyLog(log.copy(tookVitamins = !log.tookVitamins))
        }
    }

    fun updateSleep(hours: Float) {
        viewModelScope.launch {
            val log = getOrCreateTodayLog()
            repository.saveDailyLog(log.copy(sleptHours = hours))
        }
    }

    fun updateWeight(weight: Float) {
        viewModelScope.launch {
            val log = getOrCreateTodayLog()
            repository.saveDailyLog(log.copy(weightKg = weight))
        }
    }

    fun toggleSymptom(symptom: String) {
        viewModelScope.launch {
            val log = getOrCreateTodayLog()
            val currentList = if (log.symptoms.isEmpty()) emptyList() else log.symptoms.split(",")
            val newList = if (currentList.contains(symptom)) {
                currentList - symptom
            } else {
                currentList + symptom
            }
            repository.saveDailyLog(log.copy(symptoms = newList.joinToString(",")))
        }
    }

    // --- Kick Counter Operations ---
    fun startKickSession() {
        if (_isCountingKicks.value) return
        _isCountingKicks.value = true
        _currentSessionKicks.value = 0
        _currentSessionSeconds.value = 0

        kickTimerJob = viewModelScope.launch {
            while (_isCountingKicks.value) {
                delay(1000)
                _currentSessionSeconds.value += 1
            }
        }
    }

    fun logKick() {
        if (!_isCountingKicks.value) return
        _currentSessionKicks.value += 1
    }

    fun stopAndSaveKickSession() {
        if (!_isCountingKicks.value) return
        val count = _currentSessionKicks.value
        val seconds = _currentSessionSeconds.value
        _isCountingKicks.value = false
        kickTimerJob?.cancel()

        if (count > 0) {
            viewModelScope.launch {
                val kickLog = KickLogEntity(
                    date = _todayDate.value,
                    timestamp = System.currentTimeMillis(),
                    count = count,
                    durationSeconds = seconds
                )
                repository.saveKickLog(kickLog)
            }
        }
    }

    fun cancelKickSession() {
        _isCountingKicks.value = false
        kickTimerJob?.cancel()
        _currentSessionKicks.value = 0
        _currentSessionSeconds.value = 0
    }

    fun deleteKickLog(id: Int) {
        viewModelScope.launch {
            repository.deleteKickLog(id)
        }
    }

    // --- AI Coach Chat Operations ---
    fun askCoach(question: String) {
        if (question.isBlank()) return
        val userMsg = ChatMessage(text = question, isUser = true)
        _chatMessages.value = _chatMessages.value + userMsg
        _chatLoading.value = true

        val systemInstruction = """
            You are "Prega AI Coach", a supportive, warm, and comforting pregnancy wellness companion.
            Provide information about pregnancy trimesters, symptom remedies (nausea, sleep issues), light prenatal exercises, and nutrition.
            
            RULES:
            1. Emphasize that you are an AI assistant and NOT a doctor.
            2. For severe symptoms (like sharp pain, severe bleeding, continuous headaches), strongly urge the user to contact their OB-GYN immediately.
            3. Answer warmly, empathetically, and clearly, with short, easy-to-read formatting (bullet points, spaced paragraphs).
        """.trimIndent()

        viewModelScope.launch {
            val currentProfile = profile.value
            if (currentProfile != null && !currentProfile.isPremium) {
                if (currentProfile.freeQuestionsRemaining <= 0) {
                    val upgradeMsg = ChatMessage(
                        text = "Prega AI Coach: You have reached the limit of 5 free AI questions for your trial! Please tap the 'Upgrade to Premium' banner above or in Settings to unlock unlimited questions, personalized weekly meal plans, and kick metrics reports. 🤰💖✨",
                        isUser = false
                    )
                    _chatMessages.value = _chatMessages.value + upgradeMsg
                    _chatLoading.value = false
                    return@launch
                }
                // Decrement free questions remaining
                val updatedProfile = currentProfile.copy(
                    freeQuestionsRemaining = currentProfile.freeQuestionsRemaining - 1
                )
                repository.saveUserProfile(updatedProfile)
            }

            val response = GeminiClient.askGemini(question, systemInstruction)
            val coachMsg = ChatMessage(text = response, isUser = false)
            _chatMessages.value = _chatMessages.value + coachMsg
            _chatLoading.value = false
        }
    }

    // --- AI Weekly Meal Plan ---
    private val _weeklyMealPlan = MutableStateFlow<String?>(null)
    val weeklyMealPlan: StateFlow<String?> = _weeklyMealPlan.asStateFlow()

    private val _mealPlanLoading = MutableStateFlow(false)
    val mealPlanLoading: StateFlow<Boolean> = _mealPlanLoading.asStateFlow()

    fun generateWeeklyMealPlan(currentWeek: Int, babyNickname: String) {
        _mealPlanLoading.value = true
        val systemInstruction = """
            You are a prenatal nutritionist and dietitian expert.
            Generate a personalized, medically-vetted, day-by-day weekly meal plan for a pregnant mother in Week $currentWeek of her pregnancy (baby's nickname: $babyNickname).
            Include Breakfast, Lunch, Snack, and Dinner for each day (Monday through Sunday).
            Incorporate key pregnancy superfoods like eggs, yogurt, salmon, and spinach.
            Highlight the specific benefits of nutrients (like Folate, Choline, Iron, Calcium) for this week of baby development.
            Format beautifully with clear headings, daily bullet points, and an encouraging closing message.
        """.trimIndent()

        viewModelScope.launch {
            val response = GeminiClient.askGemini(
                prompt = "Please generate my personalized week $currentWeek prenatal weekly meal plan now. Include daily meals, portion guides, and essential nutrient highlights.",
                systemPrompt = systemInstruction
            )
            _weeklyMealPlan.value = response
            _mealPlanLoading.value = false
        }
    }

    // --- Week Milestone Presets (1 to 40) ---
    fun getWeekInfo(weekNum: Int): WeekInfo {
        val clampWeek = weekNum.coerceIn(1, 40)
        val trimester = when {
            clampWeek <= 13 -> 1
            clampWeek <= 27 -> 2
            else -> 3
        }
        return when (clampWeek) {
            in 1..3 -> WeekInfo(
                week = clampWeek,
                sizeName = "Tiny Atom",
                lengthCm = 0.1,
                weightGrams = 0.0,
                description = "Fertilization occurs. The fertilized egg is journeying to your uterus to find its cozy home for the next nine months.",
                iconEmoji = "✨",
                trimester = trimester
            )
            4 -> WeekInfo(
                week = 4,
                sizeName = "Poppy Seed",
                lengthCm = 0.15,
                weightGrams = 0.01,
                description = "Implantation takes place. The tiny cell bundle is now securely attached to the uterine wall and beginning to grow.",
                iconEmoji = "🌱",
                trimester = trimester
            )
            5 -> WeekInfo(
                week = 5,
                sizeName = "Apple Seed",
                lengthCm = 0.25,
                weightGrams = 0.05,
                description = "The baby's neural tube is forming, laying the foundation for the brain, spinal cord, and central nervous system.",
                iconEmoji = "🍎",
                trimester = trimester
            )
            6 -> WeekInfo(
                week = 6,
                sizeName = "Sweet Pea",
                lengthCm = 0.5,
                weightGrams = 0.1,
                description = "The baby's heart begins to beat at twice the speed of yours! Small buds are forming that will become arms and legs.",
                iconEmoji = "🟢",
                trimester = trimester
            )
            7 -> WeekInfo(
                week = 7,
                sizeName = "Blueberry",
                lengthCm = 1.2,
                weightGrams = 0.5,
                description = "Your baby's face features are starting to sketch out. The brain is developing thousands of tiny new brain cells every minute.",
                iconEmoji = "🫐",
                trimester = trimester
            )
            8 -> WeekInfo(
                week = 8,
                sizeName = "Raspberry",
                lengthCm = 1.6,
                weightGrams = 1.0,
                description = "Fingers and toes are starting to web out! The tail-like structure is almost gone, and your baby is moving around.",
                iconEmoji = "🍓",
                trimester = trimester
            )
            9 -> WeekInfo(
                week = 9,
                sizeName = "Green Grape",
                lengthCm = 2.3,
                weightGrams = 2.0,
                description = "Muscle movements are beginning as skeletal joints start to move. The heart is fully split into four chambers now.",
                iconEmoji = "🍇",
                trimester = trimester
            )
            10 -> WeekInfo(
                week = 10,
                sizeName = "Kumquat",
                lengthCm = 3.1,
                weightGrams = 4.0,
                description = "The critical embryonic phase is complete! Your baby is now officially a fetus, with all vital organs formed and active.",
                iconEmoji = "🍊",
                trimester = trimester
            )
            11 -> WeekInfo(
                week = 11,
                sizeName = "Brussels Sprout",
                lengthCm = 4.1,
                weightGrams = 7.0,
                description = "Fingernails, hair follicles, and tooth buds are starting to form. Your baby is busy kicking and stretching inside.",
                iconEmoji = "🥬",
                trimester = trimester
            )
            12 -> WeekInfo(
                week = 12,
                sizeName = "Lime",
                lengthCm = 5.4,
                weightGrams = 14.0,
                description = "Reflexes are developing! If you poke your tummy, the baby might squirm. Vocal cords are also starting to develop.",
                iconEmoji = "💚",
                trimester = trimester
            )
            13 -> WeekInfo(
                week = 13,
                sizeName = "Lemon",
                lengthCm = 7.4,
                weightGrams = 23.0,
                description = "The final week of the first trimester! Fingerprints have formed, and your baby's kidneys are generating urine.",
                iconEmoji = "🍋",
                trimester = trimester
            )
            14 -> WeekInfo(
                week = 14,
                sizeName = "Nectarine",
                lengthCm = 8.7,
                weightGrams = 43.0,
                description = "Welcome to the second trimester! The golden era. The baby can make facial expressions and swallow fluid.",
                iconEmoji = "🍑",
                trimester = trimester
            )
            15 -> WeekInfo(
                week = 15,
                sizeName = "Apple",
                lengthCm = 10.1,
                weightGrams = 70.0,
                description = "Your baby's skeleton is continuing to harden. Fine, downy hair called lanugo is covering their body to keep them cozy.",
                iconEmoji = "🍏",
                trimester = trimester
            )
            16 -> WeekInfo(
                week = 16,
                sizeName = "Avocado",
                lengthCm = 11.6,
                weightGrams = 100.0,
                description = "Baby's eyes can track slowly behind closed lids. The circulatory system is functioning fully.",
                iconEmoji = "🥑",
                trimester = trimester
            )
            17 -> WeekInfo(
                week = 17,
                sizeName = "Pear",
                lengthCm = 13.0,
                weightGrams = 140.0,
                description = "A protective coating called myelin is wrapping around the nerves. Fat deposits are forming under baby's skin.",
                iconEmoji = "🍐",
                trimester = trimester
            )
            18 -> WeekInfo(
                week = 18,
                sizeName = "Sweet Potato",
                lengthCm = 14.2,
                weightGrams = 190.0,
                description = "Your baby can hear your voice and heartbeat now! You might feel tiny flutters, also known as quickening.",
                iconEmoji = "🍠",
                trimester = trimester
            )
            19 -> WeekInfo(
                week = 19,
                sizeName = "Heirloom Tomato",
                lengthCm = 15.3,
                weightGrams = 240.0,
                description = "A protective greasy coating called vernix caseosa covers baby's skin to shield it from continuous amniotic fluid exposure.",
                iconEmoji = "🍅",
                trimester = trimester
            )
            20 -> WeekInfo(
                week = 20,
                sizeName = "Banana",
                lengthCm = 25.6,
                weightGrams = 300.0,
                description = "Halfway mark! Your baby's swallowing mechanism is fully functional, and they are sleeping and waking in regular cycles.",
                iconEmoji = "🍌",
                trimester = trimester
            )
            21 -> WeekInfo(
                week = 21,
                sizeName = "Carrot",
                lengthCm = 26.7,
                weightGrams = 360.0,
                description = "The digestive system is starting to process sugars from the swallowed fluid. Baby has taste buds now!",
                iconEmoji = "🥕",
                trimester = trimester
            )
            22 -> WeekInfo(
                week = 22,
                sizeName = "Papaya",
                lengthCm = 27.8,
                weightGrams = 430.0,
                description = "The baby's brain is developing rapidly. Eyes are fully formed, though the iris lacks pigmentation.",
                iconEmoji = "🧡",
                trimester = trimester
            )
            23 -> WeekInfo(
                week = 23,
                sizeName = "Grapefruit",
                lengthCm = 28.9,
                weightGrams = 500.0,
                description = "The lungs are starting to produce surfactant to keep air sacs open, gearing up for breathing after birth.",
                iconEmoji = "🍊",
                trimester = trimester
            )
            24 -> WeekInfo(
                week = 24,
                sizeName = "Cantaloupe",
                lengthCm = 30.0,
                weightGrams = 600.0,
                description = "Your baby's skin is translucent but starting to fill in. Inner ear is fully developed, giving them a sense of balance.",
                iconEmoji = "🍈",
                trimester = trimester
            )
            25 -> WeekInfo(
                week = 25,
                sizeName = "Cauliflower",
                lengthCm = 34.6,
                weightGrams = 660.0,
                description = "Capillaries are forming under the skin, giving it a healthy pink glow. Baby can open their nostrils now.",
                iconEmoji = "🥦",
                trimester = trimester
            )
            26 -> WeekInfo(
                week = 26,
                sizeName = "Red Cabbage",
                lengthCm = 35.6,
                weightGrams = 760.0,
                description = "Baby's eyes can open and blink! Brain waves show response to surrounding sounds outside your belly.",
                iconEmoji = "🥬",
                trimester = trimester
            )
            27 -> WeekInfo(
                week = 27,
                sizeName = "Head of Lettuce",
                lengthCm = 36.6,
                weightGrams = 875.0,
                description = "Final week of Trimester 2! Your baby has regular sleep patterns and may experience occasional hiccups.",
                iconEmoji = "🥬",
                trimester = trimester
            )
            28 -> WeekInfo(
                week = 28,
                sizeName = "Eggplant",
                lengthCm = 37.6,
                weightGrams = 1000.0,
                description = "Welcome to Trimester 3! Baby can now blink and has eyelashes. Lung development continues at full speed.",
                iconEmoji = "🍆",
                trimester = trimester
            )
            29 -> WeekInfo(
                week = 29,
                sizeName = "Acorn Squash",
                lengthCm = 38.6,
                weightGrams = 1150.0,
                description = "Muscle fibers are hardening, and your baby is kicking harder. Make sure to keep tracking those kicks!",
                iconEmoji = "🤎",
                trimester = trimester
            )
            30 -> WeekInfo(
                week = 30,
                sizeName = "Cucumber",
                lengthCm = 39.9,
                weightGrams = 1300.0,
                description = "A great deal of brain tissue is forming. Your baby is shedding the fine lanugo hair as they build up body fat.",
                iconEmoji = "🥒",
                trimester = trimester
            )
            31 -> WeekInfo(
                week = 31,
                sizeName = "Pineapple",
                lengthCm = 41.1,
                weightGrams = 1500.0,
                description = "Your baby can turn their head from side to side. The bone structure is fully formed but remaining flexible.",
                iconEmoji = "🍍",
                trimester = trimester
            )
            32 -> WeekInfo(
                week = 32,
                sizeName = "Squash",
                lengthCm = 42.4,
                weightGrams = 1700.0,
                description = "Your baby is occupying most of the space inside. Nails are now fully covering the tips of their toes and fingers.",
                iconEmoji = "🎃",
                trimester = trimester
            )
            33 -> WeekInfo(
                week = 33,
                sizeName = "Celery",
                lengthCm = 43.7,
                weightGrams = 1900.0,
                description = "Your immune system is passing precious antibodies to your baby to establish their protection after birth.",
                iconEmoji = "🥬",
                trimester = trimester
            )
            34 -> WeekInfo(
                week = 34,
                sizeName = "Butternut Squash",
                lengthCm = 45.0,
                weightGrams = 2100.0,
                description = "The central nervous system and lungs are reaching mature stages. Your baby's skin is smooth and plump.",
                iconEmoji = "🌾",
                trimester = trimester
            )
            35 -> WeekInfo(
                week = 35,
                sizeName = "Honeydew Melon",
                lengthCm = 46.2,
                weightGrams = 2400.0,
                description = "Your baby is getting ready for delivery, usually settling into a head-down position now.",
                iconEmoji = "🍈",
                trimester = trimester
            )
            36 -> WeekInfo(
                week = 36,
                sizeName = "Papaya",
                lengthCm = 47.4,
                weightGrams = 2600.0,
                description = "Baby is packing on about 30 grams of fat every single day! All vital organ systems are ready to transition.",
                iconEmoji = "💛",
                trimester = trimester
            )
            37 -> WeekInfo(
                week = 37,
                sizeName = "Winter Melon",
                lengthCm = 48.6,
                weightGrams = 2900.0,
                description = "Baby is considered early term now! The digestive and respiratory systems are ready to face the world.",
                iconEmoji = "🍉",
                trimester = trimester
            )
            38 -> WeekInfo(
                week = 38,
                sizeName = "Rhubarb",
                lengthCm = 49.8,
                weightGrams = 3100.0,
                description = "The brain is establishing connections to control breathing, swallowing, and sleep. Almost there!",
                iconEmoji = "🌿",
                trimester = trimester
            )
            39 -> WeekInfo(
                week = 39,
                sizeName = "Watermelon",
                lengthCm = 50.7,
                weightGrams = 3300.0,
                description = "Your baby is considered full term! They have finished their core development and are patiently waiting.",
                iconEmoji = "🍉",
                trimester = trimester
            )
            else -> WeekInfo(
                week = 40,
                sizeName = "Pumpkin",
                lengthCm = 51.2,
                weightGrams = 3500.0,
                description = "Due date week! All systems are 100% prepared to meet you. Welcome to parenthood, you are ready!",
                iconEmoji = "🎃",
                trimester = trimester
            )
        }
    }
}

class PregnancyViewModelFactory(private val repository: PregnancyRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PregnancyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PregnancyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
