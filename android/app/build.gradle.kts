plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "br.com.gamemap"
    compileSdk = 36
    defaultConfig {
        applicationId = "br.com.gamemap.goiania"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        debug { applicationIdSuffix = ".dev"; versionNameSuffix = "-debug" }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")
    implementation("org.maplibre.gl:android-sdk-opengl:13.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.car.app:app-testing:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

val verifyGameAssets by tasks.registering {
    group = "verification"
    description = "Checks that the separately supplied original game asset pack is prepared."
    doLast {
        val required = listOf(
            "game-assets/catalog.json",
            "game-assets/gta/fonts/pricedow.ttf",
            "game-assets/rdr2/fonts/HapnaSlabSerif-DemiBold.ttf",
            "game-assets/rdr2/fonts/RDRLino-Regular.ttf",
            "maps/glyphs/NavSkinGtaRoad/0-255.pbf",
            "maps/glyphs/NavSkinRdrMap/0-255.pbf",
            "maps/sprites/gta.png", "maps/sprites/rdr2.png",
            "navigation-icons/gta/vehicles/arrow.png", "navigation-icons/gta/vehicles/arrow@2x.png",
        )
        val missing = required.filterNot { file("src/main/assets/$it").isFile }
        check(missing.isEmpty()) {
            "Original game asset pack is missing: ${missing.joinToString()}. " +
                "Use the prepared local package or follow docs/game-assets-provenance.md before building."
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyGameAssets) }
