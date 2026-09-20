import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun buildConfigString(name: String): String {
    val value = localProperties.getProperty(name, "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$value\""
}

android {
    namespace = "com.example.beaqua"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.beaqua"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-beta.1"

        buildConfigField("String", "SUPABASE_URL", buildConfigString("SUPABASE_URL"))
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            buildConfigString("SUPABASE_PUBLISHABLE_KEY")
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)

    // Mapbox
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.common)
    implementation(libs.mapbox.sdk.services)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Xendit dependencies
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.browser)
    implementation(libs.lottie)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
