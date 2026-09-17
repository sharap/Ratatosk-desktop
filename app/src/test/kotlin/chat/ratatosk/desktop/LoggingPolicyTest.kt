package chat.ratatosk.desktop

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * В журнал не должно попадать то, что пришло проводом.
 *
 * Тексты сообщений, имена файлов, адреса и ссылки сопряжения человек не
 * выбирал показывать: stdout десктопного приложения оседает в журнале сессии
 * (journald, `~/.xsession-errors`), и читать его может кто угодно с доступом
 * к машине. Проверяется правило, а не поведение: соблазн написать
 * `println(event)` при отладке возвращается, и заметить это в ревью трудно.
 */
class LoggingPolicyTest {
    private val sources: List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun sourcesAreWhereWeThink() {
        assertTrue("исходники не найдены — тест смотрит не туда", sources.size > 30)
    }

    @Test
    fun onlyTheLoggerPrints() {
        val offenders = sources.filter { file ->
            file.name != "Log.kt" && file.readLines().any { line ->
                val code = line.substringBefore("//")
                "println(" in code || "printStackTrace(" in code
            }
        }
        assertTrue("печать мимо журнала: ${offenders.map { it.name }}", offenders.isEmpty())
    }

    @Test
    fun eventsAreLoggedByClassNameOnly() {
        // Событие ядра несёт тексты сообщений и имена: в журнал идёт только
        // имя класса. Ловим подстановку события целиком в строку журнала.
        val bad = Regex("""Log\.[dwe]\([^)]*\$\{?(event|message|msg|contact|file)\b(?!::)""")
        val offenders = sources.mapNotNull { file ->
            val hits = file.readLines().withIndex().filter { (_, line) ->
                val code = line.substringBefore("//")
                bad.containsMatchIn(code)
            }
            if (hits.isEmpty()) null else "${file.name}:${hits.map { it.index + 1 }}"
        }
        assertTrue("содержимое события в журнале: $offenders", offenders.isEmpty())
    }
}
