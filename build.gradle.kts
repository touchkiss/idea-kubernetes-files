import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.17.0"
}
group = "io.github.kubernetesfiles"
version = "0.1.4"
repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}
dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        local(providers.gradleProperty("localIdePath").getOrElse("/Applications/IntelliJ IDEA.app"))
        bundledPlugin("com.intellij.kubernetes")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "262"; untilBuild = "262.*" }
    }
}
tasks {
    runIde { args(projectDir.absolutePath) }
    buildSearchableOptions { enabled = false }
    named("prepareJarSearchableOptions") { enabled = false }
}

// Optional local license reuse for Gradle plugin 2.17, which lacks subscriptionKey.
// Store only the path in ~/.gradle/gradle.properties; never commit the key.
val sandboxIdeaKey = providers.gradleProperty("kubernetesFilesIdeaKey").orNull
if (sandboxIdeaKey != null) {
    tasks.named<PrepareSandboxTask>("prepareSandbox") {
        // Reapply after sandbox resets and changes to the external key.
        outputs.upToDateWhen { false }
        doFirst {
            require(File(sandboxIdeaKey).let { it.isFile && it.canRead() }) {
                "kubernetesFilesIdeaKey must point to a readable IDEA key file"
            }
        }
        doLast {
            val key = sandboxConfigDirectory.file("idea.key").get().asFile
            key.parentFile.mkdirs()
            Files.deleteIfExists(key.toPath())
            Files.createFile(
                key.toPath(),
                PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")
                )
            )
            File(sandboxIdeaKey).inputStream().use { source ->
                key.outputStream().use { target -> source.copyTo(target) }
            }
            val disabled = sandboxConfigDirectory.file("disabled_plugins.txt").get().asFile
            if (disabled.isFile) {
                disabled.writeText(disabled.readLines()
                    .filterNot { it.trim() == "com.intellij.modules.ultimate" }
                    .joinToString("\n"))
            }
        }
    }
}
