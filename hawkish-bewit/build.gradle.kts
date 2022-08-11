plugins {
  kotlin("multiplatform")
  id("com.android.library")
  id("org.jetbrains.dokka")
  id("com.adarshr.test-logger") version "3.1.0"
  id("signing")
  id("maven-publish")
}

group = LibraryConstants.group
version = LibraryConstants.versionName

repositories {
  mavenCentral()
  maven("https://repo.repsy.io/mvn/chrynan/public")
}

kotlin {
  android {
    publishAllLibraryVariants()
  }

  targets {
    android {
      compilations.all {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
      }
    }
    jvm {
      compilations.all {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
      }
      testRuns["test"].executionTask.configure {
        useJUnitPlatform()
      }
    }
    js(BOTH) {
      browser {
        testTask {
          // failing tests on JS for now
          enabled = false
          useKarma {
            useChromeHeadless()
          }
        }
      }
//      nodejs()
    }
    ios()
    iosSimulatorArm64()
  }

  sourceSets {
    all {
      languageSettings {
        languageVersion = "1.7"
        progressiveMode = true
//        optIn("kotlin.contracts.ExperimentalContracts")
//        optIn("kotlin.time.ExperimentalTime")
//        optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
//        optIn("kotlinx.serialization.ExperimentalSerializationApi")
      }
    }
    val commonMain by getting {
      dependencies {
        implementation(kotlin("stdlib-jdk8"))
        implementation("com.eygraber:uri-kmp:0.0.6")

        implementation("com.squareup.okio:okio:3.2.0")
        implementation("io.matthewnelson.kotlin-components:encoding-base64:1.1.3")
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
      }
    }

    val jsMain by getting {
      dependencies {
        implementation("com.squareup.okio:okio-nodefilesystem:3.2.0")
      }
    }

    val jsTest by getting {
      dependencies {
        // tests on JS, workaround webpack > 5 not including node polyfills by default
        //  https://github.com/square/okio/issues/1163
        implementation(devNpm("node-polyfill-webpack-plugin", "^2.0.1"))
      }
    }

    val jvmMain by sourceSets.getting
    val jvmTest by getting {
      dependencies {
        api("org.junit.jupiter:junit-jupiter-api:5.8.2")
        implementation("com.mercateo:test-clock:1.0.2")
        implementation("io.strikt:strikt-core:0.33.0")
        runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation("com.squareup.okio:okio-fakefilesystem:3.2.0")
      }
    }

    val iosMain by sourceSets.getting
    val iosSimulatorArm64Main by sourceSets.getting
    iosSimulatorArm64Main.dependsOn(iosMain)
  }
}

android {
  compileSdk = LibraryConstants.Android.compileSdkVersion

  defaultConfig {
    minSdk = LibraryConstants.Android.minSdkVersion
    targetSdk = LibraryConstants.Android.targetSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    getByName("release") {
      proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_1_8.toString()
      // Opt-in to experimental compose APIs
      freeCompilerArgs = listOf(
        "-opt-in=kotlin.RequiresOptIn"
      )
    }
  }

  sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
  sourceSets["main"].java.srcDirs("src/androidMain/kotlin")
  sourceSets["main"].res.srcDirs("src/androidMain/res")

  sourceSets["test"].java.srcDirs("src/androidTest/kotlin")
  sourceSets["test"].res.srcDirs("src/androidTest/res")
}

tasks.withType<Jar> { duplicatesStrategy = DuplicatesStrategy.INHERIT }

//val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
//  dependsOn(dokkaHtml)
  archiveClassifier.set("javadoc")
//  from(dokkaHtml.outputDirectory)
}

publishing {
  publications.withType<MavenPublication> {
    artifact(javadocJar.get())
    pom {
      name.set(project.name)
      description.set("Signed URLs loosely using Hawk Bewits")
      url.set("https://github.com/innertech/hawk-bewit")
      licenses {
        license {
          name.set("The Apache License, Version 2.0")
          url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
      }
      developers {
        developer {
          id.set("rocketraman")
          name.set("Raman Gupta")
          email.set("rocketraman@gmail.com")
        }
      }
      scm {
        connection.set("scm:git:git@github.com:rocketraman/bootable.git")
        developerConnection.set("scm:git:ssh://github.com:rocketraman/bootable.git")
        url.set("https://github.com/innertech/hawk-bewit")
      }
    }
  }
  repositories {
    maven {
      name = "sonatype"
      url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2")
      credentials {
        username = project.findProperty("sonatypeUser") as? String
        password = project.findProperty("sonatypePassword") as? String
      }
    }
  }
}

signing {
  useGpgCmd()
  sign(publishing.publications)
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion))
  }
}

/*
tasks.withType<Test> {
  outputs.upToDateWhen { false }
}
*/

// https://stackoverflow.com/a/58624464/430128
val iosTest: Task by tasks.creating {
  val device = project.findProperty("iosDevice")?.toString() ?: "iPhone 8"
  val testExecutable = kotlin.targets.getByName<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>("iosX64").binaries.getTest("DEBUG")
  dependsOn(testExecutable.linkTaskName)
  group = JavaBasePlugin.VERIFICATION_GROUP
  description = "Runs tests for target 'ios' on an iOS simulator"

  doLast {
    exec {
      println(testExecutable.outputFile.absolutePath)
      commandLine( "xcrun", "simctl", "spawn", "--standalone", device, testExecutable.outputFile.absolutePath)
    }
  }
}

tasks.getByName("allTests").dependsOn(iosTest)
