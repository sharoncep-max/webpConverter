plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStore = providers.environmentVariable("WEBP_KEYSTORE")
val releaseAlias = providers.environmentVariable("WEBP_KEY_ALIAS")
val releaseStorePassword = providers.environmentVariable("WEBP_STORE_PASSWORD")
val releaseKeyPassword = providers.environmentVariable("WEBP_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStore, releaseAlias, releaseStorePassword, releaseKeyPassword)
    .all { it.isPresent && it.get().isNotBlank() }

android {
    namespace = "com.example.webpconverter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apps.multiformat.webpconverter"
        minSdk = 28
        targetSdk = 36
        versionCode = 6
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("production") {
                storeFile = file(releaseStore.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("production")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

tasks.register("verifyReleaseSigning") {
    doLast {
        check(hasReleaseSigning) {
            "Release signing requires WEBP_KEYSTORE, WEBP_KEY_ALIAS, WEBP_STORE_PASSWORD and WEBP_KEY_PASSWORD."
        }
        check(file(releaseStore.get()).isFile) { "WEBP_KEYSTORE must point to an existing keystore." }
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn("verifyReleaseSigning")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.17.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.work:work-testing:2.10.5")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
