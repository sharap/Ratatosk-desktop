package chat.ratatosk.desktop.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Разметка, снятая для одной строки предпросмотра: в списке чатов и в
 * уведомлении человек должен видеть слова, а не звёздочки и решётки.
 *
 * Набор случаев перенесён из android-версии (`MarkdownUtilsTest`): один и тот
 * же помощник в двух приложениях обязан отвечать одинаково.
 */
class MarkdownPlainTextTest {
    private fun plain(text: String) = MarkdownUtils.toPlainText(text, "спойлер")

    @Test
    fun codeKeepsContentWithoutBackticks() {
        assertEquals("вызови foo() потом", plain("вызови `foo()` потом"))
        assertEquals("val x = 1", plain("```kotlin\nval x = 1\n```"))
        assertEquals("val x = 1", plain("    val x = 1"))
    }

    @Test
    fun linkKeepsLabelAndDropsAddress() {
        assertEquals("смотри тут", plain("смотри [тут](https://example.com/very/long)"))
    }

    @Test
    fun headingsQuotesAndListsBecomeOneLine() {
        assertEquals("Привет", plain("# Привет"))
        assertEquals("так он и сказал", plain("> так он и сказал"))
        assertEquals("раз два", plain("- раз\n- два"))
        assertEquals("первая вторая", plain("первая\nвторая"))
    }

    @Test
    fun emphasisIsStripped() {
        assertEquals("очень важно", plain("**очень** _важно_"))
        assertEquals("было стало", plain("~~было~~ стало"))
    }

    @Test
    fun spoilerBecomesLabel() {
        // Скрытое остаётся скрытым и в предпросмотре: иначе уведомление
        // показало бы то, что человек спрятал под спойлер.
        assertEquals("это [спойлер] конец", plain("это ||тайна|| конец"))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", plain(""))
        assertEquals("", plain("   "))
    }
}
