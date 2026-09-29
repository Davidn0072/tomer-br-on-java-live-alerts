package com.livealerts.emulator;

import java.time.Instant;

/** Minimal timestamped console logging — no framework needed for a process this small. */
final class Log {

    private Log() {
    }

    static void info(String message) {
        print("INFO", message);
    }

    static void warn(String message) {
        print("WARN", message);
    }

    private static void print(String level, String message) {
        System.out.println(Instant.now() + " [" + level + "] " + message);
    }
}
