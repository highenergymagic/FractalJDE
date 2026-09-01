/*CDDL HEADER START
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 * You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
 * or http://illumos.org/license/CDDL.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at usr/src/OPENSOLARIS.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: 
 * 
 * CDDL HEADER END
 * Copyright (C) 2026 by Fractal Microsystems, Inc.
 * Use is subject to license terms.
 */
package org.fractalmicro.os;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

/**
 * What version this is.
 *
 * The number is semantic and lives in version.properties, bumped by hand or by the
 * build script. The build number is the moment it was built, written as yymmddhhmmss
 * and then in hexadecimal, so it always rises and stays short: a build at
 * 30 August 2026, 21:15:00 is 260830211500, which is 3CBBF2CD8EC.
 */
public final class Version {
    private Version() {}

    private static final String RESOURCE = "/org/fractalmicro/resources/version.properties";
    private static final String FALLBACK_NUMBER = "1.0.8";

    private static String number;
    private static String build;
    private static String builtAt;

    private static synchronized void load() {
        if (number != null) return;
        Properties props = new Properties();

        try (InputStream in = Version.class.getResourceAsStream(RESOURCE)) {
            if (in != null) props.load(in);
        } catch (Exception ignored) { }

        if (props.getProperty("version") == null) {
            // Running from a class directory rather than the jar: read the source of truth.
            Path file = Paths.get("version.properties");
            if (Files.isReadable(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(in);
                } catch (Exception ignored) { }
            }
        }

        number = props.getProperty("version", FALLBACK_NUMBER).trim();
        build = props.getProperty("build", "").trim();
        builtAt = props.getProperty("built", "").trim();

        if (build.isEmpty()) {
            // An unstamped build is being run straight from the compiler; stamp it now.
            long now = System.currentTimeMillis();
            build = buildNumber(now);
            builtAt = timestamp(now);
        }
    }

    /** For example "1.0.8". */
    public static String number() {
        load();
        return number;
    }

    /** For example "3CBBF2CD8EC". */
    public static String build() {
        load();
        return build;
    }

    /** When it was built, as plain text. */
    public static String builtAt() {
        load();
        return builtAt;
    }

    /** "1.0.8 (3CBBF2CD8EC)". */
    public static String full() {
        return number() + " (" + build() + ")";
    }

    /** yymmddhhmmss read as a number, in hexadecimal. */
    public static String buildNumber(long epochMillis) {
        String stamp = new SimpleDateFormat("yyMMddHHmmss")
            .format(new Date(epochMillis));
        return Long.toHexString(Long.parseLong(stamp)).toUpperCase(Locale.ROOT);
    }

    /** Turns a build number back into the time it was made, for checking. */
    public static String decodeBuild(String hex) {
        try {
            String stamp = Long.toString(Long.parseLong(hex, 16));
            if (stamp.length() != 12) return "";
            return "20" + stamp.substring(0, 2) + "-" + stamp.substring(2, 4) + "-"
                 + stamp.substring(4, 6) + " " + stamp.substring(6, 8) + ":"
                 + stamp.substring(8, 10) + ":" + stamp.substring(10, 12);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static String timestamp(long epochMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(epochMillis));
    }
}
