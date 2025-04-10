import java.util.Properties
import java.io.FileInputStream

// --- Código para carregar local.properties (Necessário para API Key) ---
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
} else {
    println("Warning: local.properties not found. API Key will be missing from BuildConfig.")
}
// --- FIM ---

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
    // --- ADICIONADO PLUGIN KSP ---
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.mentesa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mentesa"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- LÓGICA MANUAL BuildConfigField (Necessária para API Key) ---
        val apiKeyFromProperties = localProperties.getProperty("apiKey") ?: "" // Use "apiKey" se for o nome em local.properties
        if (apiKeyFromProperties.isBlank()) {
            println("Warning: 'apiKey' not found in local.properties. BuildConfig field will be empty.")
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"${apiKeyFromProperties}\"")
        // --- FIM ---
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {}
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11 // Mantenha 11 ou mude para 17 se preferir/necessário
        targetCompatibility = JavaVersion.VERSION_11 // Mantenha 11 ou mude para 17
    }
    kotlinOptions {
        jvmTarget = "11" // Mantenha 11 ou mude para 17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.generativeai)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)
    implementation("com.github.jeziellago:compose-markdown:0.3.5")

    // --- DEPENDÊNCIAS DO ROOM ADICIONADAS ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler) // Usa KSP para o compilador
    // --- FIM ---

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // --- DEPENDÊNCIAS DE TESTE ROOM/COROUTINES ---
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.lifecycle.viewmodel.ktx) // Dependência KTX explícita
}