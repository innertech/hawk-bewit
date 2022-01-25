plugins {
  kotlin("jvm") version "1.6.10"
  id("org.jetbrains.dokka") version "1.6.10"
  signing
  `maven-publish`
}

group = "tech.inner"
version = "1.0"

repositories {
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))

  testApi("org.junit.jupiter:junit-jupiter-api:5.8.2")
  testImplementation("com.mercateo:test-clock:1.0.2")
  testImplementation("io.strikt:strikt-core:0.33.0")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
  dependsOn(dokkaHtml)
  archiveClassifier.set("javadoc")
  from(dokkaHtml.outputDirectory)
}

java {
  withSourcesJar()
}

tasks.named<Test>("test") {
  useJUnitPlatform()
}

publishing {
  publications {
    create<MavenPublication>("mavenCentral") {
      artifact(javadocJar)
      from(components["java"])
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
  }
  repositories {
    maven {
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
  sign(publishing.publications["mavenCentral"])
}
