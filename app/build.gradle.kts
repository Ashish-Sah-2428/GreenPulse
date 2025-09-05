plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.mapapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mapapp"
        minSdk = 24
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {



    // HTTP call ke liye
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
// Coroutine for background
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


    implementation ("com.google.firebase:firebase-database:20.3.0")
    implementation ("androidx.lifecycle:lifecycle-viewmodel:2.8.0")
    implementation ("androidx.lifecycle:lifecycle-livedata:2.8.0")
    implementation ("org.osmdroid:osmdroid-android:6.1.16")
    implementation ("com.google.firebase:firebase-database:20.3.0")
    implementation ("androidx.lifecycle:lifecycle-livedata:2.6.2")
    implementation ("androidx.lifecycle:lifecycle-viewmodel:2.6.2")




    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.preference)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}