plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.moodproject"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.moodproject"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Add these options for handling large model files
    aaptOptions {
        noCompress("model")
    }

    packagingOptions {
        resources {
            excludes += listOf(
                "project.properties",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    // Existing dependencies
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Vosk library for offline speech recognition
    implementation("com.alphacephei:vosk-android:0.3.47")

    // For handling JSON responses from Vosk
    implementation("org.json:json:20210307")
}

// Make sure you have the jcenter repository in your project-level settings.gradle.kts file
// repositories {
//    google()
//    mavenCentral()
//    jcenter()
// }