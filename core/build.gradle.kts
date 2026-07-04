// Pure-Java core: no Bukkit/Paper/BlueMap types allowed here.
// This module must stay buildable with Maven Central alone (see CLAUDE.md).

repositories {
  mavenCentral()
}

// Java 21 bytecode: keeps the fast local test loop alive on the sandbox's JDK 21
// (JDK 25 in CI compiles it identically via --release).
tasks.withType<JavaCompile>().configureEach {
  options.release = 21
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}
