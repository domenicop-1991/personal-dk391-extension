plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "eu.kanade.tachiyomi.animeextension.it.animeunity"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.animeextension.it.animeunity"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Aniyomi extensions lib
    compileOnly("com.github.aniyomiorg:extensions-lib:14")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Kotlin serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Jsoup for HTML parsing
    implementation("org.jsoup:jsoup:1.17.1")

    // Injekt for dependency injection
    compileOnly("com.github.inorichi.injekt:injekt-core:65b0440")
}
