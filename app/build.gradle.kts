import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
}

// Версия приложения в одном месте: она попадает и в пакеты, и в имя
// собранного jar-а, а разъехавшись, эти два числа врут о одном и том же.
val appVersion = "0.1.0"

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

fun Exec.configureCoreBuild(outDir: Provider<Directory>, target: String?, bluetooth: Boolean = true) {
    group = "ratatosk"
    workingDir = coreDir
    // Отладочные символы в дистрибутиве ни к чему, а весят больше самой
    // библиотеки. Через окружение — чтобы не править профиль в ядре.
    environment("CARGO_PROFILE_RELEASE_STRIP", "debuginfo")

    val args = mutableListOf("bash", "tools/build-desktop.sh", "--out", outDir.get().asFile.absolutePath)
    if (coreFeatures.isNotBlank()) args += listOf("--features", coreFeatures)
    if (target != null) args += listOf("--target", target)
    if (!bluetooth) args += "--no-bt"
    commandLine(args)

    inputs.dir(coreDir.resolve("crates")).withPropertyName("crates")
    inputs.file(coreDir.resolve("Cargo.toml")).withPropertyName("cargoToml")
    inputs.file(coreDir.resolve("Cargo.lock")).withPropertyName("cargoLock")
    inputs.file(coreDir.resolve("tools/build-desktop.sh")).withPropertyName("script")
    inputs.property("features", coreFeatures)
    inputs.property("bluetooth", bluetooth)
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

// Linux arm64 (PinePhone с Mobian, одноплатники). Как и Windows — не по
// умолчанию: нужен линкер `aarch64-linux-gnu-gcc` и цель Rust
// `aarch64-unknown-linux-gnu`. Подключается к ресурсам с -Pratatosk.arm64=true.
//
// Сам `.deb` под arm64 так не получить: `jpackage` кладёт в пакет ту JVM,
// под которой работает, и собирать его надо на arm64. Зато с этой библиотекой
// (и графикой arm64, см. ниже) собирается один jar, который запускается
// системной JVM и там, и здесь.
val uniffiArm64Dir = layout.buildDirectory.dir("generated/uniffi-linux-arm64")
// Эфир в кросс-сборке по умолчанию выключен: `bluer` тянет `libdbus-sys`,
// а тому нужны заголовки dbus **целевой** архитектуры. Их ставят отдельно
// (`dpkg --add-architecture arm64`, `libdbus-1-dev:arm64`, кросс-pkg-config),
// и тогда — `-Pratatosk.arm64.bt=true`.
val arm64Bluetooth = providers.gradleProperty("ratatosk.arm64.bt").map { it.toBoolean() }.getOrElse(false)
val buildRustCoreArm64 = tasks.register<Exec>("buildRustCoreArm64") {
    description = "Кросс-сборка ratatosk-ffi под Linux arm64 (glibc)."
    configureCoreBuild(uniffiArm64Dir, target = "aarch64-unknown-linux-gnu", bluetooth = arm64Bluetooth)
    // Без этого Rust линкует целевые объекты хозяйским `cc`, и `rust-lld`
    // отвечает «incompatible with elf64-x86-64» — по сообщению не догадаться.
    environment("CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER", "aarch64-linux-gnu-gcc")
    environment("CC_aarch64_unknown_linux_gnu", "aarch64-linux-gnu-gcc")
    doFirst {
        // Иначе падает `cc-rs` на середине сборки `ring`, и по сообщению
        // непонятно, что делать. Говорим прямо и заранее.
        val linker = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .any { File(it, "aarch64-linux-gnu-gcc").canExecute() }
        require(linker) {
            "Нет линкера aarch64-linux-gnu-gcc. Установите его " +
                "(Debian/Ubuntu: sudo apt install gcc-aarch64-linux-gnu) и цель Rust: " +
                "rustup target add aarch64-unknown-linux-gnu"
        }
    }
}
val includeArm64 = providers.gradleProperty("ratatosk.arm64").map { it.toBoolean() }.getOrElse(false)

// Имя jar-а Compose берёт по машине сборки («linux-x64»), а внутри лежат обе
// архитектуры — скажем это в имени, иначе на PinePhone файл выглядит чужим.
if (includeArm64) {
    tasks.matching { it.name == "packageUberJarForCurrentOS" }.configureEach {
        // Тип задачи — `org.gradle.jvm.tasks.Jar`, а не `bundling.Jar`.
        (this as org.gradle.jvm.tasks.Jar).archiveFileName.set("Ratatosk-linux-x64-arm64-$appVersion.jar")
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(files(uniffiDir.map { it.dir("kotlin") }).builtBy(buildRustCore))
}
sourceSets.named("main") {
    resources.srcDir(files(uniffiDir.map { it.dir("resources") }).builtBy(buildRustCore))
    if (includeWindows) {
        resources.srcDir(files(uniffiWindowsDir.map { it.dir("resources") }).builtBy(buildRustCoreWindows))
    }
    if (includeArm64) {
        resources.srcDir(files(uniffiArm64Dir.map { it.dir("resources") }).builtBy(buildRustCoreArm64))
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    // Графика Skia под arm64 — только когда её просят: это ещё десятки мегабайт
    // в сборке, а хозяйской машине она не нужна.
    if (includeArm64) {
        implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:0.150.1")
    }
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
    implementation(libs.jna.platform)
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
            packageVersion = appVersion
            description = "Ratatosk — переписка без серверов"
            vendor = "Ratatosk"

            linux {
                // Без него `packageDeb` подставляет адрес сборочной машины.
                debMaintainer = "noreply@ratatosk.chat"
                appCategory = "Network"
                shortcut = true
                iconFile.set(project.file("src/main/resources/icon.png"))
            }

            windows {
                // Один и тот же UUID на все выпуски: по нему установщик
                // понимает, что это обновление, а не второе приложение.
                // Меняется только если это уже другое приложение.
                upgradeUuid = "2f4c9c1e-8f1a-4f2a-9b3e-5a7c6d0e1b42"
                menuGroup = "Ratatosk"
                menu = true
                shortcut = true
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }

            macOS {
                bundleID = "chat.ratatosk.desktop"
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}

// --- Зависимости .deb -----------------------------------------------------
//
// `ffmpeg` — им играются и пишутся голосовые и проигрываются кружки:
// своего декодера Opus на JVM нет, а нативные библиотеки ради этого —
// сотня мегабайт. Без него приложение работает, но запись и просмотр
// молча не работали бы, и человек винил бы приложение, а не
// отсутствующий пакет.
//
// Остальное — то, что линкует **сама библиотека ядра**: эфир (BlueZ)
// на Linux собирается всегда, отсюда dbus, а за ним systemd и libcap.
// Их приходится называть руками: jpackage осматривает JVM и приложение,
// а нашу библиотеку грузит JNA из ресурсов, и в его список она не
// попадает. Не назвав, получаем UnsatisfiedLinkError при запуске —
// и ни слова о том, чего не хватает.
//
// Плагин Compose своей строчки для `Depends` не даёт, зато пробрасывает
// в jpackage свободные доводы — ими и говорим.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    if (name.contains("Deb", ignoreCase = true)) {
        freeArgs.addAll(
            "--linux-package-deps",
            // Без пробелов: jpackage читает доводы из файла и делит их
            // по пробелам, так что «ffmpeg, libdbus-1-3» превращается
            // в неизвестный ключ. Запятая без пробела для dpkg законна.
            "ffmpeg,libdbus-1-3,libsystemd0,libcap2",
        )
    }
}

tasks.withType<Test>().configureEach {
    providers.gradleProperty("ratatosk.test.keyring").orNull?.let { systemProperty("ratatosk.test.keyring", it) }

    // Язык проверок закреплён, и это не прихоть. `Strings` выбирает слова
    // по языку системы, а часть проверяемой логики живёт **только**
    // в русской ветке — склонения «вложение/вложения/вложений». Пока язык
    // брался у машины, набор проверок был зелёным здесь и красным в CI,
    // причём про слова, а не про поведение: худший вид падения — тот,
    // который зависит от того, кто запускает.
    //
    // Через jvmArgs, а не systemProperty: `Locale.getDefault()` читается
    // при запуске JVM, и свойство, поставленное позже, на него не влияет.
    jvmArgs("-Duser.language=ru", "-Duser.country=RU")
}
