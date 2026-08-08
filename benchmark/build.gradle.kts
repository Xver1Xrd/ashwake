plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.ashwake.benchmark"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    defaultConfig {
        // Macrobenchmark меряет холодный старт и кадры на реальном устройстве,
        // поэтому minSdk 24 недостаточно: нужен API 24+ с профилями
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Мерять имеет смысл только на сборке, близкой к релизной:
        // debug-сборка медленнее в разы, и её числа ни о чём не говорят
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "benchmark"
    }
}
