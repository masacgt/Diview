plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigning = listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    .associateWith { providers.environmentVariable("DIVIEW_RELEASE_$it").orNull }
val hasReleaseSigning = releaseSigning.values.any { !it.isNullOrBlank() }
if (hasReleaseSigning) {
    require(releaseSigning.values.all { !it.isNullOrBlank() }) {
        "Set all four DIVIEW_RELEASE_STORE_FILE, STORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD environment variables."
    }
}

android {
    namespace = "com.kurogane.dividend"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kurogane.dividend"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.kurogane.dividend.ReleaseFixtureInstrumentation"

        buildConfigField(
            "String",
            "DIVIEW_D1_API_BASE_URL",
            "\"${providers.gradleProperty("diviewD1ApiBaseUrl").orElse("https://diview-irbank.omasa.workers.dev").get().trimEnd('/')}\""
        )
    }

    signingConfigs {
        if (hasReleaseSigning) create("production") {
            storeFile = file(releaseSigning.getValue("STORE_FILE")!!)
            storePassword = releaseSigning.getValue("STORE_PASSWORD")
            keyAlias = releaseSigning.getValue("KEY_ALIAS")
            keyPassword = releaseSigning.getValue("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("production")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
