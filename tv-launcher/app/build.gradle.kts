plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Resolved at the Project level (where Gradle's file() helper lives) so the
// signing block below stays a simple null check.
val keystoreFile = System.getenv("ASPECTS_KEYSTORE")
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.aspects.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aspects.tvlauncher"
        // The SINOTEC SWTV-20AE runs Android TV 11 (API 30). minSdk 21 keeps the
        // APK installable on essentially any Android TV box you might add later.
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing is driven by env vars so no private key ever lands in this
    // public repo. When the secrets are absent the release build falls back to the
    // debug key, which still produces a perfectly installable sideload APK.
    signingConfigs {
        create("sideload") {
            if (keystoreFile != null) {
                storeFile = keystoreFile
                storePassword = System.getenv("ASPECTS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ASPECTS_KEY_ALIAS")
                keyPassword = System.getenv("ASPECTS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 is off on purpose: this APK is already tiny and an untestable
            // shrink pass is not worth the risk on a device I cannot debug on.
            isMinifyEnabled = false
            signingConfig =
                if (keystoreFile != null) signingConfigs.getByName("sideload")
                else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
