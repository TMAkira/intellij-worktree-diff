import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugin("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    // `./gradlew runIde -PsmokeProject=<path>` opens that project in the sandbox IDE with no
    // first-run dialogs, and the plugin walks through its own surfaces (see Startup.smoke).
    runIde {
        providers.gradleProperty("smokeProject").orNull?.let { path ->
            args(path)
            systemProperty("worktreeDiff.smoke", "true")
            systemProperty("idea.trust.all.projects", "true")
            systemProperty("jb.consents.confirmation.enabled", "false")
            systemProperty("idea.initially.ask.config", "never")
            systemProperty("ide.show.tips.on.startup", "false")
        }
    }
}
