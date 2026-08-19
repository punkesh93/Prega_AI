@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AiMessage
import com.example.ai.AiResult
import com.example.ai.OpenRouterClient
import com.example.ai.PregaModel
import com.example.ai.PregaPrompts
import com.example.ai.stripMarkdown
import com.example.data.*
import com.example.domain.GamificationEngine
import com.example.stats.AppStats
import com.example.stats.StatEvent
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
    val timestamp: Long = System.currentTimeMillis(),
    /** The canned opener. Excluded from AI history so it can't skew replies. */
    val isGreeting: Boolean = false,
)

/**
 * One-shot UI events emitted after a rewarded action. Kept separate from state
 * so a celebration fires exactly once and is never replayed on rotation.
 */
sealed interface RewardEvent {
    data class Points(val amount: Int, val reason: String) : RewardEvent
    data class BadgeEarned(val badge: BadgeDef) : RewardEvent
    data class StreakExtended(val days: Int) : RewardEvent
    /** A grace day was spent to protect her streak. Framed as care, not a loss. */
    data class StreakProtected(val remaining: Int) : RewardEvent
    data class LevelUp(val level: Int, val title: String) : RewardEvent
}

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

    private val gamification = GamificationEngine(repository)

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

    // --- Gamification State ---
    /** One-shot events the UI consumes to fire confetti, toasts, badge reveals. */
    private val _rewards = MutableSharedFlow<RewardEvent>(extraBufferCapacity = 8)
    val rewards: SharedFlow<RewardEvent> = _rewards.asSharedFlow()

    val progress: StateFlow<ProgressEntity> = repository.getProgress()
        .map { it ?: ProgressEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressEntity())

    val badges: StateFlow<List<BadgeEntity>> = repository.getBadges()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayQuests: StateFlow<List<QuestEntity>> = _todayDate
        .flatMapLatest { date -> repository.getQuestsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val moodToday: StateFlow<MoodEntity?> = _todayDate
        .flatMapLatest { date -> repository.getMood(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val appointments: StateFlow<List<AppointmentEntity>> = repository.getAppointments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nextAppointment: StateFlow<AppointmentEntity?> = _todayDate
        .flatMapLatest { date -> repository.getNextAppointment(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- AI Coach Chat State ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = GREETING,
                isUser = false,
                isGreeting = true,
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

    /**
     * Attaches a Google account to the profile.
     *
     * Uses `copy()` against whatever profile already exists rather than
     * reconstructing one field-by-field — the previous version rebuilt the
     * whole entity from a fixed list of fields and silently dropped every
     * one it forgot to list (onboarding status, notification settings,
     * dietary preferences...). If this is ever called on a fully set-up
     * profile — someone linking Google from Settings after already using the
     * app for weeks — the old version would have quietly reset all of that.
     *
     * Called with no existing profile (fresh install, signing in during
     * onboarding) it creates one via all-default `UserProfileEntity()`,
     * which is exactly the blank slate onboarding expects to fill in next.
     */
    fun signInWithGoogle(name: String, email: String, photoUrl: String) {
        viewModelScope.launch {
            val current = profile.value ?: UserProfileEntity(id = 1, name = "")
            repository.saveUserProfile(
                current.copy(
                    // Only fill the name if she hadn't already set one —
                    // don't clobber a name she chose herself with whatever
                    // her Google account happens to be labelled.
                    name = current.name.ifBlank { name },
                    email = email,
                    photoUrl = photoUrl,
                    isGoogleSignedIn = true,
                )
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.deleteUserProfile()
            // Reset chat messages
            _chatMessages.value = listOf(
                ChatMessage(
                    text = GREETING,
                    isUser = false,
                    isGreeting = true,
                )
            )
        }
    }

    fun deleteAllUserData() {
        viewModelScope.launch {
            repository.deleteEverything()
            // Reset chat messages
            _chatMessages.value = listOf(
                ChatMessage(
                    text = GREETING,
                    isUser = false,
                    isGreeting = true,
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
            award(GamificationEngine.Action.LogWater, profile.value?.currentWeek ?: 12, "Water logged")
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
            val nowTaken = !log.tookVitamins
            repository.saveDailyLog(log.copy(tookVitamins = nowTaken))
            // Only reward turning it on — un-ticking is a correction, not a failure.
            if (nowTaken) {
                award(GamificationEngine.Action.LogVitamins, profile.value?.currentWeek ?: 12, "Vitamins logged")
            }
        }
    }

    fun updateSleep(hours: Float) {
        viewModelScope.launch {
            val log = getOrCreateTodayLog()
            repository.saveDailyLog(log.copy(sleptHours = hours))
            award(GamificationEngine.Action.LogSleep, profile.value?.currentWeek ?: 12, "Sleep logged")
        }
    }

    /**
     * Weight is recorded but never gamified — no points, no streak, no badge.
     * Attaching rewards to a number on a scale during pregnancy is exactly the
     * kind of pressure this app should not create.
     */
    fun updateWeight(weight: Float) {
        viewModelScope.launch {
            val log = getOrCreateTodayLog()
            repository.saveDailyLog(log.copy(weightKg = weight))
            repository.saveWeight(WeightEntity(date = _todayDate.value, weightKg = weight))
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
                repository.saveKickLog(
                    KickLogEntity(
                        date = _todayDate.value,
                        timestamp = System.currentTimeMillis(),
                        count = count,
                        durationSeconds = seconds,
                    )
                )
                award(GamificationEngine.Action.KickSession, profile.value?.currentWeek ?: 12, "Kick session saved")
                AppStats.log(StatEvent.KickSessionCompleted)
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
        if (_chatLoading.value) return  // guard against double-submit

        _chatMessages.value = _chatMessages.value + ChatMessage(text = question, isUser = true)
        _chatLoading.value = true

        viewModelScope.launch {
            val currentProfile = profile.value
            val week = currentProfile?.currentWeek ?: 12
            val trimester = trimesterFor(week)

            // Chat is unlimited for everyone: the coach runs on a free-tier
            // model, so a per-question cap protects nothing. The old 5/day
            // gate (and its DB fields) stays dormant in the schema in case a
            // paid model ever returns. Premium now differentiates on meal
            // plans, kick history, and future voice features instead.

            // Real conversation history, so follow-up questions actually work.
            val history = _chatMessages.value
                .dropLast(1)
                .filterNot { it.isGreeting }
                .map { AiMessage(if (it.isUser) "user" else "assistant", it.text) }

            val result = OpenRouterClient.complete(
                systemPrompt = PregaPrompts.coach(
                    week = week,
                    trimester = trimester,
                    babyName = currentProfile?.babyNamePlaceholder.orEmpty(),
                    name = currentProfile?.name.orEmpty(),
                    chatLanguage = currentProfile?.chatLanguage ?: "English",
                ),
                userPrompt = question,
                history = history,
                model = PregaModel.Conversational,
                temperature = 0.7,
            )

            val reply = when (result) {
                is AiResult.Success -> result.text.stripMarkdown()
                is AiResult.NotConfigured -> OFFLINE_COACH_REPLY
                is AiResult.Failure -> "I couldn't reach my notes just now — ${result.reason}" +
                    if (result.retryable) " Try asking again in a moment." else ""
            }

            _chatMessages.value = _chatMessages.value + ChatMessage(text = reply, isUser = false)
            _chatLoading.value = false

            if (result is AiResult.Success) {
                award(GamificationEngine.Action.AskCoach, week, "Question asked")
                AppStats.log(StatEvent.CoachQuestionAsked)
            }
        }
    }

    /** Shown when no API key is configured, so the feature degrades gracefully. */
    private val OFFLINE_COACH_REPLY =
        "I can't reach my AI service right now — no API key is configured. " +
        "Everything else in the app works offline: your logs, kick counter, " +
        "quests and journey are all saved on your device.\n\n" +
        "For anything that worries you, your midwife or OB-GYN is the right call."

    // ─── Chat language + dynamic suggested questions ───────────────────────

    fun setChatLanguage(lang: String) {
        viewModelScope.launch {
            val current = profile.value ?: return@launch
            if (current.chatLanguage == lang) return@launch
            repository.saveUserProfile(current.copy(chatLanguage = lang))
            refreshSuggestedQuestions(force = true)
        }
    }

    private val _suggestedQuestions = MutableStateFlow<List<String>>(emptyList())
    /** AI-written, week+language-specific chips for the empty chat screen.
     *  Empty list = generation unavailable; the UI falls back to its static
     *  English set. */
    val suggestedQuestions: StateFlow<List<String>> = _suggestedQuestions.asStateFlow()

    private var suggestionsKey: String? = null

    /**
     * Regenerates the chips when week or language changes (or on demand).
     * Every failure path lands on emptyList — never stale wrong-language
     * chips, never a crash; the static English fallback covers the gap.
     */
    fun refreshSuggestedQuestions(force: Boolean = false) {
        viewModelScope.launch {
            val p = profile.value ?: return@launch
            val key = "${p.currentWeek}|${p.chatLanguage}"
            if (!force && key == suggestionsKey) return@launch
            suggestionsKey = key

            val raw = OpenRouterClient.completeOrNull(
                systemPrompt = PregaPrompts.suggestedQuestions(
                    week = p.currentWeek,
                    trimester = trimesterFor(p.currentWeek),
                    babyName = p.babyNamePlaceholder,
                    name = p.name,
                    chatLanguage = p.chatLanguage,
                ),
                userPrompt = "Give me this week's 4 questions.",
                model = PregaModel.Quick,
                temperature = 0.7,
                maxTokens = 140,
            )?.stripMarkdown()

            _suggestedQuestions.value = raw
                ?.lines()
                ?.map { it.trim().trimStart('-', '•', '*', ' ').trim() }
                // Strip any "1." / "2)" numbering the model sneaks in.
                ?.map { it.replace(Regex("""^\d+[.)]\s*"""), "") }
                ?.filter { it.length in 8..90 }
                ?.distinct()
                ?.take(4)
                ?.takeIf { it.size >= 2 }
                ?: emptyList()
        }
    }

    // --- AI Weekly Meal Plan ---
    private val _weeklyMealPlan = MutableStateFlow<String?>(null)
    val weeklyMealPlan: StateFlow<String?> = _weeklyMealPlan.asStateFlow()

    private val _mealPlanLoading = MutableStateFlow(false)
    val mealPlanLoading: StateFlow<Boolean> = _mealPlanLoading.asStateFlow()

    fun generateWeeklyMealPlan(currentWeek: Int, babyNickname: String) {
        if (_mealPlanLoading.value) return
        _mealPlanLoading.value = true

        viewModelScope.launch {
            val p = profile.value
            // Feed today's logged symptoms in, so the plan works around how she
            // actually feels rather than an idealised week.
            val symptoms = todayLog.value?.symptoms?.replace(",", ", ").orEmpty()

            val result = OpenRouterClient.complete(
                systemPrompt = PregaPrompts.mealPlan(
                    week = currentWeek,
                    trimester = trimesterFor(currentWeek),
                    babyName = babyNickname,
                    name = p?.name.orEmpty(),
                    preferences = p?.dietaryPreferences.orEmpty(),
                    symptoms = symptoms,
                ),
                userPrompt = "Build my week $currentWeek meal plan.",
                model = PregaModel.Conversational,
                temperature = 0.8,
                maxTokens = 2000,
            )

            _weeklyMealPlan.value = when (result) {
                is AiResult.Success -> result.text.stripMarkdown()
                is AiResult.NotConfigured -> null
                is AiResult.Failure -> null
            }
            _mealPlanError.value = when (result) {
                is AiResult.NotConfigured -> "AI isn't configured on this build."
                is AiResult.Failure -> result.reason
                else -> null
            }
            _mealPlanLoading.value = false
        }
    }

    private val _mealPlanError = MutableStateFlow<String?>(null)
    val mealPlanError: StateFlow<String?> = _mealPlanError.asStateFlow()

    // --- Daily AI insight (home screen) ---
    private val _dailyInsight = MutableStateFlow<String?>(null)
    val dailyInsight: StateFlow<String?> = _dailyInsight.asStateFlow()

    /**
     * Total distinct days she has logged anything — drives Garden Visitors.
     * Was undercounting: it only read daily_logs (water/vitamins/weight/
     * symptoms taps), so a user doing just the mood + journal check-in every
     * day — the most common path — never moved this number and visitors
     * never unlocked. Now reads the union across daily_logs, mood_logs, and
     * journal_entries (see PregnancyDao.getAllActiveDates).
     */
    val daysActive: StateFlow<Int> = repository.getAllActiveDates()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Journal ---
    val journalEntries = repository.getJournalEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _checkedInToday = MutableStateFlow(true) // assume yes until checked
    val checkedInToday: StateFlow<Boolean> = _checkedInToday.asStateFlow()

    fun refreshCheckInState() {
        viewModelScope.launch {
            _checkedInToday.value = repository.hasJournalEntryFor(_todayDate.value)
        }
    }

    /**
     * Saves a memory. If she wrote no caption of her own, one is generated —
     * warm, matched to her note's vibe — with her note itself as the offline
     * fallback so the entry is never blank.
     */
    fun saveJournalEntry(note: String, photoFile: String, mood: Int, shareToClub: Boolean = false) {
        viewModelScope.launch {
            val p = profile.value ?: return@launch
            val week = p.currentWeek

            val caption = OpenRouterClient.completeOrNull(
                systemPrompt = PregaPrompts.journalCaption(
                    week = week,
                    trimester = trimesterFor(week),
                    babyName = p.babyNamePlaceholder,
                    name = p.name,
                ),
                userPrompt = if (note.isBlank()) "No note today — caption the moment itself."
                else "Her note: \"$note\"",
                model = PregaModel.Quick,
                temperature = 1.0,
                maxTokens = 50,
            )?.stripMarkdown() ?: note.ifBlank { "Week $week, held onto." }

            repository.addJournalEntry(
                JournalEntity(
                    date = _todayDate.value,
                    week = week,
                    trimester = trimesterFor(week),
                    photoFile = photoFile,
                    note = note,
                    caption = caption,
                    mood = mood,
                )
            )
            _checkedInToday.value = true
            award(GamificationEngine.Action.JournalEntry, week, "Memory kept")
            AppStats.log(StatEvent.JournalSaved)

            // Community share: her explicit per-entry choice, words only
            // (never the photo in Phase 1), fire-and-forget — a failed share
            // never disturbs the local save that already succeeded.
            if (shareToClub) {
                val text = note.ifBlank { caption }
                com.example.community.CommunityRepository.myProfile()?.let { cp ->
                    com.example.community.CommunityRepository.sharePost(
                        club = cp.dueMonth,
                        profile = cp,
                        week = week,
                        body = text,
                    )
                }
            }
        }
    }

    fun deleteJournalEntry(id: Long) {
        viewModelScope.launch { repository.deleteJournalEntry(id) }
    }

    // --- Daily affirmation ---
    private val _dailyAffirmation = MutableStateFlow<String?>(null)
    val dailyAffirmation: StateFlow<String?> = _dailyAffirmation.asStateFlow()

    /**
     * Offline pool so the card never sits empty. Same rule as the AI prompt:
     * affirms what is true about her — never an outcome promise.
     */
    private val FALLBACK_AFFIRMATIONS = listOf(
        "My body knows how to do this, one day at a time.",
        "I am allowed to rest as much as I need.",
        "Growing a person is work. I am doing it right now.",
        "I don't have to feel grateful every minute to be a good mother.",
        "Today, showing up is enough.",
        "I can ask for help. That is strength, not weakness.",
        "Every week that passes, I have carried us both.",
        "I trust myself to notice what matters.",
    )

    fun refreshDailyAffirmation() {
        viewModelScope.launch {
            val p = profile.value ?: return@launch
            val week = p.currentWeek
            _dailyAffirmation.value = OpenRouterClient.completeOrNull(
                systemPrompt = PregaPrompts.dailyAffirmation(
                    week = week,
                    trimester = trimesterFor(week),
                    babyName = p.babyNamePlaceholder,
                    name = p.name,
                ),
                userPrompt = "Today is ${_todayDate.value}. Give me today's affirmation.",
                model = PregaModel.Quick,
                temperature = 0.7,
                maxTokens = 60,
            )?.stripMarkdown() ?: FALLBACK_AFFIRMATIONS[
                // Stable per-day pick, so it doesn't change on every recomposition.
                (_todayDate.value.hashCode().let { if (it < 0) -it else it }) % FALLBACK_AFFIRMATIONS.size
            ]
        }
    }

    /**
     * Offline insights, one pool per trimester. The insight card must NEVER
     * sit on a skeleton forever — that reads as "the app is broken" (exactly
     * the bug report that prompted this). If the AI call fails for any
     * reason (no credit, rate limit, degenerate output, no internet), the
     * card gets one of these instead, picked stably per day.
     */
    private val FALLBACK_INSIGHTS_T1 = listOf(
        "Early weeks are heavy lifting: organs are forming and your body is building the placenta from scratch. Tiredness now is construction work, not weakness.",
        "Nausea often peaks around now and eases by the second trimester. Small, frequent bites and cold foods are gentler than big meals.",
        "Your blood volume is already increasing to support the baby. Extra water genuinely helps with the headaches and dizziness this can bring.",
        "The baby's heart is among the very first things to form and start beating. Most of the drama is invisible from the outside right now.",
    )
    private val FALLBACK_INSIGHTS_T2 = listOf(
        "Many mothers feel energy return in these middle weeks. A gentle daily walk is one of the best-supported habits for the whole pregnancy.",
        "The baby is developing hearing now. Your voice, carried through your body, is becoming the most familiar sound in their world.",
        "First flutters are often felt in this stretch — like bubbles or a light tap. They get unmistakable with time.",
        "Your centre of gravity is shifting as the bump grows. Slower position changes help with the light-headedness when standing up.",
    )
    private val FALLBACK_INSIGHTS_T3 = listOf(
        "The baby is practicing breathing movements now — inhaling amniotic fluid to strengthen tiny lungs for the outside world.",
        "Sleep gets harder in the third trimester. Pillows under the bump and between the knees make side-sleeping kinder on your back.",
        "Noticing kick patterns matters most now: you know your baby's rhythm best, and any real change is worth a call to your midwife.",
        "Your body is quietly rehearsing too — practice tightenings that come and go are it warming up, not the real thing.",
    )

    private fun fallbackInsight(week: Int): String {
        val pool = when (trimesterFor(week)) {
            1 -> FALLBACK_INSIGHTS_T1
            2 -> FALLBACK_INSIGHTS_T2
            else -> FALLBACK_INSIGHTS_T3
        }
        return pool[(_todayDate.value.hashCode().let { if (it < 0) -it else it }) % pool.size]
    }

    /**
     * Regenerated once per day. The prompt explicitly rotates its angle so two
     * consecutive days never read the same.
     */
    fun refreshDailyInsight() {
        viewModelScope.launch {
            val p = profile.value ?: return@launch
            val week = p.currentWeek
            _dailyInsight.value = OpenRouterClient.completeOrNull(
                systemPrompt = PregaPrompts.dailyInsight(
                    week = week,
                    trimester = trimesterFor(week),
                    babyName = p.babyNamePlaceholder,
                    name = p.name,
                ),
                userPrompt = "Today is ${_todayDate.value}. Give me today's insight.",
                model = PregaModel.Quick,
                // Was 1.0 — the most loop-prone setting, and the two garbage
                // outputs users actually saw (word loops, <pad> spam) both
                // came from this path. 0.7 keeps variety with less chaos.
                temperature = 0.7,
                maxTokens = 120,
            )?.stripMarkdown() ?: fallbackInsight(week)
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

    // ─── Shared helpers ───────────────────────────────────────────────────

    private fun trimesterFor(week: Int): Int = when {
        week <= 13 -> 1
        week <= 27 -> 2
        else -> 3
    }

    /**
     * Runs a rewarded action through the gamification engine and emits whatever
     * the UI should celebrate. Every tracked interaction funnels through here so
     * points, streaks and badges can never drift out of sync.
     */
    /** Steps goal reached (once per day, guarded on the tile side). */
    fun onStepsGoalReached() {
        val week = profile.value?.currentWeek ?: return
        award(GamificationEngine.Action.StepsGoal, week, "A gentle walk done")
    }

    private fun award(
        action: GamificationEngine.Action,
        week: Int,
        reason: String,
    ) {
        viewModelScope.launch {
            val logs = repository.getAllDailyLogs().first()
            val goal = profile.value?.waterGoalGlasses ?: 8

            val outcome = gamification.recordAction(
                action = action,
                currentWeek = week,
                waterGoalHits = logs.count { it.waterGlasses >= goal },
                vitaminDays = logs.count { it.tookVitamins },
                sleepDays = logs.count { it.sleptHours > 0f },
            )

            if (outcome.pointsAwarded > 0) {
                _rewards.emit(RewardEvent.Points(outcome.pointsAwarded, reason))
            }
            if (outcome.streakIncreased) {
                _rewards.emit(RewardEvent.StreakExtended(outcome.progress.currentStreak))
            }
            if (outcome.freezeUsed) {
                _rewards.emit(RewardEvent.StreakProtected(outcome.progress.streakFreezes))
            }
            if (outcome.levelledUp) {
                _rewards.emit(
                    RewardEvent.LevelUp(outcome.progress.level, outcome.progress.levelTitle)
                )
            }
            outcome.newBadges.forEach { _rewards.emit(RewardEvent.BadgeEarned(it)) }
        }
    }

    /** Call from the host activity on each launch so the streak resolves once. */
    fun onAppOpened() {
        viewModelScope.launch {
            val week = profile.value?.currentWeek ?: 12
            award(GamificationEngine.Action.DailyOpen, week, "Welcome back")
            ensureQuestsForToday()
            refreshDailyInsight()
            refreshDailyAffirmation()
            repository.pruneQuestsBefore(_todayDate.value)
        }
    }

    // ─── Daily quests ─────────────────────────────────────────────────────

    /**
     * Generates today's quests if they don't exist yet. Falls back to a curated
     * offline pool so the feature never simply vanishes without a network.
     */
    private suspend fun ensureQuestsForToday() {
        val date = _todayDate.value
        if (repository.getQuestsForDateOnce(date).isNotEmpty()) return

        val p = profile.value
        val week = p?.currentWeek ?: 12

        // Freshness guard: hand the model the last two days' quests and forbid
        // repeats. Without this, similar prompts on similar weeks converge on
        // the same three suggestions — "changed every day" was the explicit ask.
        val recentTitles = runCatching {
            val today = java.time.LocalDate.parse(date)
            (1..2).flatMap { d ->
                repository.getQuestsForDateOnce(today.minusDays(d.toLong()).toString())
            }.map { it.title }
        }.getOrDefault(emptyList())

        val generated = OpenRouterClient.completeOrNull(
            systemPrompt = PregaPrompts.questCopy(
                week = week,
                trimester = trimesterFor(week),
                babyName = p?.babyNamePlaceholder.orEmpty(),
                name = p?.name.orEmpty(),
            ),
            userPrompt = buildString {
                append("Give me today's three quests.")
                if (recentTitles.isNotEmpty()) {
                    append(" These were the last days' quests — do NOT repeat or lightly rephrase any of them: ")
                    append(recentTitles.joinToString("; "))
                }
            },
            model = PregaModel.Quick,
            temperature = 1.0,
            maxTokens = 250,
        )

        val quests = generated?.stripMarkdown()?.let(::parseQuests)?.takeIf { it.size == 3 }
            ?: FallbackQuests.POOL.shuffled().take(3).map { it.first to it.second }

        repository.saveQuests(
            quests.mapIndexed { i, (title, why) ->
                QuestEntity(
                    id = "$date-$i",
                    date = date,
                    title = title,
                    rationale = why,
                )
            }
        )
    }

    /** Parses the `QUEST: ... | WHY: ...` lines the prompt asks for. */
    private fun parseQuests(raw: String): List<Pair<String, String>> =
        raw.lineSequence()
            .filter { it.contains("QUEST:", ignoreCase = true) }
            .mapNotNull { line ->
                val quest = line.substringAfter("QUEST:", "").substringBefore("|").trim()
                val why = line.substringAfter("WHY:", "").trim()
                if (quest.isBlank()) null else quest to why
            }
            .toList()

    fun completeQuest(quest: QuestEntity) {
        if (quest.completed) return
        viewModelScope.launch {
            repository.updateQuest(quest.copy(completed = true))
            award(
                GamificationEngine.Action.CompleteQuest,
                profile.value?.currentWeek ?: 12,
                "Quest complete",
            )
        }
    }

    // ─── Mood ─────────────────────────────────────────────────────────────

    fun logMood(mood: Int, note: String = "", tags: List<String> = emptyList()) {
        viewModelScope.launch {
            repository.saveMood(
                MoodEntity(
                    date = _todayDate.value,
                    mood = mood.coerceIn(1, 5),
                    note = note,
                    tags = tags.joinToString(","),
                )
            )
            award(GamificationEngine.Action.LogMood, profile.value?.currentWeek ?: 12, "Mood logged")
        }
    }

    // ─── Appointments ─────────────────────────────────────────────────────

    fun saveAppointment(appointment: AppointmentEntity) {
        viewModelScope.launch {
            repository.saveAppointment(appointment)
            if (appointment.id == 0) {
                award(
                    GamificationEngine.Action.LogAppointment,
                    profile.value?.currentWeek ?: 12,
                    "Appointment saved",
                )
            }
        }
    }

    fun deleteAppointment(id: Int) {
        viewModelScope.launch { repository.deleteAppointment(id) }
    }

    // ─── Contractions ─────────────────────────────────────────────────────

    val contractions: StateFlow<List<ContractionEntity>> = repository.getContractions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logContraction(durationSeconds: Int, intensity: Int) {
        viewModelScope.launch {
            val previous = contractions.value.firstOrNull()
            val now = System.currentTimeMillis()
            val interval = previous?.let { ((now - it.startTime) / 1000).toInt() } ?: 0
            repository.saveContraction(
                ContractionEntity(
                    startTime = now,
                    durationSeconds = durationSeconds,
                    intervalSeconds = interval,
                    intensity = intensity.coerceIn(1, 3),
                )
            )
        }
    }

    // ─── Onboarding & settings ────────────────────────────────────────────

    /**
     * Writes the profile from the onboarding flow in one transaction and marks
     * setup complete, so a half-finished profile can never leave the app in a
     * state where it re-runs onboarding over existing data.
     */
    fun completeOnboarding(result: com.example.ui.onboarding.OnboardingResult) {
        viewModelScope.launch {
            // `copy()` against any profile a Google sign-in already created
            // earlier in this same flow, so her email/photo/isGoogleSignedIn
            // survive rather than being overwritten by a blank slate.
            val current = profile.value ?: UserProfileEntity(id = 1, name = "")
            repository.saveUserProfile(
                current.copy(
                    name = result.name.ifBlank { current.name },
                    currentWeek = result.week,
                    babyNamePlaceholder = result.babyName.ifBlank { "Little One" },
                    dueDate = result.eddDate,
                    eddDate = result.eddDate,
                    lmpDate = result.lmpDate,
                    dietaryPreferences = result.dietaryPreferences,
                    isFirstPregnancy = result.isFirstPregnancy,
                    notificationsEnabled = result.notificationsEnabled,
                    billingRegion = result.billingRegion,
                    onboardingComplete = true,
                )
            )
            onAppOpened()
        }
    }

    fun updateProfileDetails(name: String, babyName: String, diet: String) {
        viewModelScope.launch {
            val current = profile.value ?: return@launch
            repository.saveUserProfile(
                current.copy(
                    name = name.trim(),
                    babyNamePlaceholder = babyName.trim().ifBlank { "Little One" },
                    dietaryPreferences = diet.trim(),
                )
            )
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            profile.value?.let {
                repository.saveUserProfile(it.copy(notificationsEnabled = enabled))
            }
        }
    }

    fun setWaterGoal(glasses: Int) {
        viewModelScope.launch {
            profile.value?.let {
                repository.saveUserProfile(it.copy(waterGoalGlasses = glasses.coerceIn(1, 20)))
            }
        }
    }

    /**
     * Entitlement cache. Only ever called from the billing client — UI code
     * must never grant premium directly.
     */
    fun syncPremiumEntitlement(isPremium: Boolean) {
        viewModelScope.launch {
            val current = profile.value ?: return@launch
            if (current.isPremium == isPremium) return@launch
            repository.saveUserProfile(current.copy(isPremium = isPremium))
            if (isPremium) AppStats.log(StatEvent.PremiumPurchased)
        }
    }

    // ─── Export ───────────────────────────────────────────────────────────

    private val _exportedData = MutableStateFlow<String?>(null)
    val exportedData: StateFlow<String?> = _exportedData.asStateFlow()

    /**
     * Plain-text export of everything held locally. Deliberately human-readable
     * rather than JSON: the realistic use is printing it or showing it to a
     * midwife, not importing it elsewhere.
     */
    fun exportData() {
        viewModelScope.launch {
            val p = profile.value
            val logs = repository.getAllDailyLogs().first()
            val kicks = repository.getAllKickLogs().first()
            val moods = repository.getRecentMoods(365).first()
            val appts = repository.getAppointments().first()

            _exportedData.value = buildString {
                appendLine("PREGA AI — YOUR DATA")
                appendLine("Exported ${_todayDate.value}")
                appendLine()
                appendLine("PROFILE")
                appendLine("  Name: ${p?.name.orEmpty()}")
                appendLine("  Week: ${p?.currentWeek ?: 0}")
                appendLine("  Due date: ${p?.eddDate.orEmpty()}")
                appendLine()
                appendLine("DAILY LOGS (${logs.size})")
                logs.forEach {
                    appendLine(
                        "  ${it.date} · water ${it.waterGlasses} · " +
                            "vitamins ${if (it.tookVitamins) "yes" else "no"} · " +
                            "sleep ${it.sleptHours}h" +
                            if (it.symptoms.isNotBlank()) " · ${it.symptoms}" else ""
                    )
                }
                appendLine()
                appendLine("KICK SESSIONS (${kicks.size})")
                kicks.forEach {
                    appendLine("  ${it.date} · ${it.count} movements in ${it.durationSeconds / 60}m")
                }
                appendLine()
                appendLine("MOOD (${moods.size})")
                moods.forEach { appendLine("  ${it.date} · ${it.mood}/5 ${it.tags}") }
                appendLine()
                appendLine("APPOINTMENTS (${appts.size})")
                appts.forEach { appendLine("  ${it.date} ${it.time} · ${it.title}") }
            }
        }
    }

    fun clearExport() { _exportedData.value = null }

    companion object {
        const val GREETING =
            "Hi, I'm Prega AI. Ask me anything — how you feel, food, sleep, " +
            "what's normal this week.\n\nI'm not a doctor. If something " +
            "worries you, call your midwife or doctor first."
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
