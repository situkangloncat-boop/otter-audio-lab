plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.otter.audiolab"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.otter.audiolab"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.1")

    // FFmpegKit ASLI (com.arthenica) sudah PENSIUN & dihapus dari Maven Central (April 2025).
    // Pakai fork komunitas yang masih aktif dirilis, API package-nya tetap sama:
    // com.arthenica.ffmpegkit.* -- jadi kode di MainActivity.kt tidak perlu diubah.
    // Cek versi terbaru di: https://mvnrepository.com/artifact/io.github.jamaismagic.ffmpeg/ffmpeg-kit-lts-full-16kb
    implementation("io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-full-16kb:6.1.7")
}
