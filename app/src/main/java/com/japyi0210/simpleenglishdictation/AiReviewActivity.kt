package com.japyi0210.simpleenglishdictation

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.japyi0210.simpleenglishdictation.adapter.ChatAdapter
import com.japyi0210.simpleenglishdictation.model.ChatMessage
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.*

data class Review(
    val sentence: String = "",
    val userInput: String = "",
    val feedback: String = "",
    val similarity: Int = 0,
    val timestamp: Date? = null,
    val id: String = "",
    val replayCount: Int = 0,
    val usedHint: Boolean = false
)

class AiReviewActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReviewAdapter
    private val reviewList = mutableListOf<Review>()
    private lateinit var emptyReviewHint: TextView

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<ChatMessage>()
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var emptyHint: TextView
    private var lastMessageTime = 0L

    private var lastUserQuestion: String? = null
    private var lastGptAnswer: String? = null

    private var mInterstitialAd: InterstitialAd? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_review)

        MobileAds.initialize(this) {}
        val adView = findViewById<AdView>(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        loadInterstitialAd()

        emptyReviewHint = findViewById(R.id.empty_review_hint)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ReviewAdapter(reviewList)
        recyclerView.adapter = adapter

        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        chatAdapter = ChatAdapter(chatList)
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter

        inputField = findViewById(R.id.input_question)
        sendButton = findViewById(R.id.button_send)
        emptyHint = findViewById(R.id.empty_chat_hint)
        updateEmptyHintVisibility()

        sendButton.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastMessageTime < 15000) {
                Toast.makeText(this, "15초마다 질문할 수 있어요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val question = inputField.text.toString().trim()
            if (question.isEmpty()) {
                Toast.makeText(this, "AI도우미에게 질문을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            inputField.setText("")
            lastMessageTime = now

            chatList.clear()
            chatAdapter.notifyDataSetChanged()
            updateEmptyHintVisibility()

            val userMessage = ChatMessage(true, question)
            chatList.add(userMessage)
            chatAdapter.notifyItemInserted(chatList.size - 1)
            chatRecyclerView.scrollToPosition(chatList.size - 1)
            updateEmptyHintVisibility()

            fetchApiKeyFromRemoteConfig { apiKey ->
                if (!apiKey.isNullOrBlank()) {
                    sendQuestionToGPT(question, apiKey)
                } else {
                    val errorMessage = ChatMessage(false, "❗ API 키를 불러올 수 없습니다.")
                    chatList.add(errorMessage)
                    chatAdapter.notifyItemInserted(chatList.size - 1)
                    chatRecyclerView.scrollToPosition(chatList.size - 1)
                    updateEmptyHintVisibility()
                }
            }
        }

        loadReviews()
        setupBottomNavigation()
    }

    private fun updateEmptyHintVisibility() {
        emptyHint.visibility = if (chatList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun fetchApiKeyFromRemoteConfig(onKeyFetched: (String?) -> Unit) {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf("openai_api_key" to ""))

        remoteConfig.fetchAndActivate().addOnCompleteListener {
            val key = if (it.isSuccessful) remoteConfig.getString("openai_api_key") else null
            onKeyFetched(key)
        }
    }

    private fun sendQuestionToGPT(question: String, apiKey: String) {
        coroutineScope.launch {
            val responseText = withContext(Dispatchers.IO) {
                try {
                    val messagesArray = JSONArray().apply {
                        if (!lastUserQuestion.isNullOrBlank() && !lastGptAnswer.isNullOrBlank()) {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", lastUserQuestion)
                            })
                            put(JSONObject().apply {
                                put("role", "assistant")
                                put("content", lastGptAnswer)
                            })
                        }
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "$question\n\nProvide a feedback in KOREAN using 2 or 3 sentences in a friendly tone.")
                        })
                    }

                    val json = JSONObject().apply {
                        put("model", "gpt-4.1-nano-2025-04-14")
                        put("messages", messagesArray)
                        put("temperature", 0.7)
                    }

                    val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                    val request = Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(requestBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext "❌ 응답 실패: ${response.code}"
                        }
                        val body = response.body?.string()
                        JSONObject(body)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                    }
                } catch (e: Exception) {
                    "⚠️ 오류 발생: ${e.localizedMessage}"
                }
            }

            val gptMessage = ChatMessage(false, responseText)
            chatList.add(gptMessage)
            chatAdapter.notifyItemInserted(chatList.size - 1)
            chatRecyclerView.scrollToPosition(chatList.size - 1)
            updateEmptyHintVisibility()

            saveChatToFirebase(question, responseText)

            lastUserQuestion = question
            lastGptAnswer = responseText
        }
    }

    private fun saveChatToFirebase(question: String, answer: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val chat = mapOf(
            "question" to question,
            "answer" to answer,
            "timestamp" to Date()
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .collection("chat_logs")
            .add(chat)
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            "ca-app-pub-1872760638277957/9712274803",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    mInterstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                }
            }
        )
    }

    private fun loadReviews() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인 후 사용 가능합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .collection("reviews")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                reviewList.clear()
                for (doc in documents) {
                    val review = doc.toObject(Review::class.java).copy(id = doc.id)
                    reviewList.add(review)
                }
                adapter.notifyDataSetChanged()
                emptyReviewHint.visibility = if (reviewList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(this, "리뷰를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.selectedItemId = R.id.ai_review

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_menu -> {
                    startActivity(Intent(this, ScenarioSelectActivity::class.java))
                    true
                }
                R.id.nav_dictation -> {
                    Toast.makeText(this, "시나리오를 선택하세요.", Toast.LENGTH_SHORT).show()
                    false
                }
                R.id.ai_review -> true
                R.id.nav_account -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                    } else {
                        startActivity(Intent(this, StatisticsChartActivity::class.java))
                    }
                    true
                }
                R.id.nav_exit -> {
                    val versionName = try {
                        packageManager.getPackageInfo(packageName, 0).versionName
                    } catch (e: Exception) {
                        "알 수 없음"
                    }

                    val exitMessage = """
                        학습기록을 저장하고 종료하시겠습니까?

                        📦 버전: $versionName
                        📧 문의: CREN-J (japyi0210@gmail.com)
                    """.trimIndent()

                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("저장 및 종료")
                        .setMessage(exitMessage)
                        .setPositiveButton("예") { _, _ -> showAdOrExit() }
                        .setNegativeButton("아니오", null)
                        .show()
                    true
                }
                else -> false
            }
        }
    }

    private fun showAdOrExit() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    FirebaseAuth.getInstance().signOut()
                    finishAffinity()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    FirebaseAuth.getInstance().signOut()
                    finishAffinity()
                }
            }
            mInterstitialAd?.show(this)
        } else {
            FirebaseAuth.getInstance().signOut()
            finishAffinity()
        }
    }
}
