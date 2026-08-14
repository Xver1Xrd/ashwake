plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.ashwake"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.ashwake"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            // Схема Room в git — чтобы миграции ревьюились по диффу, а не на слово
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    // Подпись релиза берётся из переменных окружения: держать хранилище
    // ключей в репозитории нельзя, а собирать релиз debug-ключом — значит
    // выпустить сборку, которую нельзя обновить настоящей.
    //
    // Ключа нет — конфигурация не создаётся, и релиз собирается неподписанным
    // и честно об этом молчит. Падать здесь неправильно: debug-сборка
    // на машине без ключа должна собираться.
    val releaseKeystore = System.getenv("ASHWAKE_KEYSTORE")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.exists() }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("ASHWAKE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ASHWAKE_KEY_ALIAS")
                keyPassword = System.getenv("ASHWAKE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        // Сборка для Macrobenchmark: близка к релизной, но отлаживаемая
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
        release {
            signingConfig = releaseKeystore?.let { signingConfigs.getByName("release") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    /**
     * Статический анализ.
     *
     * Появился после того, как lint нашёл в проекте вызовы API 31 и 34 при
     * minSdk 26: код компилировался молча и падал на устройстве. Такое ловится
     * только здесь, поэтому ошибки роняют сборку, а не копятся в отчёте.
     *
     * Предупреждения не роняют: их сотни, и половина — «вышла новая версия
     * библиотеки». Смешивать это с настоящими падениями значит перестать
     * читать и то и другое.
     */
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        // Проверять переводы нечего: приложение одноязычное
        disable += setOf("MissingTranslation", "GradleDependency", "AndroidGradlePluginVersion")
        textReport = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric поднимает настоящий контекст Android: без ресурсов
            // не создать ни базу, ни строки
            isIncludeAndroidResources = true
            all {
                // Robolectric поднимает Android целиком: дефолтной кучи
                // на композицию с тестовым графом Hilt не хватает
                it.maxHeapSize = "2g"
            }
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    // Размытие фона под навигационной панелью и панелью вкладок (дизайн-система, п. 2)
    implementation(libs.haze)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.documentfile)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Room и ресурсы на JVM: инструментальные тесты требуют эмулятора,
    // а проверять схему и восстановление из архива нужно на каждой сборке
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
}
