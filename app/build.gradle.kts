import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME") ?: error("VERSION_NAME missing")

// versionCode = commit count on main. main's history is never rewritten, so it only ever goes up.
val appVersionCode: Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1).coerceAtLeast(1)

// Signing: environment variables win when present; otherwise the committed, non-secret CI key.
// Never replace keystore/ci-release.keystore: a different key forces an uninstall, which deletes the user's data.
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.emotionalleaves.sprout"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emotionalleaves.sprout"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Auto-update: where releases live, and an optional read-only key for this private repo. The key comes
        // only from the UPDATE_TOKEN repo secret (never committed). Without it, Sprout offers the Releases page.
        buildConfigField("String", "UPDATE_REPO", "\"EmotionalLeaves/Budget\"")
        val updateToken = (env("UPDATE_TOKEN") ?: "").filter { it.isLetterOrDigit() || it == '_' }
        buildConfigField("String", "UPDATE_TOKEN", "\"$updateToken\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(env("SIGNING_KEYSTORE_PATH") ?: rootProject.file("keystore/ci-release.keystore").path)
            storePassword = env("SIGNING_STORE_PASSWORD") ?: "sprout-ci"
            keyAlias = env("SIGNING_KEY_ALIAS") ?: "sprout"
            keyPassword = env("SIGNING_KEY_PASSWORD") ?: "sprout-ci"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("user.timezone", "America/Chicago") }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    sourceSets {
        // Room schema JSONs for MigrationTestHelper. Robolectric reads the debug variant's merged assets,
        // so they go into debug builds only (release APKs never contain them).
        getByName("debug").assets.srcDir("$projectDir/schemas")
        // Shared fake SimpleFIN fixtures live with the core tests.
        getByName("test").resources.srcDir("$rootDir/core/src/test/resources")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/previous-compilation-data.bin")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Robolectric normally downloads its Android jar from Maven Central while tests run, which can flake in CI.
// Resolve it through Gradle instead (cached by setup-gradle, retried) and run Robolectric offline.
val robolectricRuntime: Configuration by configurations.creating { isTransitive = false }

tasks.withType<Test>().configureEach {
    val jars = robolectricRuntime
    inputs.files(jars).withPropertyName("robolectricJars").withNormalizer(ClasspathNormalizer::class.java)
    doFirst {
        val dir = jars.singleFile.parentFile
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", dir.absolutePath)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// Coverage for the app's data layer (core has its own report). UI and generated code are left out.
kover {
    reports {
        filters {
            excludes {
                packages("com.emotionalleaves.sprout.ui")
                classes("*_Impl", "*_Impl\$*", "*.BuildConfig", "*.MainActivity*", "*.ComposableSingletons*", "*.SproutApp*", "*.AppContainer*")
            }
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    implementation(libs.paging.compose)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    robolectricRuntime(libs.robolectric.android.all)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
}
