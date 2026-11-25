package com.asnidev.sysreadout.monitor

import kotlinx.coroutines.flow.MutableStateFlow

/** An IPv4/UDP packet read from the VPN interface. */
class UdpPacket(val srcIp: ByteArray, val dstIp: ByteArray, val srcPort: Int, val dstPort: Int, val payload: ByteArray)

/** Just enough IPv4/UDP to answer DNS queries on a tun interface. */
object Packets {

    fun parseUdp4(buf: ByteArray, length: Int): UdpPacket? {
        if (length < 28 || (buf[0].toInt() ushr 4) != 4) return null
        val ihl = (buf[0].toInt() and 0x0F) * 4
        if (buf[9].toInt() != 17 || length < ihl + 8) return null // not UDP
        val total = u16(buf, 2).coerceAtMost(length)
        val udpLen = u16(buf, ihl + 4)
        val end = minOf(total, ihl + udpLen)
        if (end < ihl + 8) return null
        return UdpPacket(
            srcIp = buf.copyOfRange(12, 16),
            dstIp = buf.copyOfRange(16, 20),
            srcPort = u16(buf, ihl),
            dstPort = u16(buf, ihl + 2),
            payload = buf.copyOfRange(ihl + 8, end),
        )
    }

    /** IPv4 header with a valid checksum; UDP checksum 0 ("none"), which IPv4 allows. */
    fun buildUdp4(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val total = 28 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        put16(p, 2, total)
        put16(p, 6, 0x4000) // don't fragment
        p[8] = 64 // TTL
        p[9] = 17 // UDP
        srcIp.copyInto(p, 12)
        dstIp.copyInto(p, 16)
        put16(p, 10, checksum(p, 0, 20))
        put16(p, 20, srcPort)
        put16(p, 22, dstPort)
        put16(p, 24, 8 + payload.size)
        payload.copyInto(p, 28)
        return p
    }

    /**
     * The monitor relays DNS over UDP only. A lookup that falls back to TCP
     * (an answer too big for UDP) gets an immediate reset, so the resolver
     * gives up at once instead of waiting for a handshake that never comes.
     * Returns that RST+ACK for a TCP SYN to port 53, else null.
     */
    fun tcpResetFor(buf: ByteArray, length: Int): ByteArray? {
        if (length < 40 || (buf[0].toInt() ushr 4) != 4 || buf[9].toInt() != 6) return null
        val ihl = (buf[0].toInt() and 0x0F) * 4
        if (length < ihl + 20) return null
        val flags = buf[ihl + 13].toInt()
        val syn = flags and 0x02 != 0
        val rst = flags and 0x04 != 0
        if (!syn || rst || u16(buf, ihl + 2) != 53) return null
        val seq = ((buf[ihl + 4].toLong() and 0xFF) shl 24) or ((buf[ihl + 5].toLong() and 0xFF) shl 16) or
            ((buf[ihl + 6].toLong() and 0xFF) shl 8) or (buf[ihl + 7].toLong() and 0xFF)
        val ack = (seq + 1) and 0xFFFFFFFFL

        val p = ByteArray(40)
        p[0] = 0x45
        put16(p, 2, 40)
        put16(p, 6, 0x4000)
        p[8] = 64
        p[9] = 6 // TCP
        buf.copyInto(p, 12, 16, 20) // from the address it was sent to
        buf.copyInto(p, 16, 12, 16) // back to the sender
        put16(p, 10, checksum(p, 0, 20))
        put16(p, 20, u16(buf, ihl + 2))
        put16(p, 22, u16(buf, ihl))
        // seq 0; ack = their seq + 1 (the SYN counts as one)
        p[28] = (ack ushr 24).toByte(); p[29] = (ack ushr 16).toByte(); p[30] = (ack ushr 8).toByte(); p[31] = ack.toByte()
        p[32] = 0x50 // 20-byte header
        p[33] = 0x14 // RST + ACK
        // TCP checksum over the pseudo header (addresses, protocol, length) and the segment.
        val pseudo = ByteArray(12 + 20)
        p.copyInto(pseudo, 0, 12, 20)
        pseudo[9] = 6
        put16(pseudo, 10, 20)
        p.copyInto(pseudo, 12, 20, 40)
        put16(p, 36, checksum(pseudo, 0, pseudo.size))
        return p
    }

    fun checksum(p: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += u16(p, i)
            i += 2
        }
        if (length % 2 == 1) sum += (p[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    internal fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v ushr 8).toByte()
        b[i + 1] = v.toByte()
    }
}

object Dns {
    data class Question(val name: String, val type: Int)

    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    fun question(msg: ByteArray): Question? = runCatching {
        if (msg.size < 12 || Packets.u16(msg, 4) < 1) return null
        val (name, next) = readName(msg, 12)
        Question(name, Packets.u16(msg, next))
    }.getOrNull()

    /**
     * [response] cut down to fit [max] bytes the standard way: header with the
     * TC (truncated) bit and no records, question kept. Unchanged if it fits.
     */
    fun truncated(response: ByteArray, max: Int): ByteArray {
        if (response.size <= max || response.size < 12) return response
        var end = 12
        val questions = runCatching {
            repeat(Packets.u16(response, 4)) { end = readName(response, end).second + 4 }
            true
        }.getOrDefault(false)
        if (!questions || end > response.size || end > max) end = 12
        val out = response.copyOf(end)
        out[2] = (out[2].toInt() or 0x02).toByte() // TC
        if (end == 12) Packets.put16(out, 4, 0)
        Packets.put16(out, 6, 0)
        Packets.put16(out, 8, 0)
        Packets.put16(out, 10, 0)
        return out
    }

    /** A and AAAA addresses in a response, in the same text form the /proc/net parser uses. */
    fun addresses(msg: ByteArray): List<String> = runCatching {
        if (msg.size < 12) return emptyList()
        val questions = Packets.u16(msg, 4)
        val answers = Packets.u16(msg, 6)
        var i = 12
        repeat(questions) { i = readName(msg, i).second + 4 }
        val out = mutableListOf<String>()
        repeat(answers) {
            i = readName(msg, i).second
            val type = Packets.u16(msg, i)
            val len = Packets.u16(msg, i + 8)
            val data = i + 10
            when {
                type == TYPE_A && len == 4 -> out += (0 until 4).joinToString(".") { (msg[data + it].toInt() and 0xFF).toString() }
                type == TYPE_AAAA && len == 16 -> out += Parsers.ipv6(msg.copyOfRange(data, data + 16))
            }
            i = data + len
        }
        out
    }.getOrDefault(emptyList())

    /** Reads a possibly-compressed name; returns it and the offset just past it. */
    fun readName(msg: ByteArray, start: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var i = start
        var end = -1
        var jumps = 0
        while (true) {
            val len = msg[i].toInt() and 0xFF
            when {
                len == 0 -> {
                    if (end < 0) end = i + 1
                    break
                }
                len and 0xC0 == 0xC0 -> {
                    if (end < 0) end = i + 2
                    if (++jumps > 16) error("compression loop")
                    i = ((len and 0x3F) shl 8) or (msg[i + 1].toInt() and 0xFF)
                }
                else -> {
                    labels += String(msg, i + 1, len, Charsets.US_ASCII)
                    i += len + 1
                }
            }
        }
        return labels.joinToString(".") to end
    }
}

/** What the DNS monitor saw, kept in memory so the log can catch up when it's next visible. */
object DnsLog {
    /** [seq] is set by [add]: lookups are answered on several threads, so times can arrive out of order. */
    data class Lookup(val time: Long, val uid: Int, val name: String, val addresses: List<String>, val seq: Long = 0)

    val running = MutableStateFlow(false)

    private val lookups = ArrayDeque<Lookup>()
    private var nextSeq = 1L
    private val hosts = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 4096
    }

    @Synchronized
    fun add(lookup: Lookup) {
        lookups.addLast(lookup.copy(seq = nextSeq++))
        while (lookups.size > 2000) lookups.removeFirst()
        lookup.addresses.forEach { hosts[it] = lookup.name }
    }

    /** Lookups added after the one numbered [seq] (0: all that are kept). */
    @Synchronized
    fun since(seq: Long): List<Lookup> = lookups.filter { it.seq > seq }

    /** The number of the newest lookup, to skip everything up to now. */
    @Synchronized
    fun latestSeq(): Long = nextSeq - 1

    /** The name an app looked up to get [ip], if the monitor saw it. */
    @Synchronized
    fun host(ip: String): String? = hosts[ip]
}
