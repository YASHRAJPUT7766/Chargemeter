plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    // Uncomment the line below only after you've added your app to a Firebase
    // project and dropped google-services.json into this app/ folder.
    // See README.md "Optional: Firebase setup" section.
    // id("com.google.gms.google-services")
}

android {
    namespace = "com.yash.chargemeterpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yash.chargemeterpro"
        minSdk = 26 // Android 8.0 — required for reliable BatteryManager EXTRA_ properties
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Fill these in via ~/.gradle/gradle.properties or environment variables
            // before running a release build. Never commit real keystore secrets.
            storeFile = System.getenv("CMP_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("CMP_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("CMP_KEY_ALIAS")
            keyPassword = System.getenv("CMP_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only apply the release signing config when secrets are actually present,
            // so a clean checkout still assembles a debug-signed release for testing.
            if (System.getenv("CMP_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            // TopAppBar (ChargeMeterNavHost.kt, SessionDetailScreen.kt) is marked
            // experimental in Material3 1.3.1. Opting in at the module level here
            // avoids scattering @OptIn(ExperimentalMaterial3Api::class) across
            // every call site — revisit if Material3 stabilizes this API later.
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // --- Core / Compose BOM ---
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // --- Hilt (DI) ---
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // --- Room (local persistence — charging sessions, samples, alerts) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- DataStore (settings / preferences / thresholds) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- WorkManager (background monitoring, auto session close) ---
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // --- Glance (Home screen widget, Compose-based) ---
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // --- Charts (live graphs: wattage/current/voltage/% /temp vs time) ---
    implementation("com.patrykandpatrick.vico:compose:2.0.0-beta.4")
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-beta.4")

    // --- Kotlin ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // --- PDF report export ---
    implementation("com.itextpdf:itext7-core:8.0.5")

    // --- Accompanist permissions (notification permission flow on Android 13+) ---
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // --- Optional Firebase (only active once google-services.json is added +
    //     the plugin above is uncommented). Auth / Crashlytics / Analytics are
    //     opt-in per README — the app must build and run fully without them.
    // implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    // implementation("com.google.firebase:firebase-auth-ktx")
    // implementation("com.google.firebase:firebase-crashlytics-ktx")
    // implementation("com.google.firebase:firebase-analytics-ktx")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
