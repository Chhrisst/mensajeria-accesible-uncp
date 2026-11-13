package com.example.mensajeria

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
}
