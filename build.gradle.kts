import org.gradle.api.publish.PublishingExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.allopen) apply false
    alias(libs.plugins.kotlinxBenchmark) apply false
    alias(libs.plugins.mavenPublish) apply false
}

version = "1.7.2-java11.2-SNAPSHOT"

allprojects {
    group = "top.e404.latex"
    version = rootProject.version
}

subprojects {
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "nexus"
                    url = uri("https://nexus.e404.top:3443/repository/maven-snapshots/")
                    credentials {
                        username = providers.gradleProperty("nexus.username").orNull
                        password = providers.gradleProperty("nexus.password").orNull
                    }
                }
            }
        }
    }
}
