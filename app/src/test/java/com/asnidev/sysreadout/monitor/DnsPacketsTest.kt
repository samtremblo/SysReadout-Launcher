package com.asnidev.sysreadout.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DnsPacketsTest {

    // "example.com" A query, id 0x1234, recursion desired.
    private val query = byteArrayOf(
        0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0,
        7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
        3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0, 0, 1, 0, 1,
    )

    @Test
    fun aResponseThatFitsIsUntouched() {
        assertSame(query, Dns.truncated(query, 512))
    }

    @Test
    fun aTooBigResponseKeepsTheQuestionAndSetsTc() {
        val big = query.copyOf(query.size + 3000).also {
            it[2] = 0x81.toByte(); it[3] = 0x80.toByte() // response flags
            it[7] = 40 // 40 answers (not really there; must be zeroed)
        }
        val cut = Dns.truncated(big, 1000)
        assertEquals(query.size, cut.size)
        assertEquals(0x02, cut[2].toInt() and 0x02) // TC
        assertEquals(1, Packets.u16(cut, 4))
        assertEquals(0, Packets.u16(cut, 6))
        assertEquals(Dns.Question("example.com", Dns.TYPE_A), Dns.question(cut))
    }

    private fun tcpSyn(dstPort: Int, flags: Int = 0x02): ByteArray {
        val p = ByteArray(40)
        p[0] = 0x45; p[3] = 40; p[9] = 6
        byteArrayOf(10, 0, 0, 5).copyInto(p, 12)
        byteArrayOf(10, 111, 222.toByte(), 2).copyInto(p, 16)
        p[20] = 0x9C.toByte(); p[21] = 0x40 // port 40000
        p[22] = (dstPort ushr 8).toByte(); p[23] = dstPort.toByte()
        p[24] = 0; p[25] = 0; p[26] = 0x10; p[27] = 0x00 // seq 4096
        p[32] = 0x50; p[33] = flags.toByte()
        return p
    }

    @Test
    fun aTcpDnsSynGetsAValidReset() {
        val rst = Packets.tcpResetFor(tcpSyn(53), 40)
        assertNotNull(rst)
        rst!!
        assertEquals(0, Packets.checksum(rst, 0, 20)) // IP header checksum
        assertEquals(0x14, rst[33].toInt()) // RST + ACK
        assertEquals(53, Packets.u16(rst, 20))
        assertEquals(40000, Packets.u16(rst, 22))
        assertEquals(4097, Packets.u16(rst, 30)) // ack = seq + 1
        // TCP checksum verifies to zero over pseudo header + segment.
        val pseudo = ByteArray(32)
        rst.copyInto(pseudo, 0, 12, 20); pseudo[9] = 6; pseudo[11] = 20
        rst.copyInto(pseudo, 12, 20, 40)
        assertEquals(0, Packets.checksum(pseudo, 0, 32))
    }

    @Test
    fun otherTcpAndUdpAreLeftAlone() {
        assertNull(Packets.tcpResetFor(tcpSyn(853), 40))
        assertNull(Packets.tcpResetFor(tcpSyn(53, flags = 0x10), 40)) // plain ACK
        assertNull(Packets.tcpResetFor(tcpSyn(53, flags = 0x04), 40)) // a reset: never answer one
        val udp = Packets.buildUdp4(byteArrayOf(10, 0, 0, 5), byteArrayOf(10, 111, 222.toByte(), 2), 40000, 53, query)
        assertNull(Packets.tcpResetFor(udp, udp.size))
    }
}
