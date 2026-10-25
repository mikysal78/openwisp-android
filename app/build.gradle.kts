import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firma della release: keystore.properties (fuori dal repo) con storeFile,
// storePassword, keyAlias e keyPassword; in CI arriva dalle variabili d'ambiente.
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
    System.getenv("OW_KEYSTORE")?.let { setProperty("storeFile", it) }
    System.getenv("OW_KEYSTORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("OW_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("OW_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}

android {
    namespace = "it.ninuxnnxx.openwisp"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.ninuxnnxx.openwisp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signing.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    dependenciesInfo {
        // F-Droid e le build riproducibili non vogliono il blocco cifrato di Google
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.work.runtime)
    implementation(libs.biometric)
    implementation(libs.browser)
    implementation(libs.core.ktx)
    implementation(libs.fragment)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
