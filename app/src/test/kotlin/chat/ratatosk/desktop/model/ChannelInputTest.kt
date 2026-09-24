package chat.ratatosk.desktop.model

import chat.ratatosk.desktop.backend.Channel
import chat.ratatosk.desktop.backend.Group
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Почему в канале закрыто поле ввода.
 *
 * Причины разные, и слова к ним разные: «нет права» — про §6.2, «ждём
 * впуска» — про §10.4, «читать нечем» — про отсутствие поколения ключа.
 * Свалив их в одно «нельзя», клиент соврал бы всем троим.
 */
class ChannelInputTest {
    private fun channel(
        canWrite: Boolean = false,
        awaiting: Boolean = false,
        readable: Boolean = true,
        mine: Boolean = false,
    ) = Channel(
        open = false,
        mine = mine,
        canWrite = canWrite,
        canAdmit = mine,
        rightsUntilMs = 0UL,
        powBits = 0u,
        awaiting = awaiting,
        readable = readable,
        mayRotate = false,
        ownerUnseen = false,
        grantsExpiring = 0u,
        sourcesNow = 1u,
        seedsKnown = 1u,
        awaitingBlocks = 0u,
        rotationOverdue = false,
        signal = org.ratatosk.core.FfiChannelSignal.FINE,
        waiting = null,
    )

    private fun group(channel: Channel? = null) = Group(
        chatId = ByteArray(16) { 1 },
        title = "Канал",
        joined = true,
        canManage = channel?.mine ?: false,
        avatarMs = 0UL,
        createdMs = null,
        channel = channel,
    )

    @Test
    fun anOrdinaryGroupIsNotGated() {
        assertEquals(ChannelInput.ALLOWED, channelInput(group()))
        assertEquals(ChannelInput.ALLOWED, channelInput(null))
    }

    @Test
    fun theOwnerWrites() {
        assertEquals(ChannelInput.ALLOWED, channelInput(group(channel(mine = true, canWrite = true))))
    }

    /**
     * Держатель права пишет наравне с владельцем.
     *
     * Так было не всегда: пока рой не развозил чужое слово, у делегата
     * не было дороги — состав канала §3.2 оставляет владельцу. Теперь
     * своё слово уезжает владельцу и своим сидам, и гасить поле по составу
     * больше не за чем.
     */
    @Test
    fun aGranteeWritesToo() {
        assertEquals(ChannelInput.ALLOWED, channelInput(group(channel(canWrite = true))))
    }

    @Test
    fun aReaderIsToldAboutRights() {
        assertEquals(ChannelInput.NO_RIGHT, channelInput(group(channel(canWrite = false))))
    }

    /** Ожидание впуска важнее прочего: пока не впустили, разговора нет. */
    @Test
    fun awaitingComesFirst() {
        assertEquals(
            ChannelInput.AWAITING,
            channelInput(group(channel(canWrite = true, awaiting = true))),
        )
    }

    @Test
    fun withoutAKeyThereIsNothingToReadWith() {
        assertEquals(
            ChannelInput.NOT_READABLE,
            channelInput(group(channel(canWrite = true, readable = false))),
        )
    }
}
