package chat.ratatosk.desktop.model

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Правила устройства моделей.
 *
 * Обе проверки — про то, что компилятор пропускает молча. Модель, забытая
 * в списке `features`, не получает ни событий, ни очистки при выходе:
 * выглядит это как «раздел не обновляется» и «чужие данные у следующего
 * аккаунта», а выясняется через неделю. Своя ошибка мимо [SessionContext]
 * так же тихо прячет от человека всё, о чём модель хотела сказать
 * (на андроиде это случилось при разрезке — там окно завело свою).
 */
class ModelsHygieneTest {
    private val modelsDir = File("src/main/kotlin/chat/ratatosk/desktop/model")
    private val models: List<File> =
        modelsDir.listFiles()?.filter { it.extension == "kt" }?.sortedBy { it.name } ?: emptyList()
    private val appModels = File(modelsDir, "AppModels.kt")

    @Test
    fun sourcesAreWhereWeThink() {
        assertTrue("модели не найдены — проверка смотрит не туда", models.size > 10)
        assertTrue("нет AppModels.kt", appModels.isFile)
    }

    /** Каждая модель — в списке `features`: иначе она вне событий и очистки. */
    @Test
    fun everyFeatureModelIsInTheList() {
        val listed = Regex("""private val features: List<FeatureModel> = listOf\(([^)]*)\)""")
            .find(appModels.readText())
            ?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        assertTrue("список features не найден", listed.size > 5)

        val declared = models.mapNotNull { file ->
            val text = file.readText()
            val name = Regex("""class (\w+Model)\s*\([^)]*\)\s*:[^{]*FeatureModel""")
                .find(text)?.groupValues?.get(1)
                ?: return@mapNotNull null
            // `ContactsModel` → `contacts`, как её зовёт AppModels.
            name.removeSuffix("Model").replaceFirstChar { it.lowercase() }
        }
        assertTrue("моделей-наследников не нашлось", declared.size > 5)

        val forgotten = declared.filterNot { it in listed }
        assertTrue("не в features — ни событий, ни очистки: $forgotten", forgotten.isEmpty())
    }

    /** Ошибка одна на всех: `SessionContext._error`. */
    @Test
    fun thereIsOnlyOneErrorChannel() {
        val session = File(modelsDir, "Session.kt")
        val own = models
            .filter { it.absolutePath != session.absolutePath }
            .filter { file ->
                file.readLines().any { Regex("""val _error\s*=\s*MutableStateFlow""").containsMatchIn(it) }
            }
        assertTrue("своя ошибка мимо SessionContext: ${own.map { it.name }}", own.isEmpty())
    }
}
