plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.hot.reload)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Room's generated database constructor is an expect/actual object.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.gympad.shared"
        compileSdk = 37
        minSdk = 26
        androidResources {
            enable = true
        }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.camera.camera2)
            implementation(libs.camera.view)
            implementation(libs.camera.mlkit.vision)
            implementation(libs.mlkit.barcode.scanning)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.compose.ui.test)
            implementation(libs.room.testing)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// A headless system Java can't open windows, so the desktop app always runs on the toolchain JDK.
val toolchains = extensions.getByType<JavaToolchainService>()

compose.resources {
    packageOfResClass = "com.gympad.resources"
}

compose.desktop {
    application {
        mainClass = "com.gympad.MainKt"
        javaHome = toolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
            .get().metadata.installationPath.asFile.absolutePath
    }
}
