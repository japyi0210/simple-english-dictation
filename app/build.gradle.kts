plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
    id("com.google.gms.google-services") // ✅ Firebase용
}

android {
    namespace = "com.japyi0210.simpleenglishdictation"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.japyi0210.simpleenglishdictation"
        minSdk = 23
        targetSdk = 35
        versionCode = 28
        versionName = "1.3.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ OpenAI API 키를 BuildConfig에 안전하게 주입
        val apiKey: String = project.findProperty("OPENAI_API_KEY") as? String ?: ""
        buildConfigField("String", "OPENAI_API_KEY", "\"$apiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true // ✅ View 기반 레이아웃을 함께 쓰는 경우
    }
}

dependencies {

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ✅ ChatGPT API 연동
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("org.json:json:20210307")

    // ✅ Firebase 관련
    implementation(platform("com.google.firebase:firebase-bom:33.15.0"))
    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore-ktx:24.10.1")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // ✅ Google In-App Review (⭐ 추가)
    implementation("com.google.android.play:review:2.0.1")

    // ✅ UI
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")

    // ✅ Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ✅ Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ✅ Google Ads
    implementation("com.google.android.gms:play-services-ads:24.4.0")

    // ✅ Jetpack Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ✅ MPAndroidChart (BarChart, LineChart 등)
    implementation("com.github.PhilJay:MPAndroidChart:3.1.0")

    // ✅ Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    }

// ✅ Firebase 설정 적용
apply(plugin = "com.google.gms.google-services")
