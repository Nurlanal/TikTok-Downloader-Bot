pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RafiqMarket"

// Tək APK: yalnız ":app" application moduludur, qalanları kitabxana modullarıdır.
include(":app")
include(":core")
include(":feature:auth")
include(":feature:customer")
include(":feature:courier")
include(":feature:admin")
