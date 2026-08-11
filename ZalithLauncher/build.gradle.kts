import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import com.android.build.gradle.tasks.MergeSourceSetFolders

plugins {
    id("com.android.application")
    // No explicit version here: AGP 9's built-in-Kotlin machinery already puts a Kotlin
    // Gradle Plugin version on the buildscript classpath internally. Pinning a version
    // here collides with that ("already on the classpath with an unknown version") -
    // Gradle can't verify the two match, so it refuses. Letting this resolve to
    // whatever AGP 9.2.0 already loaded is also the combination Google actually tests.
    id("org.jetbrains.kotlin.android")
}
val getCFApiKey = {
    System.getenv("CURSEFORGE_API_KEY") ?: run {
        val curseforgeKeyFile = File(rootDir, "curseforge_key.txt")
        if (curseforgeKeyFile.canRead() && curseforgeKeyFile.isFile) {
            curseforgeKeyFile.readText()
        } else {
            logger.warn("BUILD: You have no CurseForge key, the curseforge api will get disabled !")
            "DUMMY"
        }
    }
}

// TurtleLauncher: AI Chat GitHub sync token, following the exact same env-var-first
// pattern as getCFApiKey above, but the local fallback is now an AES-256 ENCRYPTED
// file (github_chat_token.txt.enc) instead of plaintext - matching how a .jks
// keystore needs a password to actually be read, not just OS file permissions.
// Decrypted at build time via openssl using GITHUB_CHAT_TOKEN_PASSWORD, which you
// set yourself (export it in your shell profile, or Termux's ~/.bashrc) - that
// password is never written to any file in this repo, encrypted or not.
val getGithubChatSyncToken = {
    System.getenv("GITHUB_CHAT_SYNC_TOKEN") ?: run {
        val encryptedFile = File(rootDir, "github_chat_token.txt.enc")
        val password = System.getenv("GITHUB_CHAT_TOKEN_PASSWORD")
        if (encryptedFile.canRead() && encryptedFile.isFile && !password.isNullOrBlank()) {
            val process = ProcessBuilder(
                "openssl", "enc", "-aes-256-cbc", "-pbkdf2", "-iter", "100000", "-d",
                "-in", encryptedFile.absolutePath,
                "-pass", "env:GITHUB_CHAT_TOKEN_PASSWORD"
            ).redirectErrorStream(false).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) {
                output
            } else {
                logger.warn("BUILD: Failed to decrypt github_chat_token.txt.enc (wrong password?), AI Chat saving will get disabled !")
                ""
            }
        } else {
            logger.warn("BUILD: You have no GitHub chat-sync token (or no GITHUB_CHAT_TOKEN_PASSWORD set), AI Chat saving will get disabled !")
            ""
        }
    }
}

val getBuildType = {
    val buildType = System.getenv("ZL_BUILD_TYPE") ?: "DEBUG"
    logger.warn("BUILD: Build Type --> $buildType")
    buildType
}

val nameId = "com.endiq.turtlelauncher"
// The actual Kotlin/Java source tree still lives under com.movtery.zalithlauncher.* -
// namespace controls where the generated R/BuildConfig classes land, and must match
// that source package or every implicit "R" reference and "import ...BuildConfig"
// across the codebase breaks. applicationId (the public app ID / branding) is free
// to differ from namespace, which is how we keep the com.endiq.turtlelauncher identity
// without renaming every package declaration in the project.
val namespaceId = "com.movtery.zalithlauncher"
val generatedZalithDir = file("$buildDir/generated/source/zalith/java")
val launcherAPPName = project.findProperty("launcher_app_name") as? String ?: error("The \"launcher_app_name\" property is not set in gradle.properties.")
val launcherName = project.findProperty("launcher_name") as? String ?: error("The \"launcher_name\" property is not set in gradle.properties.")
val launcherVersionCode = (project.findProperty("launcher_version_code") as? String)?.toIntOrNull() ?: error("The \"launcher_version_code\" property is not set as an integer in gradle.properties.")
val launcherVersionName = project.findProperty("launcher_version_name") as? String ?: error("The \"launcher_version_name\" property is not set in gradle.properties.")

configurations {
    create("instrumentedClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
}

android {
    namespace = namespaceId
    // TurtleLauncher: bumped so Android 15 (API 35) APIs - WindowInsetsControllerCompat edge-
    // to-edge, predictive back, etc. (see Tools#setFullscreen and the manifest's
    // enableOnBackInvokedCallback) - are actually on the compile classpath. Deliberately NOT
    // bumping targetSdk past 34 in the same change: targeting 35 makes edge-to-edge mandatory
    // (Window#setDecorFitsSystemWindows(true) becomes a no-op) and predictive back default-on
    // app-wide, both instant behavior changes across every one of this app's ~20 activities/
    // fragments with zero opportunity to test each one on real Android 15 hardware first. The
    // code above already opts in to both explicitly and per-activity where needed, so once
    // that's been verified on-device, bumping targetSdk to 35 here should be close to a no-op.
    // Separately: apps targeting API 35 must also ship 16 KB-page-aligned native libraries for
    // Play Store submission - this project's numerous prebuilt jniLibs .so files (renderers,
    // LWJGL natives, libpojavexec.so, etc.) would need to be confirmed/rebuilt 16 KB-aligned
    // before that targetSdk bump, independent of anything fixable from Kotlin/Java source here.
    compileSdk = 35

    signingConfigs {
        create("releaseBuild") {
            val pwd = System.getenv("MOVTERY_KEYSTORE_PASSWORD") ?: ""
            storeFile = file("movtery-key.jks")
            storePassword = pwd
            keyAlias = "mtp"
            keyPassword = pwd
        }
        create("customDebug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = nameId
        minSdk = 26
        targetSdk = 34
        versionCode = launcherVersionCode
        versionName = launcherVersionName
        multiDexEnabled = true //important
        manifestPlaceholders["launcher_name"] = launcherAPPName
    }

    buildTypes {
        val storageProviderId = "$nameId.storage_provider"

        getByName("debug") {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("customDebug")
            resValue("string", "storageProviderAuthorities", "$storageProviderId.debug")
        }
        create("proguard") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
        }
        create("proguardNoDebug") {
            initWith(getByName("proguard"))
            isDebuggable = false
        }
        getByName("release") {
            // Don't set to true or java.awt will be a.a or something similar.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "storageProviderAuthorities", storageProviderId)
            signingConfig = signingConfigs.getByName("releaseBuild")
        }
    }

    sourceSets["main"].java.srcDirs(generatedZalithDir)

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                    val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
                    afterEvaluate {
                        val task = tasks.named("merge${variantName}Assets").get() as MergeSourceSetFolders
                        task.doLast {
                            val arch = System.getProperty("arch", "all")
                            val assetsDir = task.outputDir.get().asFile
                            val jreList = listOf("jre-8", "jre-17", "jre-21", "jre-25")
                            println("arch:$arch")
                            jreList.forEach { jreVersion ->
                                val runtimeDir = File("$assetsDir/components/$jreVersion")
                                println("runtimeDir:${runtimeDir.absolutePath}")
                                runtimeDir.listFiles()?.forEach {
                                    if (arch != "all" && it.name != "version" && !it.name.contains("universal") && it.name != "bin-${arch}.tar.xz") {
                                        println("delete:${it} : ${it.delete()}")
                                    }
                                }
                            }
                        }
                    }

                    (output.getFilter(ABI)?.identifier ?: "all").let { abi ->
                        val baseName = "$launcherName-${if (variant.buildType == "release") defaultConfig.versionName else "Debug-${defaultConfig.versionName}"}"
                        output.outputFileName = if (abi == "all") "$baseName.apk" else "$baseName-$abi.apk"
                    }
                }
            }
        }
    }

    splits {
        val arch = System.getProperty("arch", "all")
        if (arch != "all") {
            abi {
                isEnable = true
                reset()
                when (arch) {
                    "arm" -> include("armeabi-v7a")
                    "arm64" -> include("arm64-v8a")
                    "x86" -> include("x86")
                    "x86_64" -> include("x86_64")
                }
            }
        }
    }

    // ndkVersion = "25.2.9519653"

    // externalNativeBuild {
    //     ndkBuild {
    //         path = file("src/main/jni/Android.mk")
    //     }
    // }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf("**/libbytehook.so")
        }
    }

    buildFeatures {
        prefab = true
        buildConfig = true
        viewBinding = true
        resValues = true
    }

    // Kept in step with compileSdk = 35 above - AGP will happily resolve build-tools 34.0.0
    // against a 35 compileSdk in most cases, but pinning the matching version avoids relying
    // on that fallback for aapt2/d8 behavior around any new API-35 resource qualifiers.
    buildToolsVersion = "35.0.0"
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

// TurtleLauncher: this must be an object, not a plain top-level fun. A `fun` declared
// directly in a .kts script compiles as a member of the script's own generated class,
// so calling it from inside doLast (execution time) still captures a reference to that
// script/Project instance to dispatch the call - which is exactly what the configuration
// cache's "cannot serialize Gradle script object references" error is about, even though
// the function body itself never touches Project. A top-level object is a genuinely
// independent singleton with no implicit outer/script reference, so calling a method on
// it doesn't drag the script instance into the task's serialized state.
object InfoDistributorGenerator {
    fun generate(sourceOutputDir: File, packageName: String, className: String, constantMap: Map<String, String>) {
        val outputDir = File(sourceOutputDir, packageName.replace(".", "/"))
        outputDir.mkdirs()
        val javaFile = File(outputDir, "$className.java")
        val constants = constantMap.entries.joinToString("\n") { (key, value) ->
            "\tpublic static final String $key = \"$value\";"
        }
        javaFile.writeText(
            """
            |/**
            | * Automatically generated file. DO NOT MODIFY
            | */
            |package $packageName;
            |
            |public class $className {
            |$constants
            |}
            """.trimMargin()
        )
        println("Generated Java file: ${javaFile.absolutePath}")
    }
}

tasks.register("generateInfoDistributor") {
    // Gradle's configuration cache forbids touching Project (via project.property(...),
    // or the getCFApiKey()/getBuildType() lambdas above, which close over this script's
    // implicit Project receiver) from inside doLast - that's execution time, after the
    // cache would have already been written. Resolve every Project-dependent value here
    // in the task's configuration block instead, into plain local vals - doLast below
    // then only touches those (already just Strings/a File) and the independent
    // InfoDistributorGenerator object above, neither of which reference Project or the
    // script instance at all.
    val curseforgeApiKey = getCFApiKey()
    val githubChatSyncToken = getGithubChatSyncToken()
    val launcherName = project.property("launcher_name").toString()
    val appName = project.property("launcher_app_name").toString()
    val buildType = getBuildType()
    val outputDir = generatedZalithDir

    doLast {
        val constantMap = mapOf(
            "CURSEFORGE_API_KEY" to curseforgeApiKey,
            "GITHUB_CHAT_SYNC_TOKEN" to githubChatSyncToken,
            "LAUNCHER_NAME" to launcherName,
            "APP_NAME" to appName,
            "BUILD_TYPE" to buildType
        )
        InfoDistributorGenerator.generate(outputDir, "com.movtery.zalithlauncher", "InfoDistributor", constantMap)
    }
}

tasks.named("preBuild") {
    dependsOn("generateInfoDistributor")
}

dependencies {
    // TurtleLauncher CRASH FIX (MC 26.3+ SDL): official org.libsdl.app.* classes
    // from SDL 3.4.12's own Android AAR (libs/sdl3-android-classes.jar), needed
    // to call SDL.setContext()+SDL.setupJNI() from the app's own real Activity
    // before launching the game - see SdlAndroidJniPrep for why. A real compile-
    // time dependency (not reflection) since this jar is bundled directly.
    implementation(files("libs/sdl3-android-classes.jar"))
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("commons-codec:commons-codec:1.17.1")
    // implementation("com.wu-man:android-bsf-api:3.1.3")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0-beta01")
    implementation("androidx.annotation:annotation:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.13.0")
    // TurtleLauncher: declarative, dependency-ordered app startup for the pieces of
    // PojavApplication.onCreate() that don't need to block everything else (ANR watchdog,
    // dynamic color theming) - see TurtleStartupInitializer.
    implementation("androidx.startup:startup-runtime:1.1.1")
    // TurtleLauncher: android.os.Trace wrapper that safely no-ops below API 18 instead of
    // crashing - used to mark hot sections (asset prefetch, launch) so they show up when
    // profiling with Perfetto/Android Studio Profiler, without adding a hard dependency on
    // any particular profiler.
    implementation("androidx.tracing:tracing:1.2.0")
    // TurtleLauncher: installs the Baseline Profile below at app-install/first-run time so
    // ART can AOT-compile the app's known hot paths up front instead of interpreting/JIT-ing
    // them cold - biggest relative win on exactly the low-end devices where JIT warm-up is
    // most expensive. See src/main/baseline-prof.txt.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("com.github.duanhong169:checkerboarddrawable:1.0.2")
    implementation("com.github.PojavLauncherTeam:portrait-sdp:ed33e89cbc")
    implementation("com.github.PojavLauncherTeam:portrait-ssp:6c02fd739b")
    implementation("com.github.Mathias-Boulay:ExtendedView:1.0.0")
    implementation("com.github.Mathias-Boulay:android_gamepad_remapper:2.0.3")
    implementation("com.github.Mathias-Boulay:virtual-joystick-android:1.14")
    implementation("com.github.skydoves:powerspinner:1.2.7")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.github.angcyo.DslTablayout:TabLayout:3.6.5")

    implementation("top.fifthlight.touchcontroller:proxy-client-android:0.0.2")

    // implementation("com.intuit.sdp:sdp-android:1.0.5")
    // implementation("com.intuit.ssp:ssp-android:1.0.5")

    implementation("org.tukaani:xz:1.9")

    // Faster launcher-cache I/O (Zstandard compression, see CacheCompression.kt). The @aar
    // classifier is required on Android per the library's own docs (implementation("...@aar"),
    // testImplementation("...") without it) - it's what bundles the actual Android .so files
    // (jni/<abi>/libzstd-jni-*.so) via AGP's native-lib merging. The previous declaration here
    // was missing @aar (would only have carried desktop/server natives) and the comment above
    // it claimed a native lib was manually vendored into jniLibs/arm64-v8a as a workaround -
    // no such file exists anywhere in the project, so this was silently broken from the start.
    // Never caught because nothing had actually called into it until now.
    implementation("com.github.luben:zstd-jni:1.5.7-6@aar")
    // Fast/lightweight compression for small, frequently-rewritten launcher temp files (see
    // CacheCompression.kt). io.maryk.lz4:lz4-android is a real Android port with its own
    // bundled native libs (confirmed via its GitHub releases/Maven Central listing before
    // adding this) - the original lz4-java only ships desktop/server natives, no Android
    // build exists for it (checked and stayed blocked on that one).
    implementation("io.maryk.lz4:lz4-android:1.10.0")
    // Our version of exp4j can be built from source at
    // https://github.com/PojavLauncherTeam/exp4j
    implementation("net.sourceforge.htmlcleaner:htmlcleaner:2.6.1")
    implementation("com.bytedance:bytehook:1.0.10")

    // implementation("net.sourceforge.streamsupport:streamsupport-cfuture:1.7.0")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.commonmark:commonmark:0.19.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    implementation("com.getkeepsafe.taptargetview:taptargetview:1.14.0")
    implementation("io.github.petterpx:floatingx:2.3.3")
    implementation("org.greenrobot:eventbus:3.3.1")
    implementation("com.moandjiezana.toml:toml4j:0.7.2") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
}
