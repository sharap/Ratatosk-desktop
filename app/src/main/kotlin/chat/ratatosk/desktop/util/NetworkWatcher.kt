package chat.ratatosk.desktop.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.NetworkInterface

/**
 * Следит за сменой сети: включили Wi-Fi, подняли VPN, выдернули провод.
 *
 * Системного события об этом у JVM нет, поэтому сравниваем набор своих
 * адресов. Ядру надо сказать [onChanged] («networkChanged»): маяк §5.1
 * и соединения привязаны к адресам, и после переезда они молчат, пока
 * никто не попросил пересмотреть сеть.
 */
object NetworkWatcher {
    private const val TAG = "NetworkWatcher"
    private const val PERIOD_MS = 15_000L

    fun start(scope: CoroutineScope, onChanged: () -> Unit): Job = scope.launch(Dispatchers.IO) {
        var known = addresses()
        while (isActive) {
            delay(PERIOD_MS)
            val now = addresses()
            if (now != known) {
                // Только число адресов: сами адреса — это то, где человек сидит.
                Log.d(TAG, "network changed: ${known.size} -> ${now.size}")
                known = now
                onChanged()
            }
        }
    }

    private fun addresses(): Set<String> = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface -> iface.inetAddresses.asSequence().map { "${iface.name}:${it.hostAddress}" } }
            .toSet()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to list interfaces", e)
        emptySet()
    }
}
