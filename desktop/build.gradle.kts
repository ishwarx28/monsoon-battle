plugins { kotlin("jvm"); application }
val gdxVersion: String by project
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    runtimeOnly("com.badlogicgames.gdx:gdx-bullet-platform:$gdxVersion:natives-desktop")
    runtimeOnly("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}
application {
    mainClass.set("com.ishwarx28.monsoonbattle.desktop.DesktopLauncherKt")
    if (System.getProperty("os.name").contains("Mac", true)) applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
}

tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
