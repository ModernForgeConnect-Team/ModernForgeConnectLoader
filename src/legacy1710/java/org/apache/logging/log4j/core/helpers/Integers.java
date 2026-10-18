package org.apache.logging.log4j.core.helpers;

/** Binary compatibility for FML 1614 against modern Log4j; the beta9 implementation is no longer shipped. */
public final class Integers {
    private Integers() {}
    public static int parseInt(String value, int defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : Integer.parseInt(value);
    }
    public static int parseInt(String value) { return parseInt(value, 0); }
}
