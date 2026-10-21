import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val targetAbis = (project.findProperty("targetAbi") as String?)
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val releaseKeystore = project.findProperty("aetheryKeystore") as String?

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.msnguard.vpn"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.msnguard.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = 386
        versionName = "3.1.0-shard-only"

    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*targetAbis.toTypedArray())
            isUniversalApk = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // Fonts deflate well (~30-40%); letting AAPT compress them keeps the
        // arm64 APK under Telegram's 50 MB document cap while load cost stays
        // negligible (ResourcesCompat caches the Typeface).
        // noCompress is intentionally NOT set for ttf.
    }

    packaging {
        jniLibs {
            // SHARD-only distribution: these sidecars are used only by the
            // hidden Tor/AnyTLS/Psiphon transports and are not needed by SHARD.
            // Excluding them from packaging saves substantial space per ABI.
            excludes += setOf(
                "**/libtor.so",
                "**/libobfs4proxy.so",
                "**/libanytls.so",
                "**/libgojni.so",
            )
            // Legacy packaging is retained for executable sidecars. Android 10+
            // restricts executing app-private writable copies; nativeLibraryDir
            // is the supported read-only location for the SHARD sidecar binaries.
            // The compressed APK may be smaller, while installed size includes
            // the extracted native libraries.
            useLegacyPackaging = true
        }
        resources {
            // Netty jars each ship META-INF/INDEX.LIST + version properties +
            // license notices — ten identical entries once every module is on
            // the classpath, which mergeReleaseJavaResource refuses outright
            // (FoXy transport bring-in, only these resource names).
            pickFirsts += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.txt",
                "META-INF/LICENSE.txt",
            )
        }
    }

    if (releaseKeystore != null) {
        val envProps = Properties().apply {
            val envFile = rootProject.file("keystore.env")
            if (envFile.exists()) {
                envFile.inputStream().use { load(it) }
            }
        }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseKeystore)
                storePassword = System.getenv("AETHERY_KEYSTORE_PASSWORD")
                    ?: envProps.getProperty("storePassword")
                keyAlias = System.getenv("AETHERY_KEY_ALIAS")
                    ?: envProps.getProperty("keyAlias")
                keyPassword = System.getenv("AETHERY_KEY_PASSWORD")
                    ?: envProps.getProperty("keyPassword")
                // Anti-malware engines (Avast in particular) unpack an APK as a
                // JAR to scan it, and a JAR without a v1 signature cannot be
                // verified at all — that surfaces to the user as "Suspicious".
                // v2 alone is valid for an Android install, so this only ever
                // mattered to scanners. Sign with both: v1 for the JAR view,
                // v2 for Android's own install integrity check.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
        buildTypes.named("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
        }
    }
}

    dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    // Foxy transport (isolated package; nothing existing references these):
    // Firefox-account auth + control plane (OkHttp + EncryptedSharedPreferences),
    // H2 CONNECT upstream (netti modules), ALPN h2 on API 26-28 (conscrypt).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.netty:netty-handler:4.1.115.Final")
    implementation("io.netty:netty-codec-http2:4.1.115.Final")
    implementation("io.netty:netty-transport:4.1.115.Final")
    implementation("io.netty:netty-handler-proxy:4.1.115.Final")
    implementation("org.conscrypt:conscrypt-android:2.5.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}

targetAbis.forEach { abi ->
    val taskName = "buildRustCore${abi.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }}"
    tasks.register<Exec>(taskName) {
        group = "build"
        description = "Builds Aether for Android $abi"
        val buildScript = if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            rootProject.file("core/build-android.ps1")
        } else {
            rootProject.file("core/build-android.sh")
        }
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            commandLine(
                "powershell.exe",
                "-ExecutionPolicy", "Bypass",
                "-File", buildScript.absolutePath,
                "-Abi", abi,
            )
        } else {
            commandLine("bash", buildScript.absolutePath, "--abi", abi)
        }
        environment("ANDROID_HOME", android.sdkDirectory.absolutePath)
        environment("ANDROID_SDK_ROOT", android.sdkDirectory.absolutePath)
        environment("ANDROID_NDK_HOME", "${android.sdkDirectory.absolutePath}/ndk/26.3.11579264")
        environment("ANDROID_NDK_ROOT", "${android.sdkDirectory.absolutePath}/ndk/26.3.11579264")
        inputs.dir(rootProject.file("core/aether/src"))
        inputs.file(rootProject.file("core/aether/Cargo.toml"))
        inputs.dir(rootProject.file("core/quiche"))
        inputs.file(buildScript)
        val output = file("src/main/jniLibs/$abi/libaether.so")
        outputs.file(output)
    }
    tasks.named("preBuild").configure { dependsOn(taskName) }
}
