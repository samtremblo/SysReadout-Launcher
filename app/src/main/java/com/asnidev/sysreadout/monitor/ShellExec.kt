package com.asnidev.sysreadout.monitor

import java.io.IOException
import java.io.Reader
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bumped whenever [IShellService]'s behaviour changes, so an older running helper gets replaced. */
object ShellProtocol {
    const val VERSION = 3 // 3: commands and paths are allow-listed (ShellPolicy)
}

/**
 * The only commands and files the Shizuku helper will touch. It runs as the
 * shell user, so it refuses anything else even though only SysReadout can
 * reach it: no setting, backup or intent extra can ever become a shell command.
 * Keep in step with the callers in LogEngine and ShizukuSetup (see ShizukuTest).
 */
object ShellPolicy {
    private const val PKG = "[A-Za-z0-9_.]+"
    private const val COMPONENT = "$PKG/[A-Za-z0-9_.$]+"

    private val commands = listOf(
        "top -b -n 1 -q -o PID,UID,%CPU,RES,NAME",
        "dumpsys (power|thermalservice|batterystats --usage)",
        "logcat -d -v epoch -t 12 '\\*:[WE]'",
        "logcat -d -v epoch -T [0-9]+\\.[0-9]{3} '\\*:[WE]' \\| tail -n 60",
        "appops set --user [0-9]+ $PKG GET_USAGE_STATS allow",
        "cmd notification allow_listener $COMPONENT [0-9]+",
        "settings --user [0-9]+ get secure enabled_accessibility_services",
        "settings --user [0-9]+ put secure enabled_accessibility_services '[A-Za-z0-9_.$/:-]*'" +
            " && settings --user [0-9]+ put secure accessibility_enabled 1",
    ).map { Regex(it) }

    /** /proc files only, by plain names: no "..", no symlink tricks through other directories. */
    private val path = Regex("/proc/[a-z0-9_]+(/[a-z0-9_]+)*")

    fun allowsCommand(command: String): Boolean = commands.any { it.matches(command) }

    fun allowsPath(p: String): Boolean = path.matches(p)
}

/**
 * What the Shizuku helper does, free of Android classes so it can be unit-tested.
 * Every call is bounded in time and a failure is null, never a plausible-looking
 * empty answer: an empty process list would read as "every app just exited".
 */
object ShellExec {

    /** Longest output passed back; binder replies over ~1 MB fail outright. */
    const val MAX_OUTPUT = 512 * 1024

    private fun daemons(name: String) = ThreadFactory { r -> Thread(r, name).apply { isDaemon = true } }

    // Commands and lookups get separate threads so slow DNS can never starve `top`.
    private val readers: ExecutorService = Executors.newCachedThreadPool(daemons("shell-read"))
    private val lookups = ThreadPoolExecutor(
        4, 4, 30, TimeUnit.SECONDS, ArrayBlockingQueue(64), daemons("shell-dns"), ThreadPoolExecutor.AbortPolicy(),
    )

    /** stdout+stderr of `sh -c command`, or null when it can't start or runs past [timeoutMs]. */
    fun run(command: String, timeoutMs: Long): String? {
        val process = try {
            ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        } catch (e: IOException) {
            return null
        }
        val output = readers.submit<String> { process.inputStream.bufferedReader().use { readCapped(it, MAX_OUTPUT) } }
        return try {
            output.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) { // timeout, interrupted, read failure
            null
        } finally {
            process.destroyForcibly()
            output.cancel(true)
        }
    }

    /** Reverse-DNS names for [ips] found within [budgetMs] in total; the rest are left out. */
    fun resolve(ips: List<String>, budgetMs: Long, lookup: (String) -> String? = ::reverse): Map<String, String> {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs)
        val jobs = ArrayList<Pair<String, Future<String?>>>()
        for (ip in ips.distinct()) {
            // A full queue means earlier lookups are still hanging: skip rather than wait.
            val job = runCatching { lookups.submit<String?> { lookup(ip) } }.getOrNull() ?: continue
            jobs += ip to job
        }
        val out = LinkedHashMap<String, String>()
        for ((ip, job) in jobs) {
            val left = deadline - System.nanoTime()
            val host = if (left <= 0) null else runCatching { job.get(left, TimeUnit.NANOSECONDS) }.getOrNull()
            if (host == null) job.cancel(true) else out[ip] = host
        }
        return out
    }

    fun shutdown() {
        readers.shutdownNow()
        lookups.shutdownNow()
    }

    private fun reverse(ip: String): String? = InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }

    /** Keeps the first [limit] chars and drains the rest so the process can finish. */
    internal fun readCapped(reader: Reader, limit: Int): String {
        val out = StringBuilder()
        val buf = CharArray(8192)
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            val room = limit - out.length
            if (room > 0) out.appendRange(buf, 0, minOf(n, room))
        }
        return out.toString()
    }
}
