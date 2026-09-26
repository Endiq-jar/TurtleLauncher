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

val lwjglVersion = "3.3.3"
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
    // jsr305 is a compile-time-only annotation dependency (@Nullable etc.) and must not enter runtime assets,
    // or its same-named package clashes with the JDK built-in java.annotation module and causes ResolutionException
    val lwjglModules = fileTree("libs/$lwjglVersion") {
        include("*.jar")
        exclude("jsr305.jar")
    }
    add("lwjglModules", lwjglModules)
    add("lwjglModules", project(":LWJGL:patches"))
    implementation(project(":LWJGL:patches"))
}

tasks.jar {
    // Excluded modules are copied only by doLast and skip merging, so declare them explicitly as inputs,
    // otherwise updating those jars would misjudge UP-TO-DATE, leaving copy and version stale
    inputs.files(configurations["lwjglModules"])

    // Modules to copy over to the components directory instead of patching and merging
    val excludedModules = arrayOf(
        "lwjgl.jar",
        "lwjgl-freetype.jar",
//            "lwjgl-glfw.jar",
        "lwjgl-lwjglx.jar",
        "lwjgl-jemalloc.jar",
        "lwjgl-nanovg.jar",
        "lwjgl-openal.jar",
//            "lwjgl-opengl.jar",
        "lwjgl-sdl.jar",
        "lwjgl-shaderc.jar",
        "lwjgl-spng.jar",
        "lwjgl-spvc.jar",
        "lwjgl-stb.jar",
        "lwjgl-tinyfd.jar",
        "lwjgl-vma.jar",
        "lwjgl-vulkan.jar"
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
    outputs.file(versionFile)
    outputs.files(excludedModules.map { path -> File(destinationDirectory.get().asFile, path) })
    exclude("net/java/openjdk/cacio/ctc/**")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}