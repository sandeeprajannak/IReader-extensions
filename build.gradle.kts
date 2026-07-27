/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    classpath(libs.android.gradle)
    classpath(libs.gradle)
    classpath(libs.serialization.gradle)
  }
}
tasks.register("delete", Delete::class) {
  delete(rootProject.buildDir)
}
tasks.create<RepoTask>("repo")

// Regenerates the jar sideload artifact for a single source's already-built release APK.
// Used by scripts/publish_source.py so a single-source fix doesn't require running the
// full multi-module `repo` task (which rebuilds and re-indexes every extension).
// Usage: ./gradlew regenerateSourceJar -PsourceApk=<path> -PsourceJar=<path>
tasks.register("regenerateSourceJar") {
  doLast {
    val apkPath = project.findProperty("sourceApk") as String?
        ?: error("regenerateSourceJar requires -PsourceApk=<path>")
    val jarPath = project.findProperty("sourceJar") as String?
        ?: error("regenerateSourceJar requires -PsourceJar=<path>")
    val apk = file(apkPath)
    val jar = file(jarPath)
    RepoTask.dex2jar(apk, jar, apk.name)
  }
}

// Source ID management tasks
registerSourceIdTasks()

// JS build tasks for iOS support
// The js-sources module compiles all enabled sources to JS for iOS
tasks.register("buildJsSources") {
  group = "build"
  description = "Build JS bundles for iOS (uses js-sources module)"
  dependsOn(":js-sources:createSourceIndex")
}

// Make repo task depend on JS sources
tasks.named<RepoTask>("repo") {
  dependsOn(":js-sources:createSourceIndex")
}

// ═══════════════════════════════════════════════════════════════════════════════
// 🧪 TEST SERVER TASKS - Easy commands for source testing
// ═══════════════════════════════════════════════════════════════════════════════

tasks.register("testServer") {
  group = "testing"
  description = "🧪 Start the source test server at http://localhost:8080"
  dependsOn(":source-test-server:run")
}

tasks.register("buildAndTest") {
  group = "testing"
  description = "🚀 Build all sources and start test server"
  dependsOn("assembleDebug", ":source-test-server:run")
  tasks.findByPath(":source-test-server:run")?.mustRunAfter("assembleDebug")
}

tasks.register("quickTest") {
  group = "testing"
  description = "⚡ Start test server without rebuilding (uses cached APKs)"
  dependsOn(":source-test-server:run")
}

// ═══════════════════════════════════════════════════════════════════════════════
// 📖 HELP TASKS - Show available commands and sources
// ═══════════════════════════════════════════════════════════════════════════════

tasks.register("buildSourceHelp") {
  group = "help"
  description = "📖 Show how to build individual sources"
  doLast {
    println("""
      |
      |╔══════════════════════════════════════════════════════════════════════════════╗
      |║                    🔨 Building Individual Sources                            ║
      |╠══════════════════════════════════════════════════════════════════════════════╣
      |║                                                                              ║
      |║  To build a single source, use the full Gradle path:                         ║
      |║                                                                              ║
      |║  Individual sources (sources/{lang}/{name}):                                 ║
      |║    ./gradlew :extensions:individual:{lang}:{name}:assembleDebug              ║
      |║                                                                              ║
      |║  Examples:                                                                   ║
      |║    ./gradlew :extensions:individual:en:freewebnovel:assembleDebug            ║
      |║    ./gradlew :extensions:individual:en:royalroad:assembleDebug               ║
      |║    ./gradlew :extensions:individual:en:novelfull:assembleDebug               ║
      |║    ./gradlew :extensions:individual:ar:novelarab:assembleDebug               ║
      |║                                                                              ║
      |║  Multisrc sources (sources/multisrc/{name}):                                 ║
      |║    ./gradlew :extensions:multisrc:{name}:assembleDebug                       ║
      |║                                                                              ║
      |║  After building, start the test server:                                      ║
      |║    ./gradlew testServer                                                      ║
      |║                                                                              ║
      |╚══════════════════════════════════════════════════════════════════════════════╝
      |
    """.trimMargin())
  }
}

tasks.register("listSources") {
  group = "help"
  description = "📋 List all available sources with build commands"
  
  val sourcesDir = rootProject.file("sources")
  val multisrcDir = rootProject.file("sources/multisrc")
  
  doLast {
    println("\n📋 Available Sources:\n")
    
    sourcesDir.listFiles()?.filter { it.isDirectory && it.name != "multisrc" }?.sortedBy { it.name }?.forEach { langDir ->
      val sources = langDir.listFiles()?.filter { it.isDirectory && it.name != "main" && File(it, "build.gradle.kts").exists() }?.sortedBy { it.name }
      if (!sources.isNullOrEmpty()) {
        println("  ${langDir.name}/ (${sources.size} sources)")
        sources.forEach { source ->
          println("    └─ ${source.name}")
          println("       ./gradlew :extensions:individual:${langDir.name}:${source.name}:assembleDebug")
        }
        println()
      }
    }
    
    val multisrc = multisrcDir.listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }?.sortedBy { it.name }
    if (!multisrc.isNullOrEmpty()) {
      println("  multisrc/ (${multisrc.size} themes)")
      multisrc.forEach { source ->
        println("    └─ ${source.name}")
        println("       ./gradlew :extensions:multisrc:${source.name}:assembleDebug")
      }
    }
  }
}

// Print helpful info when project is loaded
gradle.projectsEvaluated {
  println("""
    |
    |╔══════════════════════════════════════════════════════════════════════════════╗
    |║                 🧪 IReader Extensions - Quick Commands                       ║
    |╠══════════════════════════════════════════════════════════════════════════════╣
    |║                                                                              ║
    |║  TEST SERVER:                                                                ║
    |║    ./gradlew testServer        → Start test server (port 8080)               ║
    |║    ./gradlew buildAndTest      → Build all + start server                    ║
    |║                                                                              ║
    |║  BUILD SINGLE SOURCE:                                                        ║
    |║    ./gradlew :extensions:individual:en:freewebnovel:assembleDebug            ║
    |║    ./gradlew :extensions:individual:en:royalroad:assembleDebug               ║
    |║                                                                              ║
    |║  HELP:                                                                       ║
    |║    ./gradlew listSources       → List all sources with commands              ║
    |║    ./gradlew buildSourceHelp   → Show build command format                   ║
    |║                                                                              ║
    |║  URLs: http://localhost:8080  |  http://localhost:8080/browse                ║
    |║                                                                              ║
    |╚══════════════════════════════════════════════════════════════════════════════╝
    |
  """.trimMargin())
}
