plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Single source of truth for the release name: the last non-empty line of codenames.txt,
// formatted as "codename (version)", e.g. "powder (4.0-0)". The CI release job reads the same line.
val releaseLine = rootProject.file("codenames.txt").readLines()
    .map { it.trim() }
    .lastOrNull { it.isNotEmpty() } ?: "unknown (0.0-0)"
val releaseMatch = Regex("""^(\S+)\s+\(([^)]+)\)""").find(releaseLine)
val releaseCodename = (releaseMatch?.groupValues?.get(1) ?: "unknown")
    .replaceFirstChar { it.uppercase() }
val releaseVersion = releaseMatch?.groupValues?.get(2) ?: "0.0-0"

android {
    namespace = "com.onikiri.open"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.onikiri.open"
        minSdk = 26
        targetSdk = 37
        versionCode = 40
        versionName = releaseVersion

        buildConfigField("String", "VERSION_CODENAME", "\"$releaseCodename\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.libsu.core)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val codename = rootProject.file("codenames.txt")
    .takeIf { it.exists() }
    ?.readLines()
    ?.lastOrNull { it.isNotBlank() }
    ?.trim()
    ?.substringBefore(" ")
    ?.lowercase()
    ?: "powder"

val ver = android.defaultConfig.versionName ?: "4.0-0"

base {
    archivesName.set("Onikiri-$ver-$codename")
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        val dir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        dir.listFiles { f -> f.name.endsWith("-debug.apk") }?.forEach { file ->
            val target = File(dir, file.name.removeSuffix("-debug.apk") + ".apk")
            if (target.exists()) target.delete()
            file.renameTo(target)
        }
    }
}
