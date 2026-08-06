import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing credentials come from local.properties (git-ignored) or the
// matching environment variables for CI. Nothing secret is ever committed.
// When they are absent the release build is simply left unsigned rather than
// failing, so a clean checkout can still run `assembleRelease` to verify R8.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingSecret(key: String): String? =
    (localProperties.getProperty(key) ?: System.getenv(key))?.takeIf(String::isNotBlank)

val releaseKeystore = signingSecret("RELEASE_STORE_FILE")

android {
    namespace = "com.sannyas.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sannyas.player"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingSecret("RELEASE_STORE_PASSWORD")
                keyAlias = signingSecret("RELEASE_KEY_ALIAS")
                keyPassword = signingSecret("RELEASE_KEY_PASSWORD")
                // Play requires v2; v1 keeps the APK installable on the API 24
                // devices minSdk still admits.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required for java.time and other APIs on minSdk 24.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
            "-opt-in=androidx.media3.common.util.UnstableApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Media3 stamps a timestamp into every PlaybackException via
        // SystemClock, which throws in a JVM test unless stubs return defaults.
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    bundle {
        language {
            // Both interface languages ship in every install. Play's default is
            // to deliver only the device's locale, which would leave the
            // in-app language picker offering Hindi to a device that never
            // downloaded the Hindi strings.
            enableSplit = false
        }
    }

    androidResources {
        // discourses.json ships uncompressed so it can be memory-mapped quickly.
        noCompress += "json"
        // The interface language is chosen in-app (Settings > Language) and
        // applied by AppLocale, so the system's per-app picker is deliberately
        // not advertised — two controls for one setting only ever disagree.
        generateLocaleConfig = false
    }

    // Exported Room schemas are shipped only to the instrumentation APK, where
    // MigrationTestHelper reads them. They must not bloat the release build.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

// Room exports its schema so future migrations can be verified against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.palette)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Bridges suspend functions to the ListenableFuture API MediaLibraryService uses.
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.accompanist.permissions)
    // In-app review; degrades to a no-op where Play is unavailable.
    implementation(libs.play.review)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
