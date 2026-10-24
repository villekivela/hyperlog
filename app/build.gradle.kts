plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hyperlog.app"
    // Current Compose and Material 3 1.5 refuse to build against 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hyperlog.app"
        minSdk = 26
        targetSdk = 36
        // The release tag sets the version (SPEC, Distribution): `-PhyperlogVersion=v0.2.0` gives
        // versionName 0.2.0 and versionCode 200 (major·10000 + minor·100 + patch), as AppVersion reads it.
        val version = (findProperty("hyperlogVersion") as String? ?: "0.1.0").removePrefix("v")
        val parts = Regex("""(\d+)\.(\d{1,2})\.(\d{1,2})""").matchEntire(version)?.groupValues?.drop(1)?.map(String::toInt)
            ?: error("hyperlogVersion must be MAJOR.MINOR.PATCH (minor and patch below 100), was $version")
        versionCode = parts[0] * 10_000 + parts[1] * 100 + parts[2]
        versionName = version
    }

    // The release key never enters the repo (SPEC, Distribution). The release workflow decodes it
    // into the runner's temp dir and passes its path and password as env vars; locally the same
    // names work as Gradle properties. Without them a release build comes out unsigned.
    fun signingValue(name: String): String? =
        (findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() }
    val keystore = signingValue("HYPERLOG_KEYSTORE_FILE")
    val releaseSigning = keystore?.let {
        signingConfigs.create("release") {
            storeFile = file(it)
            storeType = "PKCS12"
            storePassword = signingValue("HYPERLOG_KEYSTORE_PASSWORD")
                ?: error("HYPERLOG_KEYSTORE_FILE is set but HYPERLOG_KEYSTORE_PASSWORD isn't")
            // PKCS12 keeps one password for the store and the key.
            keyPassword = storePassword
            keyAlias = signingValue("HYPERLOG_KEY_ALIAS") ?: "hyperlog"
        }
    }

    buildTypes {
        debug {
            // Installs beside a release build.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "DEV Hyperlog")
        }
        release {
            resValue("string", "app_name", "Hyperlog")
            isMinifyEnabled = false
            signingConfig = releaseSigning
        }
    }

    buildFeatures {
        compose = true
        resValues = true
        // The version the update check compares against, and DEBUG, which turns it off in .dev builds.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        // Robolectric runs Compose and resources in plain JVM unit tests.
        unitTests.isIncludeAndroidResources = true
        // Robolectric's SDK 36 sandbox reaches into JDK internals.
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            // The live Claude test runs only with a key, so a run with one isn't served from a run without.
            it.inputs.property("anthropicApiKeySet", System.getenv("ANTHROPIC_API_KEY").isNullOrBlank().not())
        }
    }
}

room {
    // Exported schemas are the baseline for migrations once a release holds real data.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.anthropic.java)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
