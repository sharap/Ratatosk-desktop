import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
}

// --- Ядро: библиотека и биндинги из одной сборки ---------------------------
//
// Собирает `ratatosk-core/tools/build-desktop.sh`: он кладёт библиотеку в
// `<out>/resources/<префикс JNA>/`, а биндинги, сгенерированные по этой же
// библиотеке, — в `<out>/kotlin/`. Оба каталога подключаются к sourceSet
// ниже, так что руками ничего не копируется, а в `src/` сгенерированного нет.
//
// Признаки ядра — `ratatosk.features` в gradle.properties (или -P...).
// Путь к ядру — `ratatosk.core.dir`, по умолчанию соседний каталог.

val coreDir: File = providers.gradleProperty("ratatosk.core.dir")
    .map { rootDir.resolve(it) }
    .getOrElse(rootDir.resolve("../ratatosk-core"))
val coreFeatures: String = providers.gradleProperty("ratatosk.features").getOrElse("")

fun Exec.configureCoreBuild(outDir: Provider<Directory>, target: String?) {
    group = "ratatosk"
    workingDir = coreDir
    // Отладочные символы в дистрибутиве ни к чему, а весят больше самой
    // библиотеки. Через окружение — чтобы не править профиль в ядре.
    environment("CARGO_PROFILE_RELEASE_STRIP", "debuginfo")

    val args = mutableListOf("bash", "tools/build-desktop.sh", "--out", outDir.get().asFile.absolutePath)
    if (coreFeatures.isNotBlank()) args += listOf("--features", coreFeatures)
    if (target != null) args += listOf("--target", target)
    commandLine(args)

    inputs.dir(coreDir.resolve("crates")).withPropertyName("crates")
    inputs.file(coreDir.resolve("Cargo.toml")).withPropertyName("cargoToml")
    inputs.file(coreDir.resolve("Cargo.lock")).withPropertyName("cargoLock")
    inputs.file(coreDir.resolve("tools/build-desktop.sh")).withPropertyName("script")
    inputs.property("features", coreFeatures)
    outputs.dir(outDir)
}

val uniffiDir = layout.buildDirectory.dir("generated/uniffi")
val buildRustCore = tasks.register<Exec>("buildRustCore") {
    description = "Собирает ratatosk-ffi под хост и генерирует биндинги Kotlin."
    configureCoreBuild(uniffiDir, target = null)
}

// Windows-библиотека кросс-сборкой (x86_64-pc-windows-gnu). Не по умолчанию:
// нужен mingw-линкер. Подключается к ресурсам с -Pratatosk.windows=true;
// её биндинги не используются — берутся хостовые, признаки те же.
val uniffiWindowsDir = layout.buildDirectory.dir("generated/uniffi-windows")
val buildRustCoreWindows = tasks.register<Exec>("buildRustCoreWindows") {
    description = "Кросс-сборка ratatosk-ffi под Windows x86_64."
    configureCoreBuild(uniffiWindowsDir, target = "x86_64-pc-windows-gnu")
}
val includeWindows = providers.gradleProperty("ratatosk.windows").map { it.toBoolean() }.getOrElse(false)

kotlin.sourceSets.named("main") {
    kotlin.srcDir(files(uniffiDir.map { it.dir("kotlin") }).builtBy(buildRustCore))
}
sourceSets.named("main") {
    resources.srcDir(files(uniffiDir.map { it.dir("resources") }).builtBy(buildRustCore))
    if (includeWindows) {
        resources.srcDir(files(uniffiWindowsDir.map { it.dir("resources") }).builtBy(buildRustCoreWindows))
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.components.resources)

    implementation(libs.jna)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.commonmark)
    implementation(libs.commonmark.autolink)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.commonmark.tables)
    implementation(libs.qrcode)

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "chat.ratatosk.desktop.MainKt"
        // JBR из Android Studio (частый JAVA_HOME) идёт без jpackage — берём
        // тот же JDK, которым компилируем.
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(17)
        }.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // По :app:suggestRuntimeModules; jdk.unsupported нужен JNA и DataStore.
            modules("java.instrument", "jdk.unsupported")
            packageName = "Ratatosk"
            packageVersion = "1.0.0"
        }
    }
}
