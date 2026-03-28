package space.controlnet.chatmc.common.testing;

public final class ThreadConfinementAssertions {
    private ThreadConfinementAssertions() {
        throw new AssertionError("No instances.");
    }

    public static void assertSameThread(String assertionName, Thread expected, Thread actual) {
        if (expected == null) {
            throw new AssertionError(assertionName + " -> expected thread must not be null");
        }
        if (actual == null) {
            throw new AssertionError(assertionName + " -> actual thread must not be null");
        }
        if (expected != actual) {
            throw new AssertionError(assertionName
                    + " -> expected thread='" + expected.getName() + "'"
                    + ", actual thread='" + actual.getName() + "'");
        }
    }
}
