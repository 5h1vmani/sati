plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing reads the key's path and password from the environment.
// tools/release.sh sets both from ~/keys and the macOS Keychain, so no secret
// is ever in a file here. Without them, the release APK is unsigned, which is
// how F-Droid builds it.
val releaseKeystore = providers.environmentVariable("SATI_KEYSTORE").orNull
val releasePassword = providers.environmentVariable("SATI_KEY_PASSWORD").orNull
val signRelease = releaseKeystore != null && releasePassword != null

android {
    namespace = "com.padakanti.sati"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.padakanti.sati"
        // The phone is a Pixel 3 XL on Android 12, which is API 31.
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        if (signRelease) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releasePassword
                keyAlias = "sati"
                keyPassword = releasePassword
            }
        }
    }

    buildTypes {
        release {
            // R8 removes unused code and resources. Compose is large unshrunk.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (signRelease) signingConfig = signingConfigs.getByName("release")
        }
    }

    // F-Droid asks apps not to embed Google's dependency metadata block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
}
