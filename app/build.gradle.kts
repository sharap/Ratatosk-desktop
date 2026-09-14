plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
}

val rustProjectDir = file("../../ratatosk-core")
val generatedDir = file("src/main/java")

dependencies {
    implementation(libs.jna)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.commonmark)
    implementation(libs.commonmark.autolink)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.commonmark.tables)
    implementation(libs.qr.generator)
    implementation(libs.jb.compose.adaptive)
    implementation(libs.jb.compose.adaptive.layout)
    implementation(libs.jb.compose.adaptive.navigation)
    implementation(libs.jb.compose.material3.adaptive.navigation.suite)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "chat.ratatosk.desktop.MainKt"
        jvmArgs += "-Djna.library.path=${rustProjectDir.resolve("target/release").absolutePath}"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "Ratatosk"
            packageVersion = "1.0.0"
        }
    }
}

val buildRustCore = tasks.register<Exec>("buildRustCore") {
    workingDir = rustProjectDir
    commandLine("cargo", "--config", "profile.release.strip=\"debuginfo\"", "build", "-p", "ratatosk-ffi", "--lib", "--release", "--features", "tor,mail")
    
    inputs.dir(rustProjectDir.resolve("crates"))
    outputs.dir(rustProjectDir.resolve("target/release"))
}

val buildRustCoreWindows = tasks.register<Exec>("buildRustCoreWindows") {
    workingDir = rustProjectDir
    commandLine("cargo", "build", "-p", "ratatosk-ffi", "--lib", "--release", "--target", "x86_64-pc-windows-gnu", "--features", "tor,mail")
    
    inputs.dir(rustProjectDir.resolve("crates"))
    outputs.dir(rustProjectDir.resolve("target/x86_64-pc-windows-gnu/release"))
}

tasks.register<Exec>("generateUniFFIBindings") {
    dependsOn(buildRustCore)
    workingDir = rustProjectDir

    val hostLibName = if (System.getProperty("os.name").contains("Windows")) "ratatosk_ffi.dll" else if (System.getProperty("os.name").contains("Mac")) "libratatosk_ffi.dylib" else "libratatosk_ffi.so"
    val hostLibPath = rustProjectDir.resolve("target/release/$hostLibName")

    commandLine(
        "cargo", "run", "-p", "ratatosk-bindgen", "--bin", "uniffi-bindgen",
        "generate", "--library", hostLibPath.absolutePath,
        "--language", "kotlin", "--out-dir", generatedDir.absolutePath
    )

    inputs.dir(rustProjectDir.resolve("crates/ffi"))
    outputs.dir(generatedDir)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("generateUniFFIBindings")
}
