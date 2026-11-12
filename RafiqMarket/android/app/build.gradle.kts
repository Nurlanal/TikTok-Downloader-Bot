import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firebase yalnız google-services.json mövcud olduqda aktivləşir (REAL AÇAR TƏLƏB OLUNUR)
val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) apply(plugin = libs.plugins.google.services.get().pluginId)

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String): String? = (project.findProperty(name) as String?) ?: localProps.getProperty(name)

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "az.rafiqmarket.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "az.rafiqmarket.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // Google Maps açarı APK-da olmalıdır (Google-un tələbi) – Cloud Console-da paket adı + SHA-1 ilə məhdudlaşdırın
        manifestPlaceholders["MAPS_API_KEY"] = prop("MAPS_API_KEY") ?: ""
        buildConfigField("boolean", "FIREBASE_CONFIGURED", hasGoogleServices.toString())
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Rafiq Market (test)")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            resValue("string", "app_name", "Rafiq Market")
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
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// Yekun fayl adı: RafiqMarket.apk (debug üçün RafiqMarket-debug.apk)
@Suppress("DEPRECATION")
android.applicationVariants.all {
    val variant = this
    outputs.all {
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
            if (variant.buildType.name == "release") "RafiqMarket.apk" else "RafiqMarket-${variant.buildType.name}.apk"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:customer"))
    implementation(project(":feature:courier"))
    implementation(project(":feature:admin"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
}
