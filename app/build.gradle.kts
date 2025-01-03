plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.chaquopy.python)
}

android {
    namespace = "h.lillie.ytplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "h.lillie.ytplayer"
        minSdk = 30
        // noinspection OldTargetApi, EditedTargetSdkVersion
        targetSdk = 34
        versionCode = 16
        versionName = "1.1.6"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
        }
    }

    flavorDimensions += "type"
    productFlavors {
        create("android") {
            dimension = "type"
        }
        create("chromeos") {
            dimension = "type"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            install("yt-dlp")
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.container)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.mediarouter)
    implementation(libs.glide)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.material)
    implementation(libs.okhttp)
}