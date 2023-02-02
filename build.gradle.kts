group = LibraryConstants.group
version = LibraryConstants.versionName

buildscript {
  repositories {
    google()
    mavenCentral()
    maven("https://repo.repsy.io/mvn/chrynan/public")
  }
  dependencies {
    classpath("com.android.tools.build:gradle:7.3.1")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.20")
    classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.7.20")
  }
}

apply(plugin = "org.jetbrains.dokka")

allprojects {
  repositories {
    google()
    mavenCentral()
    maven("https://repo.repsy.io/mvn/chrynan/public")
  }
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
  rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().nodeVersion = "16.0.0"
}

// Documentation
tasks.named<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>("dokkaGfmMultiModule").configure {
  outputDirectory.set(file("${projectDir.path}/docs"))
}
