plugins {
    java
}

group = "org.lwjgl.glfw"

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("lwjgl-glfw-classes")
    destinationDirectory.set(file("../TurtleLauncher/src/main/assets/components/lwjgl3/"))
    // Auto update the version with a timestamp so the project jar gets updated by Turtle
    doLast {
        val versionFile = file("../TurtleLauncher/src/main/assets/components/lwjgl3/version")
        versionFile.writeText(System.currentTimeMillis().toString())
    }
    from({
        configurations.getByName("runtimeClasspath").map {
            println(it.name)
            if (it.isDirectory) it else zipTree(it)
        }
    })
    exclude("net/java/openjdk/cacio/ctc/**")
    manifest {
        attributes("Manifest-Version" to "3.3.6")
        attributes("Automatic-Module-Name" to "org.lwjgl")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}

// Build only the ABI-independent guest bridge. Do not rebuild/replace the existing
// GLFW fat jar: it also contains manually maintained compatibility classes.
val compileFileDialogBridge by tasks.registering(JavaCompile::class) {
    source(fileTree("src/bridge/java") { include("**/*.java") })
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("classes/fileDialogBridge"))
    options.release.set(8)
}
val fileDialogBridgeJar by tasks.registering(Jar::class) {
    dependsOn(compileFileDialogBridge)
    archiveFileName.set("turtle-filedialogs.jar")
    destinationDirectory.set(file("../TurtleLauncher/src/main/assets/components/lwjgl3/"))
    from(compileFileDialogBridge.flatMap { it.destinationDirectory })
    doLast {
        file("../TurtleLauncher/src/main/assets/components/lwjgl3/version")
            .writeText(System.currentTimeMillis().toString())
    }
}
