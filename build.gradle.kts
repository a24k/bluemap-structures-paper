plugins {
  java
}

allprojects {
  group = "dev.a24k.bluemapstructures"
  version = "0.1.0-SNAPSHOT"
}

subprojects {
  apply(plugin = "java")

  extensions.configure<JavaPluginExtension> {
    toolchain {
      languageVersion = JavaLanguageVersion.of(21)
    }
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
      events("passed", "skipped", "failed")
    }
  }
}
