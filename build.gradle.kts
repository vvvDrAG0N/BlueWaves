import java.io.File
import org.gradle.api.tasks.testing.Test

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}

val checkKotlinFileLineLimit by tasks.registering {
    group = "verification"
    description = "Fails when any Kotlin source file exceeds 500 lines."

    doLast {
        val sourceRoots = listOf("/src/main/", "/src/test/", "/src/androidTest/")
        val offenders = rootDir
            .walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.contains("/build/") }
            .filter { file -> sourceRoots.any { root -> file.invariantSeparatorsPath.contains(root) } }
            .mapNotNull { file ->
                val lines = file.useLines { it.count() }
                if (lines > 500) {
                    "${file.relativeTo(rootDir).invariantSeparatorsPath}: $lines lines"
                } else {
                    null
                }
            }
            .toList()

        check(offenders.isEmpty()) {
            buildString {
                appendLine("Kotlin file line limit exceeded (500).")
                offenders.forEach(::appendLine)
            }
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("checkKotlinFileLineLimit"))
    }

    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests = false
    }
}
