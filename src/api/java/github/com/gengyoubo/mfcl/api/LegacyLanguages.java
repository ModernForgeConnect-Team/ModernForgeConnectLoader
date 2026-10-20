package github.com.gengyoubo.mfcl.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Translation fixes for the legacy domain. Language codes are case-insensitive
 * ({@code zh_cn} and {@code zh_CN} are the same language). Changes take effect on the next language sync;
 * a later value for the same key replaces an earlier one.
 */
public interface LegacyLanguages {
    /**
     * Fills only keys the original language files of that language do not define, so original
     * translations win; the en_US fallback text 1.7.10 shows for such keys is replaced.
     */
    void supplement(String language, Map<String, String> entries);

    /** Replaces original translations, for keys the original language files translate incorrectly. */
    void override(String language, Map<String, String> entries);

    /** {@link #supplement(String, Map)} from a 1.7.10 {@code .lang} stream. */
    default void supplement(String language, InputStream langFile) throws IOException { supplement(language, parse(langFile)); }

    /** {@link #override(String, Map)} from a 1.7.10 {@code .lang} stream. */
    default void override(String language, InputStream langFile) throws IOException { override(language, parse(langFile)); }

    /** Keys defined by the original en_US files but not by the original files of {@code language}. Supplements are not counted. */
    Set<String> missingKeys(String language);

    /** Parses a 1.7.10 {@code .lang} stream: UTF-8, {@code key=value}, {@code #} comments. */
    static Map<String, String> parse(InputStream langFile) throws IOException {
        var entries = new LinkedHashMap<String, String>();
        for (String line : new String(langFile.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
            if (line.startsWith("﻿")) line = line.substring(1);
            int split = line.indexOf('=');
            if (line.isBlank() || line.startsWith("#") || split <= 0) continue;
            entries.put(line.substring(0, split).trim(), line.substring(split + 1));
        }
        return entries;
    }
}
