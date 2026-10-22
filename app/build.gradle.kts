plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.parentcontrol.socialblocker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.parentcontrol.socialblocker.detox"
        minSdk = 24
        targetSdk = 34
        versionCode = 15
        versionName = "1.11.0-detox"
    }

    flavorDimensions += "audience"
    productFlavors {
        // The family build is the one for a child's phone: a parent PIN on the whole app and
        // the section quotas (WhatsApp's Updates, CapCut's templates) kept by an accessibility
        // service. The public build carries neither the service nor the PIN: Reader's Detox
        // stays a detox you impose on yourself. Same application id and key, so the family
        // build installs over the public one; its version code is lifted well above the public
        // one so F-Droid, which only ever offers something newer, never puts the public build back.
        create("famille") {
            dimension = "audience"
            isDefault = true
            buildConfigField("boolean", "FAMILLE", "true")
            versionCode = 1000 + (android.defaultConfig.versionCode ?: 1)
            versionNameSuffix = "-famille"
        }
        create("publique") {
            dimension = "audience"
            buildConfigField("boolean", "FAMILLE", "false")
        }
    }

    buildTypes { release { isMinifyEnabled = false } }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // the rules JSON is parsed with the platform's org.json; the JVM tests need the real one
    testImplementation("org.json:json:20240303")
}
