plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "h.lillie.ytplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "h.lillie.ytplayer"
        minSdk = 33
        // noinspection OldTargetApi, EditedTargetSdkVersion
        targetSdk = 34
        versionCode = 15
        versionName = "1.1.5"
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

    flavorDimensions += "android"
    productFlavors {
        create("exoplayer") {
            dimension = "android"
        }
        create("vlc") {
            dimension = "android"
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

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    "exoplayerImplementation"(libs.androidx.media3.cast)
    "exoplayerImplementation"(libs.androidx.media3.common)
    "exoplayerImplementation"(libs.androidx.media3.container)
    "exoplayerImplementation"(libs.androidx.media3.database)
    "exoplayerImplementation"(libs.androidx.media3.datasource)
    "exoplayerImplementation"(libs.androidx.media3.datasource.okhttp)
    "exoplayerImplementation"(libs.androidx.media3.decoder)
    "exoplayerImplementation"(libs.androidx.media3.exoplayer)
    "exoplayerImplementation"(libs.androidx.media3.exoplayer.hls)
    "exoplayerImplementation"(libs.androidx.media3.exoplayer.smoothstreaming)
    "exoplayerImplementation"(libs.androidx.media3.extractor)
    implementation(libs.androidx.media3.session)
    "exoplayerImplementation"(libs.androidx.media3.ui)
    "exoplayerImplementation"(libs.androidx.mediarouter)
    "exoplayerImplementation"(libs.glide)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    "vlcImplementation"(libs.libvlc.all)
    implementation(libs.material)
    implementation(libs.okhttp)
}