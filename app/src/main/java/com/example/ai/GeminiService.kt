package com.example.ai

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(val text: String? = null)

@JsonClass(generateAdapter = true)
data class Content(val parts: List<Part>)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(val content: Content)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(val candidates: List<Candidate>?)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    suspend fun askGemini(prompt: String, systemPrompt: String? = null): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Prega AI Coach (Mock Mode): Your pregnancy is a beautiful journey! For your question: \"$prompt\", please know that it is very common to experience standard body adjustments, mild nausea, or fatigue. To stay healthy, make sure to drink 8-10 glasses of water daily, consume prenatal vitamins, and consult your OB-GYN for any severe symptoms. Feel free to add your GEMINI_API_KEY in the AI Studio Secrets panel to enable real-time personalized AI responses!"
        }
        val contents = listOf(Content(parts = listOf(Part(text = prompt))))
        val systemInstruction = systemPrompt?.let { Content(parts = listOf(Part(text = it))) }
        val request = GenerateContentRequest(contents = contents, systemInstruction = systemInstruction)

        return try {
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I couldn't produce a response. Please try asking again."
        } catch (e: Exception) {
            "Prega AI Coach: I encountered a connection issue. Please verify your internet and API key in the Secrets panel.\n\nError details: ${e.localizedMessage}"
        }
    }
}
