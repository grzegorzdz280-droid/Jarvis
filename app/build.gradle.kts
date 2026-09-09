plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.jarvis.mobile"; compileSdk = 35
    defaultConfig { applicationId = "com.jarvis.mobile"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "0.3.0" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.json:json:20240303")
}
