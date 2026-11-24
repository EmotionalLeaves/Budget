package com.emotionalleaves.sprout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun reportHasClassesAndFramesButNeverMessages() {
        val secret = "STARBUCKS https://demo:demo@bridge.example.test/simplefin -12.34"
        val error = IllegalStateException(secret, IllegalArgumentException("inner $secret"))
        val report = CrashLog.format(error, "1.0.1", 1_764_000_000, "main")
        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertTrue(report.contains("Caused by: java.lang.IllegalArgumentException"))
        assertTrue(report.contains("at com.emotionalleaves.sprout.data.CrashLogTest"))
        assertFalse(report.contains("STARBUCKS"))
        assertFalse(report.contains("demo"))
        assertFalse(report.contains("12.34"))
    }

    @Test fun causeLoopsDoNotHang() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        val report = CrashLog.format(b, "1", 0, "t")
        assertEquals(2, report.lines().count { it.startsWith("java.lang.RuntimeException") || it.startsWith("Caused by") })
    }

    @Test fun readAndClear() {
        val log = CrashLog(tmp.root.resolve("crash.txt"))
        assertNull(log.read())
        tmp.root.resolve("crash.txt").writeText("report")
        assertEquals("report", log.read())
        log.clear()
        assertNull(log.read())
    }
}
