package com.electrosparkles.presetpony;

import java.util.Objects;

/** Tiny assertion helper - no JUnit/framework dependency, keeps the offline-friendly no-build-tool workflow. */
public final class TestAssertions {
    private static int passed = 0;
    private static int failed = 0;

    private TestAssertions() {
    }

    public static void assertEquals(Object expected, Object actual, String testName) {
        if (Objects.equals(expected, actual)) {
            pass(testName);
        } else {
            fail(testName, "expected <" + expected + "> but got <" + actual + ">");
        }
    }

    public static void assertTrue(boolean condition, String testName) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName, "expected true, got false");
        }
    }

    public static void assertThrows(Class<? extends Throwable> expectedType, Runnable action, String testName) {
        try {
            action.run();
            fail(testName, "expected " + expectedType.getSimpleName() + " but nothing was thrown");
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) {
                pass(testName);
            } else {
                fail(testName, "expected " + expectedType.getSimpleName() + " but got " + t.getClass().getSimpleName()
                        + ": " + t.getMessage());
            }
        }
    }

    private static void pass(String testName) {
        passed++;
        System.out.println("  PASS  " + testName);
    }

    private static void fail(String testName, String detail) {
        failed++;
        System.out.println("  FAIL  " + testName + "\n        " + detail);
    }

    public static void section(String name) {
        System.out.println("\n== " + name + " ==");
    }

    /** Prints the pass/fail summary and exits with non-zero status if anything failed (useful for scripting). */
    public static void summarizeAndExit() {
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
