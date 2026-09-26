package org.lwjgl.openal.jsound;

/** Minimal logging to stdout/stderr, captured by the launcher alongside game logs. */
final class JSoundLog {

    private JSoundLog() {
    }

    static void info(String msg) {
        System.out.println("[MioJSound] " + msg);
    }

    static void error(String msg) {
        System.err.println("[MioJSound] " + msg);
    }

    static void error(String msg, Throwable t) {
        System.err.println("[MioJSound] " + msg);
        t.printStackTrace();
    }
}