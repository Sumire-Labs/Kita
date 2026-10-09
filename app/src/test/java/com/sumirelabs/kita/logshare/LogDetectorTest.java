package com.sumirelabs.kita.logshare;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LogDetectorTest {
    @Test void recognizesMinecraftAndHytaleButNotOrdinaryTextOrGenericJavaErrors() {
        assertTrue(LogDetector.confident("[12:00:00] [Server thread/INFO]: Starting minecraft server version 1.21\n"
                + "[12:00:01] [Server thread/INFO]: Loading properties\n[12:00:02] [Server thread/INFO]: Done"));
        assertTrue(LogDetector.confident("[2026/10/09 12:00:00 INFO] [Hytale] Starting server\n"
                + "[2026/10/09 12:00:01 INFO] Loading assets\n[2026/10/09 12:00:02 INFO] Ready"));
        assertTrue(LogDetector.confident("[2026/10/09 12:00:00   INFO] [HytaleServer] Loading config\n"
                + "[2026/10/09 12:00:01   INFO] [HytaleServer] Loading assets\n[2026/10/09 12:00:02   INFO] Ready"));
        assertTrue(LogDetector.confident("2026-10-09 12:00:00.1234|INFO|HytaleClient.Application.Program|Client started\n"
                + "2026-10-09 12:00:01.1234|INFO|HytaleClient.Application.Program|Loading assets\n"
                + "2026-10-09 12:00:02.1234|INFO|HytaleClient.Application.Program|Ready"));
        assertTrue(LogDetector.confident("---- Minecraft Crash Report ----\nDescription: Initializing game\njava.lang.RuntimeException"));
        assertFalse(LogDetector.confident("Minecraftで遊ぼう\nこんにちは\nまた明日"));
        assertFalse(LogDetector.confident("[12:00:00] INFO Web application started\n[12:00:01] ERROR RuntimeException\n at example.App.main"));
        assertFalse(LogDetector.confident("net.minecraft.server\nOne unrelated line"));
    }
    @Test void filenameAloneIsNotEnoughAndFencedTextCanBeSharedManually() {
        assertTrue(LogDetector.supported("latest.LOG"));
        assertTrue(LogDetector.supported("crash-2026.txt"));
        assertFalse(LogDetector.supported("latest.log.exe"));
        assertFalse(LogDetector.supported("latest.log.gz"));
        assertFalse(LogDetector.confident("Just a note stored in latest.log"));
        assertEquals("first\nsecond", LogDetector.unwrap("```log\nfirst\nsecond\n```"));
    }
}
