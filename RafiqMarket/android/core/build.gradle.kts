import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String): String? = (project.findProperty(name) as String?) ?: localProps.getProperty(name)

// Server ünvanları: local.properties və ya -P parametri ilə verilir
val debugApiUrl = prop("RAFIQ_API_URL_DEBUG") ?: "http://10.0.2.2:8080/"
val releaseApiUrl = prop("RAFIQ_API_URL") ?: ""
val mapsKeySet = !prop("MAPS_API_KEY").isNullOrBlank()

android {
    namespace = "az.rafiqmarket.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("boolean", "MAPS_KEY_SET", mapsKeySet.toString())
    }
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiUrl\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Release APK yalnız HTTPS server ünvanı ilə yığıla bilər
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        require(releaseApiUrl.startsWith("https://") && releaseApiUrl.endsWith("/")) {
            "Release build üçün local.properties-də RAFIQ_API_URL=https://.../ təyin edin (sonu / ilə)."
        }
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.material3)
    api(libs.compose.material.icons)
    api(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)
    api(libs.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.coil.compose)
    api(libs.hilt.navigation.compose)
    api(libs.androidx.navigation.compose)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    testImplementation(libs.junit)
}
