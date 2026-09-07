plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.comsat.audio.smoke"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    // Keep the runner and its libraries out of the optimized app's process.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        maybeCreate("release").apply {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "release" }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
}
