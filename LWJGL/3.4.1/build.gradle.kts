import java.security.MessageDigest

plugins {
    java
}

fun writeVersion(file: File, inputs: List<File>) {
    val digest = MessageDigest.getInstance("SHA-1")
    inputs.forEach { input ->
        input.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
    }
    file.writeText(digest.digest().joinToString("") { "%02x".format(it) })
}

val lwjglVersion = "3.4.1"
group = "org.lwjgl.glfw"

configurations {
    create("lwjglModules") {
        isCanBeResolved = true
    }
}

// JSound (javax.sound -> OpenAL bridge) sources are shared by every lwjgl version; edit only the shared copy.
// Note: shared code must stay Java 8 compatible since 3.3.3 compiles with Java 8.
sourceSets {
    main {
        java {
            srcDir("../shared/src/main/java")
        }
        resources {
            srcDir("../shared/src/main/resources")
        }
    }
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "../compileOnly", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "libs/$lwjglVersion", "include" to listOf("*.jar"))))
    add(
        "lwjglModules",
        fileTree(mapOf("dir" to "libs/$lwjglVersion", "include" to listOf("*.jar")))
    )
    add("lwjglModules", project(":LWJGL:patches"))
    implementation(project(":LWJGL:patches"))
    implementation(libs.jspecify) // lwjgl3.3.3 has jsr305 included as a jar

}

tasks.jar {
    // Excluded modules are copied only by doLast and skip merging, so declare them explicitly as inputs,
    // otherwise updating those jars would misjudge UP-TO-DATE, leaving copy and version stale
    inputs.files(configurations["lwjglModules"])

    // Modules to copy over to the components directory instead of patching and merging
    val excludedModules = arrayOf(
        "lwjgl-lwjglx.jar",
        "lwjgl.jar",
        "jsr305.jar",
        "lwjgl-freetype.jar",
        "lwjgl-jemalloc.jar",
        "lwjgl-nanovg.jar",
        "lwjgl-openal.jar",
        "lwjgl-stb.jar",
        "lwjgl-tinyfd.jar",
        "lwjgl-shaderc.jar",
        "lwjgl-spvc.jar",
        "lwjgl-vma.jar",
        "lwjgl-vulkan.jar",
        "lwjgl-spng.jar"
    )

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("lwjgl-${lwjglVersion}-merged-modules")
    destinationDirectory.set(file("$rootDir/TurtleLauncher/src/main/assets/app_runtime/lwjgl/${lwjglVersion}"))

    from({
        // Ensure that the core lwjgl jar is processed first so duplicates in META-INF from other classes
        // are ignored. This avoids InvalidModuleDescriptorException due to say, using the module-info.class
        // from lwjgl-jemalloc.
        val includedModules = configurations["lwjglModules"].filter { dep ->
            !excludedModules.any { it == dep.name }
        }
        val coreJar = includedModules.find { it.name == "lwjgl.jar" }
        val jarList =
            if (coreJar != null) listOf(coreJar) + (includedModules - coreJar) else includedModules
        println("Merging LWJGL $lwjglVersion modules in the order: ")
        jarList.map {
            println(it.name)
            if (it.isDirectory) it else zipTree(it)
        }
    })

    // Makes the jar reproducible so the version file actually is a version file
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    val versionFile = File(destinationDirectory.get().asFile, "version")
    doLast {
        val excludedModulesFileList = excludedModules.flatMap { fileName ->
            configurations["lwjglModules"].filter { it.name == fileName }
        }
        copy {
            // Copy excluded modules to the lwjgl classes dir
            from(excludedModulesFileList)
            into(archiveFile.get().asFile.parentFile)
        }
        writeVersion(versionFile, listOf(archiveFile.get().asFile) + excludedModulesFileList)
    }
    // Adds the jank to outputs
    outputs.file(versionFile)
    outputs.files(excludedModules.map { path -> File(destinationDirectory.get().asFile, path) })
    exclude("net/java/openjdk/cacio/ctc/**")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}