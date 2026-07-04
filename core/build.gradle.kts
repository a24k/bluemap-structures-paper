// Pure-Java core: no Bukkit/Paper/BlueMap types allowed here.
// This module must stay buildable with Maven Central alone (see CLAUDE.md).

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}
