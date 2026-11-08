import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Explicitly selected local overrides are not part of the public build or source tree.
val localBuild = providers.gradleProperty("resonance.localBuildConfig").orNull?.let { path ->
    val configFile = rootProject.file(path).canonicalFile
    val root = rootProject.projectDir.canonicalFile.toPath()
    val local = rootProject.file("Local").canonicalFile.toPath()
    require(!configFile.toPath().startsWith(root) || configFile.toPath().startsWith(local)) {
        "Local build configuration must be outside the checkout or inside ignored Local/"
    }
    require(configFile.isFile) { "Selected local build configuration is missing" }
    Properties().apply { configFile.inputStream().use { load(it) } }
}
val supportedLocalKeys = setOf("applicationId", "storeFile", "storePasswordEnv", "keyAlias", "keyPasswordEnv")
require(localBuild == null || localBuild.stringPropertyNames().all { it in supportedLocalKeys }) {
    "Unknown local build setting"
}
val localApplicationId = localBuild?.getProperty("applicationId")?.trim()?.also {
    require(it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) {
        "Invalid local applicationId"
    }
}
val signingKeys = listOf("storeFile", "storePasswordEnv", "keyAlias", "keyPasswordEnv")
val signingValues = signingKeys.associateWith { localBuild?.getProperty(it)?.trim().orEmpty() }
val localSigning = signingKeys.any { localBuild?.containsKey(it) == true }
require(!localSigning || signingValues.values.all { it.isNotEmpty() }) {
    "Local signing requires storeFile, storePasswordEnv, keyAlias and keyPasswordEnv"
}
val signingSecrets = if (localSigning) {
    require(rootProject.file(signingValues.getValue("storeFile")).isFile) {
        "Local signing keystore is missing"
    }
    listOf("storePasswordEnv", "keyPasswordEnv").associateWith { key ->
        providers.environmentVariable(signingValues.getValue(key)).orNull?.takeIf { it.isNotEmpty() }
            ?: error("Required signing password environment variable is missing: $key")
    }
} else emptyMap()

val presentationProfile = providers.gradleProperty("resonance.profile")
    .orElse(providers.environmentVariable("RESONANCE_PROFILE"))
    .getOrElse("portable")
require(presentationProfile in setOf("bundled", "portable")) { "resonance.profile must be bundled or portable" }
val presentationSource = providers.gradleProperty("resonance.presentationAssets").getOrElse("src/main/assets")
require(presentationProfile != "portable" || presentationSource == "src/main/assets") {
    "Portable builds cannot include a custom presentation source"
}
val packagedAssets = layout.buildDirectory.dir("generated/presentationAssets/$presentationProfile")
val preparePresentationAssets by tasks.registering(Sync::class) {
    inputs.property("presentationProfile", presentationProfile)
    // Font notices are shared and retained even when no private artwork/data is packaged.
    from("src/main/assets") { include("licenses/**") }
    if (presentationProfile == "bundled") {
        from(presentationSource) { exclude("licenses/**") }
    }
    into(packagedAssets)
}
android {
    namespace = "dev.resonance"
    compileSdk = 37
    defaultConfig {
        applicationId = localApplicationId ?: "io.github.codingezio.resonance"
        minSdk = 29
        targetSdk = 37
        versionCode = 11
        versionName = "0.9.2"
        buildConfigField("String", "PRESENTATION_PROFILE", "\"$presentationProfile\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("main").assets.setSrcDirs(listOf(packagedAssets))
    val privateRelease = if (localSigning) signingConfigs.create("localRelease") {
        storeFile = rootProject.file(signingValues.getValue("storeFile"))
        storePassword = signingSecrets.getValue("storePasswordEnv")
        keyAlias = signingValues.getValue("keyAlias")
        keyPassword = signingSecrets.getValue("keyPasswordEnv")
    } else null
    buildTypes {
        debug { applicationIdSuffix = ".catalog" }
        release {
            if (privateRelease != null) signingConfig = privateRelease
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
tasks.named("preBuild").configure { dependsOn(preparePresentationAssets) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    // Expressive theme APIs are not public in the stable 1.4.0 artifact.
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-inspector:1.11.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
