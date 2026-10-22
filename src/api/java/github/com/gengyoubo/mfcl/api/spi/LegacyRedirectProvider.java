package github.com.gengyoubo.mfcl.api.spi;

import github.com.gengyoubo.mfcl.api.LegacyRedirects;

/**
 * Implemented by an addon and declared in {@code META-INF/services/github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider}.
 * MFCL calls every provider once from its own mod constructor, before the old runtime boots, so the provider runs
 * before the addon's own constructor and must not depend on it. A provider that throws is skipped and logged.
 */
public interface LegacyRedirectProvider {
    void register(LegacyRedirects redirects);
}
