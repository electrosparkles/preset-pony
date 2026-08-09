package com.electrosparkles.presetpony;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the app version from config/app-version.properties, which lives in
 * src/main/resources and is populated at build time:
 *   - Maven filters the ${app.version} token from version.properties in the project root.
 *   - Windows bat scripts write the literal value read from version.properties before
 *     copying resources into build/, producing an identical file by a different route.
 *
 * Falls back to "dev" if the resource is missing or unreadable (e.g. running directly
 * from source without a build step).
 */
public final class AppVersion {

    private static final String RESOURCE = "/config/app-version.properties";
    private static final String FALLBACK  = "dev";

    private static final String VERSION = load();

    private AppVersion() {}

    public static String get() {
        return VERSION;
    }

    private static String load() {
        try (InputStream in = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return FALLBACK;
            Properties props = new Properties();
            props.load(in);
            String v = props.getProperty("app.version", "").trim();
            // If the token was never substituted (running straight from source),
            // treat it as unresolved and return the fallback.
            return (v.isEmpty() || v.startsWith("${")) ? FALLBACK : v;
        } catch (IOException e) {
            return FALLBACK;
        }
    }
}
