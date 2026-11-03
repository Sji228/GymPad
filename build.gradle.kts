plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.hot.reload) apply false
}

tasks.register<Copy>("apk") {
    description = "Builds the signed release APK into dist/GymPad.apk."
    dependsOn(":app:assembleRelease")
    from(layout.projectDirectory.dir("app/build/outputs/apk/release")) {
        include("*.apk")
    }
    into(layout.projectDirectory.dir("dist"))
    rename { "GymPad.apk" }
}
