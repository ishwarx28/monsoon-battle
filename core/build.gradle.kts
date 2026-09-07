plugins { kotlin("jvm"); `java-library` }
val gdxVersion: String by project
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
dependencies {
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("com.badlogicgames.gdx:gdx-bullet:$gdxVersion")
    api("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    testRuntimeOnly("com.badlogicgames.gdx:gdx-bullet-platform:$gdxVersion:natives-desktop")
}
tasks.test { testLogging { events("passed", "skipped", "failed") } }
