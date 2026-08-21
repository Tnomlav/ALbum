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

android {
    namespace = "com.example.album"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
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

    buildTypes {
        release {
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
}

// Android Studio's Generate Signed Bundle/APK runs packageRelease. Advance
// the persisted version only after that task succeeds, so failed builds do
// not consume a version number.
tasks.matching { it.name == "packageRelease" }.configureEach {
    outputs.upToDateWhen { false }
    doLast {
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
