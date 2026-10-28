import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val albumUpdateUrl = providers.gradleProperty("ALBUM_UPDATE_URL").orElse("").get()
val escapedUpdateUrl = albumUpdateUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val releaseVersionFile = rootProject.file("version.properties")
val releaseVersionProperties = Properties().apply {
    if (releaseVersionFile.isFile) releaseVersionFile.inputStream().use(::load)
}
val releaseVersionCode = releaseVersionProperties.getProperty("VERSION_CODE", "1").toInt()
val releaseVersionName = listOf(
    releaseVersionProperties.getProperty("VERSION_MAJOR", "1"),
    releaseVersionProperties.getProperty("VERSION_MINOR", "0"),
    releaseVersionProperties.getProperty("VERSION_PATCH", "0")
).joinToString(".")
val releaseStoreFile = providers.gradleProperty("ALBUM_STORE_FILE").orElse("").get()
val releaseStorePassword = providers.gradleProperty("ALBUM_STORE_PASSWORD").orElse("").get()
val releaseKeyAlias = providers.gradleProperty("ALBUM_KEY_ALIAS").orElse("").get()
val releaseKeyPassword = providers.gradleProperty("ALBUM_KEY_PASSWORD").orElse("").get()
// LibVLC ships one native library set per ABI (about 60 MB each). Shipping all
// of them in a single APK wastes space on every device, so builds are split
// per ABI by default. Pass -PalbumUniversalApk=true to also emit a single
// universal APK for distribution.
val albumUniversalApk = providers.gradleProperty("albumUniversalApk")
    .orNull?.toBooleanStrictOrNull() ?: false

android {
    namespace = "com.example.album"
    compileSdk {
        // Compiling against 37 is what current AndroidX requires; targetSdk
        // stays on 36 so the app does not silently opt into new runtime
        // behaviour it has not been tested against.
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.album"
        minSdk = 24
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        buildConfigField("String", "UPDATE_URL", "\"$escapedUpdateUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // The app ships Simplified Chinese and English only; drop the other
        // locales that come with AndroidX so the APK stops carrying them.
        localeFilters += listOf("en", "zh")
    }

    buildTypes {
        release {
            if (releaseStoreFile.isNotBlank()) {
                signingConfig = signingConfigs.create("configuredRelease") {
                    storeFile = file(releaseStoreFile)
                    storePassword = releaseStorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            }
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // The baseline locks in the warnings that existed when the review was
        // written (mostly the UseKtx style hints). Anything new fails the
        // build, so warnings cannot quietly pile up again.
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        // Translation coverage is checked once the UI text moves into
        // resources; today it would only report the two strings that exist.
        disable += setOf("MissingTranslation")
    }
    dependenciesInfo {
        // Dependency metadata inside the APK is only used by Play; the local
        // build does not need it and it saves a little space.
        includeInApk = false
        includeInBundle = false
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = albumUniversalApk
        }
    }
    packaging {
        jniLibs {
            // Store the LibVLC native libraries compressed. The system extracts
            // them at install time, which keeps the downloadable APK far
            // smaller (the shared objects compress to roughly half).
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

// Android Studio's Generate Signed Bundle/APK runs packageRelease. Advance
// the persisted version only after that task succeeds, so failed builds do
// not consume a version number.
tasks.matching { it.name == "packageRelease" }.configureEach {
    outputs.upToDateWhen { false }
    doLast {
        if (releaseStoreFile.isBlank()) return@doLast
        val nextProperties = Properties().apply {
            putAll(releaseVersionProperties)
            val patch = getProperty("VERSION_PATCH", "0").toInt()
            setProperty("VERSION_PATCH", (patch + 1).toString())
            setProperty("VERSION_CODE", (releaseVersionCode + 1).toString())
        }
        releaseVersionFile.outputStream().use { nextProperties.store(it, "Album release version; updated after each successful signed release build") }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    // AVI/other containers that the platform extractors cannot demux.
    implementation(libs.libvlc.all)
    testImplementation(libs.junit)
    // Unit tests exercise the update-manifest parser, which uses org.json.
    // The android.jar stubs throw on every call without this.
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
