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
        // Only Robolectric's large Android jar may come from a local Maven cache (where Robolectric itself puts it).
        mavenLocal { content { includeModule("org.robolectric", "android-all-instrumented") } }
        google()
        mavenCentral()
    }
}

rootProject.name = "Sprout"
include(":core")
include(":app")
