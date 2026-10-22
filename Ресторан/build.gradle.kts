plugins {
    kotlin("jvm") version "2.4.10"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(24)
}

// ---- Кодировка при компиляции ----
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Убедитесь, что эти блоки присутствуют в build.gradle.kts
tasks.withType<JavaExec> {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}



// ---- Кодировка в тестах ----
tasks.test {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
}

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}