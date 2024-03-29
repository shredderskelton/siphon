plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 31

    defaultConfig {
        applicationId = "com.shredder.siphonapp"
        minSdk = 16
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"
        //testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf(
            "-Xopt-in=kotlin.ExperimentalStdlibApi",
            "-Xopt-in=kotlin.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlin.time.ExperimentalTime",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":siphon"))
    implementation(Kotlin.jdk8)
    implementation(AndroidX.coreKtx)
    implementation(Google.material)
    implementation(AndroidX.constraintLayout)
    implementation(KotlinX.coroutines)

    implementation(AndroidX.lifecycleRuntimeKtx)
}
