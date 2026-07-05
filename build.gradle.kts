plugins {
  java
}

allprojects {
  group = "org.ykak.minecraft.bluemapstructurespaper"
  version = "0.3.0"
}

subprojects {
  apply(plugin = "java")

  // No toolchain pinning: the sandboxed dev environment only has JDK 21 (enough for
  // :core, release 21); CI runs everything on JDK 25 (required by :plugin / MC 26.x).
  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
      events("passed", "skipped", "failed")
    }
  }
}
