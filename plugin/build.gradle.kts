// Paper plugin adapter. paper-api and bluemap-api are NOT resolvable from the
// sandboxed dev environment — this module compiles in GitHub Actions CI (see CLAUDE.md).

repositories {
  mavenCentral()
  maven("https://repo.papermc.io/repository/maven-public/")
  maven("https://repo.bluecolored.de/releases")
}

dependencies {
  implementation(project(":core"))

  compileOnly(libs.paper.api)
  compileOnly(libs.bluemap.api)
  compileOnly(libs.gson) // bundled with Paper at runtime
}

tasks.processResources {
  filteringCharset = "UTF-8"
  filesMatching("plugin.yml") {
    expand("version" to project.version)
  }
}

tasks.jar {
  archiveBaseName = "BlueMapStructuresPaper"
  // Bundle :core (the only runtime dependency) into the plugin jar.
  from({
    configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
  })
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
