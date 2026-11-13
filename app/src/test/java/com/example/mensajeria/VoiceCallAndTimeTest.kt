package com.example.mensajeria

import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.repository.ChatRepository
import com.example.mensajeria.domain.service.CallSignalParser
import com.example.mensajeria.domain.service.CallSignalType
import com.example.mensajeria.domain.service.DecryptedCallStreamItem
import com.example.mensajeria.domain.service.VoiceCallSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class VoiceCallAndTimeTest {

    @Test
    fun callSignalParser_roundTripsInviteAndStripsPipes() {
        val original = VoiceCallSignal(
            type = CallSignalType.INVITE,
            callId = "call-123",
            callerId = "user-a",
            callerName = "Juan|UNCP",
            timestampMs = 1700000000000L
        )
        val encoded = CallSignalParser.encode(original)
        val parsed = CallSignalParser.parse(encoded)
        assertNotNull(parsed)
        assertEquals(CallSignalType.INVITE, parsed?.type)
        assertEquals("call-123", parsed?.callId)
        assertEquals("user-a", parsed?.callerId)
        assertEquals("Juan UNCP", parsed?.callerName)
        assertEquals(1700000000000L, parsed?.timestampMs)
    }

    @Test
    fun callSignalParser_findsPendingInviteAndIgnoresEndedOrAcceptedCalls() {
        val now = 1_700_000_050_000L
        val invite1 = VoiceCallSignal(CallSignalType.INVITE, "call-old", "cristian", "Cristian", now - 10_000L)
        val end1 = VoiceCallSignal(CallSignalType.END, "call-old", "cristian", "Cristian", now - 5_000L)
        val invite2 = VoiceCallSignal(CallSignalType.INVITE, "call-new", "cristian", "Cristian", now - 2_000L)

        val items = listOf(
            DecryptedCallStreamItem("m1", "cristian", "juan", false, CallSignalParser.encode(invite1), "", "01:00"),
            DecryptedCallStreamItem("m2", "cristian", "juan", false, CallSignalParser.encode(end1), "", "01:00"),
            DecryptedCallStreamItem("m3", "cristian", "juan", false, CallSignalParser.encode(invite2), "", "01:00")
        )

        val pending = CallSignalParser.findPendingIncomingInvite(items, currentUserId = "juan", nowMs = now)
        assertNotNull(pending)
        assertEquals("call-new", pending?.callId)

        val accept2 = VoiceCallSignal(CallSignalType.ACCEPT, "call-new", "juan", "Juan", now - 1_000L)
        val acceptedItems = items + DecryptedCallStreamItem("m4", "juan", "cristian", true, CallSignalParser.encode(accept2), "", "01:00")

        assertTrue(CallSignalParser.isCallAccepted(acceptedItems, "call-new"))
        assertFalse(CallSignalParser.hasCallTerminated(acceptedItems, "call-new"))
        assertNull(CallSignalParser.findPendingIncomingInvite(acceptedItems, currentUserId = "juan", nowMs = now))

        val end2 = VoiceCallSignal(CallSignalType.END, "call-new", "juan", "Juan", now)
        val endedItems = acceptedItems + DecryptedCallStreamItem("m5", "juan", "cristian", true, CallSignalParser.encode(end2), "", "01:00")

        assertTrue(CallSignalParser.hasCallTerminated(endedItems, "call-new"))
        assertFalse(CallSignalParser.isCallAccepted(endedItems, "call-new"))
    }

    @Test
    fun formatServerTimeToLocal_convertsUtcToTargetDeviceTimezone() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"))
            val repo = ChatRepository()
            assertEquals("00:56", repo.formatServerTimeToLocal("05:56"))
            assertEquals("00:56", repo.formatServerTimeToLocal("2026-11-13T05:56:00Z"))
            assertEquals("Ahora", repo.formatServerTimeToLocal("Ahora"))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun callSignalParser_acceptsIncomingInviteWithDeviceClockSkew() {
        val receiverNow = 1_700_000_050_000L
        // El reloj del emisor está 10 segundos adelantado respecto al receptor
        val futureInvite = VoiceCallSignal(
            type = CallSignalType.INVITE,
            callId = "call-skew",
            callerId = "juan",
            callerName = "Juan",
            timestampMs = receiverNow + 10_000L
        )
        val items = listOf(
            DecryptedCallStreamItem("m1", "juan", "carlos", false, CallSignalParser.encode(futureInvite), "", "01:41")
        )

        val pending = CallSignalParser.findPendingIncomingInvite(items, currentUserId = "carlos", nowMs = receiverNow)
        assertNotNull(pending)
        assertEquals("call-skew", pending?.callId)
    }

    @Test
    fun contactConnectionStatus_returnsDisconnectedWhenLastSeenIsEmpty() {
        val neverSeen = Contact(id = "u1", name = "Cristian", isOnline = false, lastSeenTime = "")
        assertEquals("", neverSeen.getLastSeenDisplay())
        assertEquals("Desconectado", neverSeen.getConnectionStatusText())

        val seenEarlier = Contact(id = "u2", name = "Mario", isOnline = false, lastSeenTime = "01:06")
        assertEquals("01:06", seenEarlier.getLastSeenDisplay())
        assertEquals("Conectado última vez a la 01:06", seenEarlier.getConnectionStatusText())

        val seenAtTwo = Contact(id = "u2b", name = "Mario", isOnline = false, lastSeenTime = "02:15")
        assertEquals("Conectado última vez a las 02:15", seenAtTwo.getConnectionStatusText())

        val online = Contact(id = "u3", name = "Carlos", isOnline = true, lastSeenTime = "01:41")
        assertEquals("En línea", online.getConnectionStatusText())
    }

    @Test
    fun callSignalParser_computeContactPresence_distinguishesOnlineOfflineAndExpired() {
        val now = 1_700_000_200_000L

        val recentPresence = DecryptedCallStreamItem(
            "p1", "carlos", "juan", false,
            CallSignalParser.encode(VoiceCallSignal(CallSignalType.PRESENCE, "pr1", "carlos", "Carlos", now - 30_000L)),
            "", "01:40"
        )
        val (online1, ts1) = CallSignalParser.computeContactPresence(listOf(recentPresence), nowMs = now)
        assertTrue(online1)
        assertEquals(now - 30_000L, ts1)

        val offlineSignal = DecryptedCallStreamItem(
            "p2", "carlos", "juan", false,
            CallSignalParser.encode(VoiceCallSignal(CallSignalType.OFFLINE, "off1", "carlos", "Carlos", now - 5_000L)),
            "", "01:41"
        )
        val (online2, ts2) = CallSignalParser.computeContactPresence(listOf(recentPresence, offlineSignal), nowMs = now)
        assertFalse(online2)
        assertEquals(now - 5_000L, ts2)

        val expiredPresence = DecryptedCallStreamItem(
            "p3", "mario", "juan", false,
            CallSignalParser.encode(VoiceCallSignal(CallSignalType.PRESENCE, "pr2", "mario", "Mario", now - 300_000L)),
            "", "01:06"
        )
        val (online3, ts3) = CallSignalParser.computeContactPresence(listOf(expiredPresence), nowMs = now)
        assertFalse(online3)
        assertEquals(now - 300_000L, ts3)

        // Las señales salientes propias (!isFromMe == false) nunca deben marcar al contacto en línea
        val outgoingOnly = DecryptedCallStreamItem(
            "p4", "juan", "cristian", true,
            CallSignalParser.encode(VoiceCallSignal(CallSignalType.PRESENCE, "pr3", "juan", "Juan", now - 1_000L)),
            "", "01:42"
        )
        val (online4, ts4) = CallSignalParser.computeContactPresence(listOf(outgoingOnly), nowMs = now)
        assertFalse(online4)
        assertNull(ts4)
    }
}
