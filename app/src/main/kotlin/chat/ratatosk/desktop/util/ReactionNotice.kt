package chat.ratatosk.desktop.util

import org.ratatosk.core.FfiMessage
import org.ratatosk.core.FfiReaction

/**
 * О какой реакции полного клиента стоит сказать человеку — и стоит ли.
 *
 * Событие ядра `ReactionChanged` одно на постановку и на снятие, смайлика
 * в нём нет — только чат, сообщение и автор. Поэтому смотрим в само
 * сообщение. Три отказа: чужое сообщение (в группе это шум на каждый
 * смайлик каждого), реакции автора нет (её сняли), реакция своя.
 */
fun reactionToAnnounce(msg: FfiMessage, authorIk: ByteArray): FfiReaction? {
    if (!msg.mine) return null
    val reaction = msg.reactions.firstOrNull { it.authorIk.contentEquals(authorIk) } ?: return null
    if (reaction.mine) return null
    return reaction
}

/**
 * То же для компаньона: автора в событии нет, есть только список реакций
 * целиком. Новые — это чужие смайлики, которых стало больше, чем было.
 * Каждый вернётся столько раз, на сколько вырос.
 */
fun newForeignReactions(previous: List<Pair<String, Boolean>>?, current: List<Pair<String, Boolean>>): List<String> {
    // Первый раз видим сообщение — это не «поставили», а «уже стояли».
    if (previous == null) return emptyList()
    val before = previous.filterNot { it.second }.groupingBy { it.first }.eachCount()
    return current.filterNot { it.second }.groupingBy { it.first }.eachCount()
        .flatMap { (emoji, count) -> List((count - (before[emoji] ?: 0)).coerceAtLeast(0)) { emoji } }
}
