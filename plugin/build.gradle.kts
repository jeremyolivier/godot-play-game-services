import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val pluginName = "GodotPlayGameServices"

val pluginPackageName = "com.jacobibanez.plugin.android.godotplaygameservices"

android {
    namespace = pluginPackageName
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21

        manifestPlaceholders["godotPluginName"] = pluginName
        manifestPlaceholders["godotPluginPackageName"] = pluginPackageName
        buildConfigField("String", "GODOT_PLUGIN_NAME", "\"${pluginName}\"")
        setProperty("archivesBaseName", pluginName)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.android.gms:play-services-games-v2:21.0.0")
    implementation("org.godotengine:godot:4.5.1.stable")
    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))

    // Add the dependency for the Firebase Authentication library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-auth")

    // Also add the dependency for the Google Play services library and specify its version
    implementation("com.google.android.gms:play-services-auth:21.5.1")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    dokkaPlugin("org.jetbrains.dokka:android-documentation-plugin:1.9.10")
}

// BUILD TASKS DEFINITION
val copyDebugAARToDemoAddons by tasks.registering(Copy::class) {
    description = "Copies the generated debug AAR binary to the plugin's addons directory"
    from("build/outputs/aar")
    include("$pluginName-debug.aar")
    into("demo/addons/$pluginName/bin/debug")
}

val copyReleaseAARToDemoAddons by tasks.registering(Copy::class) {
    description = "Copies the generated release AAR binary to the plugin's addons directory"
    from("build/outputs/aar")
    include("$pluginName-release.aar")
    into("demo/addons/$pluginName/bin/release")
}

val cleanDemoAddons by tasks.registering(Delete::class) {
    delete("demo/addons/$pluginName")
}

val copyAddonsToDemo by tasks.registering(Copy::class) {
    description = "Copies the export scripts templates to the plugin's addons directory"

    dependsOn(cleanDemoAddons)
    finalizedBy(copyDebugAARToDemoAddons)
    finalizedBy(copyReleaseAARToDemoAddons)

    from("export_scripts_template")
    into("demo/addons/$pluginName")
}

tasks.named("assemble").configure {
    finalizedBy(copyAddonsToDemo)
}

tasks.named<Delete>("clean").apply {
    dependsOn(cleanDemoAddons)
}

tasks.dokkaHtmlPartial {
    moduleName.set("Android Plugin")
    suppressObviousFunctions.set(true)
    suppressInheritedMembers.set(true)

    dokkaSourceSets.configureEach {
        perPackageOption {
            matchingRegex.set(".*signin|.*utils|.*snapshots|.*games|.*events")
            suppress.set(true)
        }
    }
}
