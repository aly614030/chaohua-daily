plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "cn.chaohua.collector"
    compileSdk = 35
    defaultConfig {
        applicationId = "cn.chaohua.collector"
        minSdk = 29
        targetSdk = 35
        versionCode = 25
        versionName = "0.2.5"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}
