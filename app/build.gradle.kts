import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localSigningProps = Properties().apply {
    val localFile = rootProject.file("signing.local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

fun resolveSecret(name: String): String? {
    val fromLocal = localSigningProps.getProperty(name)?.trim()
    if (!fromLocal.isNullOrBlank()) return fromLocal
    val fromGradle = (project.findProperty(name) as String?)?.trim()
    if (!fromGradle.isNullOrBlank()) return fromGradle
    val fromEnv = System.getenv(name)?.trim()
    if (!fromEnv.isNullOrBlank()) return fromEnv
    return null
}

val releaseStoreFile = resolveSecret("RELEASE_STORE_FILE")
val releaseStoreType = resolveSecret("RELEASE_STORE_TYPE")
val releaseStorePassword = resolveSecret("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = resolveSecret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = resolveSecret("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "cn.harkerhand.ohmyblbl"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "cn.harkerhand.ohmyblbl"
        minSdk = 32
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(requireNotNull(releaseStoreFile))
                storeType = releaseStoreType ?: "pkcs12"
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    buildFeatures {
        compose = true
    }
}

if (!hasReleaseSigning) {
    logger.warn(
        "Release signing is not fully configured. " +
            "Falling back to debug signing for release builds. " +
            "Set RELEASE_STORE_FILE / RELEASE_STORE_TYPE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD."
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
