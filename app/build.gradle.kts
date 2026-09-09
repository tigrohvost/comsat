plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val releaseKeystorePath = System.getenv("COMSAT_KEYSTORE")?.takeIf { it.isNotBlank() }
val releaseStorePassword =
    System.getenv("COMSAT_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias =
    System.getenv("COMSAT_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "comsat"
val releaseKeyPassword =
    System.getenv("COMSAT_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: releaseStorePassword

if ((releaseKeystorePath == null) != (releaseStorePassword == null)) {
    throw GradleException(
        "Release signing requires both COMSAT_KEYSTORE and COMSAT_KEYSTORE_PASSWORD"
    )
}
val releaseSigningConfigured = releaseKeystorePath != null

android {
    namespace = "com.comsat.audio"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.comsat.audio"
        minSdk = 26
        targetSdk = 37
        versionCode = 11
        versionName = "1.3.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Select the variant for JVM tests; UI checks live in the separate smoke module.
    testBuildType = providers.gradleProperty("comsat.testBuildType").getOrElse("debug")

    signingConfigs {
        if (releaseSigningConfigured) {
            // Credentials come from the environment and never enter VCS.
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = releaseKeyAlias
                keyPassword = requireNotNull(releaseKeyPassword)
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
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

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.session)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coroutines.android)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
