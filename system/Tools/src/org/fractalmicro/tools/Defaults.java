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
package org.fractalmicro.tools;

import org.fractalmicro.foundation.FMDictionary;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.os.FMUserDefaults;

import java.util.List;
import java.util.Map;

/**
 * defaults: read and write the preferences, from a command line.
 *
 * The same files the programs read, so a value written here is the value the Dock sees.
 * That is the whole use of a preference being a plist in a known place rather than
 * something a program keeps to itself.
 */
public final class Defaults {
    private Defaults() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "read" -> read(rest(args));
            case "write" -> write(rest(args));
            case "delete" -> delete(rest(args));
            case "domains" -> domains();
            default -> usage();
        }
    }

    /* ------------------------------------------------------------- reading */

    private static void read(List<String> args) {
        if (args.isEmpty()) {
            for (FMString domain : FMUserDefaults.domains()) {
                System.out.println(domain + " " + written(
                    FMUserDefaults.of(domain).dictionaryRepresentation(), 0));
            }
            return;
        }
        FMUserDefaults domain = FMUserDefaults.of(FMString.of(args.get(0)));
        if (args.size() == 1) {
            System.out.println(written(domain.dictionaryRepresentation(), 0));
            return;
        }
        Object value = domain.get(FMString.of(args.get(1)));
        if (value == null) {
            System.err.println("The domain/default pair of (" + args.get(0) + ", "
                               + args.get(1) + ") does not exist");
            System.exit(1);
        }
        System.out.println(written(value, 0));
    }

    /* ------------------------------------------------------------- writing */

    private static void write(List<String> args) {
        if (args.size() < 3) {
            System.err.println("usage: defaults write domain key [-bool|-int|-string] value");
            System.exit(64);
            return;
        }
        String said = args.get(args.size() - 1);
        String kind = args.size() >= 4 ? args.get(2) : "";
        FMUserDefaults.of(FMString.of(args.get(0)))
                      .set(FMString.of(args.get(1)), valueOf(kind, said));
    }

    /**
     * What the words on the command line mean, as a value.
     *
     * With a type named, that type. Without one, what it looks like: true and false are
     * truths, digits are a number, and everything else is text. That is what defaults
     * does, and a script that writes 1 for a switch expects it to come back as a switch.
     */
    private static Object valueOf(String kind, String said) {
        switch (kind) {
            case "-bool", "-boolean" -> {
                return "true".equalsIgnoreCase(said) || "yes".equalsIgnoreCase(said)
                       || "1".equals(said);
            }
            case "-int", "-integer" -> {
                return Long.parseLong(said);
            }
            case "-float" -> {
                return Double.parseDouble(said);
            }
            case "-string" -> {
                return said;
            }
            default -> { }
        }
        if ("true".equalsIgnoreCase(said) || "false".equalsIgnoreCase(said)) {
            return Boolean.parseBoolean(said);
        }
        try {
            return Long.parseLong(said);
        } catch (NumberFormatException notANumber) {
            return said;
        }
    }

    private static void delete(List<String> args) {
        if (args.isEmpty()) {
            System.err.println("usage: defaults delete domain [key]");
            System.exit(64);
            return;
        }
        FMUserDefaults domain = FMUserDefaults.of(FMString.of(args.get(0)));
        if (args.size() == 1) {
            for (FMString key : domain.dictionaryRepresentation().keys()) {
                domain.remove(key);
            }
            return;
        }
        domain.remove(FMString.of(args.get(1)));
    }

    private static void domains() {
        StringBuilder line = new StringBuilder();
        for (FMString one : FMUserDefaults.domains()) {
            if (!line.isEmpty()) line.append(", ");
            line.append(one);
        }
        System.out.println(line);
    }

    /* ------------------------------------------------------------ printing */

    /**
     * A value the way defaults prints one, which is the old plist and not the XML.
     *
     * The files are XML, as everything else on this volume is. What a person reads at a
     * command line is not, and never was: braces, semicolons, and no header.
     */
    private static String written(Object value, int depth) {
        String pad = "    ".repeat(depth + 1);
        String closing = "    ".repeat(depth);
        if (value instanceof FMDictionary said) {
            return written(said.asMap(), depth);
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "{\n" + closing + "}";
            StringBuilder out = new StringBuilder("{\n");
            for (Map.Entry<?, ?> one : map.entrySet()) {
                out.append(pad).append(one.getKey()).append(" = ")
                   .append(written(one.getValue(), depth + 1)).append(";\n");
            }
            return out.append(closing).append('}').toString();
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return "(\n" + closing + ")";
            StringBuilder out = new StringBuilder("(\n");
            for (Object one : list) {
                out.append(pad).append(written(one, depth + 1)).append(",\n");
            }
            return out.append(closing).append(')').toString();
        }
        if (value instanceof Boolean truth) return truth ? "1" : "0";
        return String.valueOf(value);
    }

    private static List<String> rest(String[] args) {
        return List.of(args).subList(1, args.length);
    }

    private static void usage() {
        System.err.println("usage: defaults read [domain [key]]");
        System.err.println("       defaults write domain key [-bool|-int|-string] value");
        System.err.println("       defaults delete domain [key]");
        System.err.println("       defaults domains");
        System.exit(64);
    }
}
