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


import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.win.Kernel32;
import org.fractalmicro.win.Registry;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The facts the About window shows. The processor line is put together the way Apple
 * writes it: how many cores, how fast, who made it, and which family it belongs to,
 * for example "6-Core 2.5 GHz Intel Core i5".
 */
public final class SystemProfile {
    private SystemProfile() {}

    /** What the system is called. One word, and the same word in every language. */
    public static final String OS_NAME = "FractalJDE";

    /** The rest of the name, and who makes it, which are sentences and are translated. */
    public static String longName() {
        return FMLocalized.of(FMString.of("system.longName")).toString();
    }

    public static String vendor() {
        return FMLocalized.of(FMString.of("system.vendor")).toString();
    }

    public static String version() { return Version.number(); }
    public static String build() { return Version.build(); }

    private static final String CPU_KEY = "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0";

    private static final Pattern FAMILY = Pattern.compile(
        "(Core Ultra \\d|Core i\\d|Core 2 Duo|Core 2 Quad|Core Duo|Core Solo|"
      + "Xeon|Pentium|Celeron|Atom|Ryzen Threadripper|Ryzen \\d|Athlon|EPYC|FX)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern SPEED_IN_NAME = Pattern.compile("@\\s*([0-9.]+)\\s*GHz",
        Pattern.CASE_INSENSITIVE);

    /** For example "6-Core 2.5 GHz Intel Core i5". */
    public static String processor() {
        String raw = Registry.string(Registry.HKEY_LOCAL_MACHINE, CPU_KEY, "ProcessorNameString");
        int cores = Kernel32.physicalCores();
        String name = raw == null ? "" : raw.replace("(R)", "").replace("(TM)", "").trim();

        String vendor = vendorFrom(name);
        String family = familyFrom(name);
        String speed = speedFrom(name);

        StringBuilder sb = new StringBuilder();
        if (cores > 1) sb.append(cores).append("-Core ");
        if (!speed.isEmpty()) sb.append(speed).append(' ');
        if (!vendor.isEmpty()) sb.append(vendor).append(' ');
        sb.append(family.isEmpty() ? "Processor" : family);
        return sb.toString().trim();
    }

    private static String vendorFrom(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("intel")) return "Intel";
        if (lower.contains("amd") || lower.contains("ryzen")) return "AMD";
        if (lower.contains("qualcomm") || lower.contains("snapdragon")) return "Qualcomm";
        if (lower.contains("arm")) return "ARM";
        String identifier = System.getenv("PROCESSOR_IDENTIFIER");
        if (identifier != null && identifier.contains("GenuineIntel")) return "Intel";
        if (identifier != null && identifier.contains("AuthenticAMD")) return "AMD";
        return "";
    }

    private static String familyFrom(String name) {
        Matcher m = FAMILY.matcher(name);
        if (!m.find()) return "";
        String family = m.group(1);
        // Keep Apple's capitalisation: "Core i5", not "CORE I5".
        return family.substring(0, 1).toUpperCase(Locale.ROOT)
             + family.substring(1).replace("I", "i").replace("CORE", "Core");
    }

    private static String speedFrom(String name) {
        Matcher m = SPEED_IN_NAME.matcher(name);
        if (m.find()) return m.group(1) + " GHz";
        int mhz = Registry.dword(Registry.HKEY_LOCAL_MACHINE, CPU_KEY, "~MHz", 0);
        if (mhz <= 0) return "";
        double ghz = Math.round(mhz / 100.0) / 10.0;
        return (ghz == Math.floor(ghz) ? String.format("%.1f", ghz) : String.valueOf(ghz)) + " GHz";
    }

    /** For example "32 GB". */
    public static String memory() {
        long bytes = Kernel32.totalMemory();
        if (bytes <= 0) return "Unknown";
        double gb = bytes / (1024.0 * 1024 * 1024);
        long rounded = Math.round(gb);
        return rounded + " GB";
    }

    public static String startupDisk() {
        return org.fractalmicro.fs.Volumes.startupDisk().name;
    }

    public static String computerName() {
        String name = System.getenv("COMPUTERNAME");
        return name == null || name.isBlank() ? "Fractal" : name;
    }
}
