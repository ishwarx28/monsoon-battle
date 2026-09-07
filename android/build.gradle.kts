plugins { id("com.android.application"); kotlin("android") }
val gdxVersion: String by project
val natives by configurations.creating
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewardId = "ca-app-pub-3940256099942544/5224354917"
val admobAppId = providers.gradleProperty("admobAppId").getOrElse(testAppId)
val rewardedAdUnit = providers.gradleProperty("rewardedAdUnit").getOrElse(testRewardId)
android {
    namespace = "com.ishwarx28.monsoonbattle"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ishwarx28.monsoonbattle"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "REWARDED_AD_UNIT", "\"$rewardedAdUnit\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/jniLibs"))
    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/*.kotlin_module")
        jniLibs.useLegacyPackaging = false
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "REWARDED_AD_UNIT", "\"$testRewardId\"")
        }
        getByName("release") { isMinifyEnabled = false }
    }
}
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    for (abi in listOf("arm64-v8a", "armeabi-v7a", "x86_64")) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-bullet-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}
val extractNatives by tasks.registering {
    inputs.files(natives)
    outputs.dir(layout.buildDirectory.dir("generated/jniLibs"))
    doLast {
        for (jar in natives.files) {
            val abi = listOf("arm64-v8a", "armeabi-v7a", "x86_64").first { jar.name.contains(it) }
            copy { from(zipTree(jar)); include("*.so"); into(layout.buildDirectory.dir("generated/jniLibs/$abi")) }
        }
    }
}
tasks.named("preBuild") { dependsOn(extractNatives) }

val validateProductionAds by tasks.registering {
    doLast {
        check(admobAppId != testAppId && rewardedAdUnit != testRewardId) {
            "Release requires -PadmobAppId=YOUR_APP_ID -PrewardedAdUnit=YOUR_REWARDED_UNIT, consent configuration and release signing."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateProductionAds) }
