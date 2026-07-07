// Paper plugin adapter. paper-api and bluemap-api are NOT resolvable from the
// sandboxed dev environment — this module compiles in GitHub Actions CI (see CLAUDE.md).

plugins {
  alias(libs.plugins.minotaur)
}

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

// Minecraft 26.x servers run on Java 25; compiling this module needs JDK 25 (CI).
tasks.withType<JavaCompile>().configureEach {
  options.release = 25
}

tasks.processResources {
  filteringCharset = "UTF-8"
  // captured at configuration time: Task.project is deprecated at execution time (Gradle 10)
  val pluginVersion = project.version.toString()
  filesMatching("plugin.yml") {
    expand("version" to pluginVersion)
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

// Publishes the plugin jar to Modrinth; runs in CD (cd.yml) after the GitHub release.
modrinth {
  token.set(providers.environmentVariable("MODRINTH_TOKEN"))
  projectId.set("bluemap-structures-paper")
  versionNumber.set(project.version.toString())
  versionType.set("release")
  uploadFile.set(tasks.jar)
  gameVersions.addAll(libs.versions.mc.supported.get().split(","))
  loaders.addAll("paper", "purpur")
  changelog.set(
    providers.environmentVariable("MODRINTH_CHANGELOG")
      .orElse("See https://github.com/a24k/bluemap-structures-paper/releases")
  )
  dependencies {
    optional.project("bluemap")
  }
}
