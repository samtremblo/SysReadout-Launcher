package com.asnidev.sysreadout.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPolicyTest {

    private val pkg = "com.sysreadout.app.debug"

    @Test
    fun everyCommandSysReadoutSendsIsAllowed() {
        listOf(
            "top -b -n 1 -q -o PID,UID,%CPU,RES,NAME",
            "dumpsys power",
            "dumpsys thermalservice",
            "dumpsys batterystats --usage",
            "logcat -d -v epoch -t 12 '*:E'",
            "logcat -d -v epoch -T 1795489351.783 '*:W' | tail -n 60",
            "appops set --user 0 $pkg GET_USAGE_STATS allow",
            "cmd notification allow_listener ${ShizukuSetup.listenerComponent(pkg)} 0",
            "settings --user 10 get secure enabled_accessibility_services",
            "settings --user 0 put secure enabled_accessibility_services " +
                "'${ShizukuSetup.withService("com.other/.Svc", ShizukuSetup.lockComponent(pkg))}'" +
                " && settings --user 0 put secure accessibility_enabled 1",
        ).forEach { assertTrue(it, ShellPolicy.allowsCommand(it)) }
    }

    @Test
    fun anythingElseIsRefused() {
        listOf(
            "rm -rf /sdcard",
            "dumpsys power; rm -rf /sdcard",
            "top -b -n 1 -q -o PID,UID,%CPU,RES,NAME && id",
            "logcat -d -v epoch -t 12 '*:E' > /sdcard/x",
            "appops set --user 0 com.x;id GET_USAGE_STATS allow",
            "settings --user 0 put secure enabled_accessibility_services 'a'; id; ' && settings --user 0 put secure accessibility_enabled 1",
        ).forEach { assertFalse(it, ShellPolicy.allowsCommand(it)) }
    }

    @Test
    fun onlyPlainProcPaths() {
        listOf("/proc/net/tcp", "/proc/net/udp6", "/proc/loadavg", "/proc/stat").forEach {
            assertTrue(it, ShellPolicy.allowsPath(it))
        }
        listOf("/proc/../data/system/x", "/data/system/packages.xml", "/proc/self/../../etc", "/sdcard/a", "proc/stat").forEach {
            assertFalse(it, ShellPolicy.allowsPath(it))
        }
    }
}
