package chat.ratatosk.desktop.util

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * Системные уведомления. Одно уведомление на [key]: новое заменяет прежнее.
 * [onClick] вызывается на UI-потоке.
 */
interface DesktopNotifier {
    fun show(key: String, title: String, body: String, onClick: () -> Unit)
    fun dismiss(key: String)

    companion object {
        /**
         * На Linux — freedesktop-уведомления через `notify-send` (libnotify ≥ 0.8:
         * действия и ожидание щелчка). Иначе — [fallback], обычно уведомление трея.
         */
        fun create(iconPath: String?, openLabel: String, fallback: DesktopNotifier): DesktopNotifier {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("linux") && NotifySendNotifier.isAvailable()) {
                return NotifySendNotifier(iconPath, openLabel)
            }
            return fallback
        }
    }
}

/**
 * `notify-send -p -A default=… --wait`: процесс печатает идентификатор
 * уведомления, ждёт и печатает имя действия, если по нему щёлкнули.
 * Замена — тем же идентификатором (`-r`), так стопка не растёт.
 */
internal class NotifySendNotifier(private val iconPath: String?, private val openLabel: String) : DesktopNotifier {
    private class Shown(val process: Process, @Volatile var id: Int? = null)

    private val shown = ConcurrentHashMap<String, Shown>()

    override fun show(key: String, title: String, body: String, onClick: () -> Unit) {
        val previous = shown[key]
        val command = buildList {
            add("notify-send")
            add("--app-name=Ratatosk")
            iconPath?.let { add("--icon=$it") }
            add("--print-id")
            previous?.id?.let { add("--replace-id=$it") }
            // «default» — щелчок по самому уведомлению у большинства демонов.
            add("--action=default=$openLabel")
            add("--")
            add(title)
            add(escapeMarkup(body))
        }
        // Прежний процесс больше не нужен: его уведомление сейчас заменится.
        previous?.process?.destroy()

        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: Exception) {
            Log.w(TAG, "notify-send failed to start", e)
            return
        }
        val entry = Shown(process)
        shown[key] = entry

        Thread({
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        when {
                            entry.id == null && trimmed.toIntOrNull() != null -> entry.id = trimmed.toInt()
                            trimmed == "default" -> SwingUtilities.invokeLater(onClick)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                // Идентификатор сохраняем для замены, даже когда уведомление закрыто:
                // демон просто покажет новое.
            }
        }, "notify-send-$key").apply { isDaemon = true }.start()
    }

    override fun dismiss(key: String) {
        val entry = shown.remove(key) ?: return
        entry.process.destroy()
        val id = entry.id ?: return
        // Закрыть на экране — через D-Bus, если есть чем; нет — само уйдёт по таймауту.
        runCatching {
            ProcessBuilder(
                "dbus-send", "--session", "--type=method_call",
                "--dest=org.freedesktop.Notifications", "/org/freedesktop/Notifications",
                "org.freedesktop.Notifications.CloseNotification", "uint32:$id",
            ).start()
        }
    }

    /** Тело уведомления демоны читают как упрощённую разметку: `<`, `&` из переписки — не теги. */
    private fun escapeMarkup(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        private const val TAG = "NotifySend"

        fun isAvailable(): Boolean = try {
            val process = ProcessBuilder("notify-send", "--version").redirectErrorStream(true).start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                val version = process.inputStream.bufferedReader().readText()
                val (major, minor) = Regex("""(\d+)\.(\d+)""").find(version)?.destructured ?: return false
                // Действия и ожидание щелчка — с 0.8.
                major.toInt() > 0 || minor.toInt() >= 8
            }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Иконка для уведомлений файлом PNG: демонам нужен путь, а в ресурсах лежит WebP.
 * Перекодирует Skia (она понимает WebP) один раз в каталог приложения.
 */
object NotificationIcon {
    fun path(): String? = try {
        val file = File(AppDirs.getMediaCacheDir().parentFile, "icon.png")
        if (!file.exists()) {
            val bytes = NotificationIcon::class.java.getResourceAsStream("/icon.webp")?.readBytes() ?: return null
            val png = org.jetbrains.skia.Image.makeFromEncoded(bytes)
                .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes ?: return null
            file.writeBytes(png)
        }
        file.absolutePath
    } catch (e: Exception) {
        Log.w("NotificationIcon", "Failed to prepare icon", e)
        null
    }
}

/**
 * Уведомление трея (Windows, macOS и Linux без libnotify). Щелчок по нему
 * Compose не передаёт, поэтому окно не открывается; заменить прежнее нельзя —
 * взамен не чаще одного уведомления в несколько секунд на ключ.
 */
class TrayNotifier(private val trayState: androidx.compose.ui.window.TrayState) : DesktopNotifier {
    private val lastShown = ConcurrentHashMap<String, Long>()

    override fun show(key: String, title: String, body: String, onClick: () -> Unit) {
        val now = System.currentTimeMillis()
        val last = lastShown[key]
        if (last != null && now - last < 5_000) return
        lastShown[key] = now
        trayState.sendNotification(androidx.compose.ui.window.Notification(title = title, message = body))
    }

    override fun dismiss(key: String) {
        lastShown.remove(key)
    }
}
