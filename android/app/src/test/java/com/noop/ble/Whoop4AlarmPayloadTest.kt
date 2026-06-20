package com.noop.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the WHOOP 4.0 SET_ALARM_TIME payload to the exact bytes captured from the official WHOOP
 * app via HCI btsnoop log (PR #535). The captured frame for epoch 1781912880 (02:48 local, UTC+3)
 * was: AA 10 00 57 23 29 42 01 30 D5 35 6A 00 00 00 00 86 3B 9C 9F — the 9-byte payload field
 * is [01 30 D5 35 6A 00 00 00 00]. Without the trailing [00 00] haptic-mode field the strap ACKs
 * but never fires the haptic (the silent-alarm bug from issue #1).
 */
class Whoop4AlarmPayloadTest {

    private fun bytes(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    @Test
    fun wireCapture_epoch1781912880_matchesOfficialApp() {
        // 1781912880 = 0x6A35D530 → LE: 0x30, 0xD5, 0x35, 0x6A
        val expected = bytes(0x01, 0x30, 0xD5, 0x35, 0x6A, 0x00, 0x00, 0x00, 0x00)
        assertArrayEquals(expected, whoop4AlarmPayload(1_781_912_880L))
    }

    @Test
    fun length_isNineBytes() {
        assertEquals(9, whoop4AlarmPayload(1_000_000_000L).size)
    }

    @Test
    fun leadingByte_isFormByte0x01() {
        assertEquals(0x01.toByte(), whoop4AlarmPayload(0L)[0])
    }

    @Test
    fun epochField_isU32LittleEndian() {
        // 0x11223344 → LE: 0x44, 0x33, 0x22, 0x11
        val p = whoop4AlarmPayload(0x11223344L)
        assertArrayEquals(bytes(0x44, 0x33, 0x22, 0x11), p.copyOfRange(1, 5))
    }

    @Test
    fun subsecondsField_isAlwaysZero() {
        val p = whoop4AlarmPayload(1_781_912_880L)
        assertEquals(0x00.toByte(), p[5])
        assertEquals(0x00.toByte(), p[6])
    }

    @Test
    fun hapticModeField_isAlwaysZero() {
        val p = whoop4AlarmPayload(1_781_912_880L)
        assertEquals(0x00.toByte(), p[7])
        assertEquals(0x00.toByte(), p[8])
    }
}
