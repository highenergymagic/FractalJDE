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

import org.fractalmicro.bundle.Dyld;
import org.fractalmicro.os.OSPaths;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * sh: the shell, which is how anything on this volume gets typed.
 *
 * CreateProcess wants a PE and every program here is a Mach-O, so the only thing a foreign
 * shell could ever run was a batch file standing in front of one. This runs the image the
 * way the desktop does, by handing it to the loader.
 */
public final class Sh {
    private Sh() {}

    /** Where a bare name is looked for, in the order a Mac looks. */
    private static final String[] PATH = {"usr/bin", "bin"};

    private static File here = new File(System.getProperty("user.dir"));
    private static int status;

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "-c".equals(args[0])) {
            System.exit(run(words(args[1])));
        }
        BufferedReader lines = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print(prompt());
            System.out.flush();
            String line = lines.readLine();
            if (line == null) break;
            List<String> said = words(line);
            if (said.isEmpty()) continue;
            if ("exit".equals(said.get(0))) break;
            status = run(said);
        }
        System.exit(status);
    }

    /** `machine:folder user$ `, which is what a Mac shows and in that order. */
    private static String prompt() {
        String home = System.getProperty("user.home", "");
        String where = here.getAbsolutePath();
        if (!home.isEmpty() && where.startsWith(home)) {
            where = "~" + where.substring(home.length());
        }
        return org.fractalmicro.os.SystemProfile.computerName()
             + ":" + where + " " + OSPaths.shortName() + "$ ";
    }

    /* ------------------------------------------------------------- running */

    /**
     * One command: a built-in, a program on the volume, or the host's.
     *
     * The volume comes first. A machine that has its own mdfind should not be the one that
     * answers when somebody standing in this system types mdfind.
     */
    private static int run(List<String> said) {
        String name = said.get(0);
        switch (name) {
            case "cd" -> { return changeDirectory(said); }
            case "pwd" -> {
                System.out.println(here.getAbsolutePath());
                return 0;
            }
            default -> { }
        }

        File image = lookUp(name);
        List<String> command = image != null
            ? Dyld.commandFor(image, said.subList(1, said.size()))
            : said;
        if (image == null && !name.contains("/") && !name.contains(File.separator)) {
            // Nothing here has that name, so it is the machine underneath being asked.
            // Through its own shell, since half of what a person types there is built into
            // it and would not be found as a file either.
            command = List.of("cmd.exe", "/c", String.join(" ", said));
        }
        return start(command);
    }

    private static int start(List<String> command) {
        try {
            Process ran = new ProcessBuilder(command).directory(here).inheritIO().start();
            return ran.waitFor();
        } catch (java.io.IOException wouldNotStart) {
            System.err.println("sh: " + command.get(0) + ": " + wouldNotStart.getMessage());
            return 127;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    /**
     * The program a name means, or nothing when the volume has none.
     *
     * A name with a separator in it is a path and is taken as one, the way every shell
     * does it. Anything else is looked for where the volume keeps its programs.
     */
    private static File lookUp(String name) {
        if (name.contains("/") || name.contains(File.separator)) {
            File named = resolve(name);
            return named.isFile() ? named : null;
        }
        for (String folder : PATH) {
            File found = OSPaths.ROOT.resolve(folder).resolve(name).toFile();
            if (found.isFile()) return found;
        }
        return null;
    }

    private static int changeDirectory(List<String> said) {
        String where = said.size() > 1 ? said.get(1) : System.getProperty("user.home", ".");
        File going = resolve(where);
        if (!going.isDirectory()) {
            System.err.println("cd: " + where + ": no such folder");
            return 1;
        }
        here = going.getAbsoluteFile();
        return 0;
    }

    private static File resolve(String where) {
        File named = new File(where);
        return named.isAbsolute() ? named : new File(here, where);
    }

    /* ------------------------------------------------------------- reading */

    /**
     * A line as the words it holds, with quoted runs kept whole.
     *
     * Enough of a shell's reading to name a file with a space in it. Not enough to be one:
     * there is no expansion here, and a line that wanted any is the host's to read.
     */
    private static List<String> words(String line) {
        List<String> said = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0; else word.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (!word.isEmpty()) { said.add(word.toString()); word.setLength(0); }
            } else {
                word.append(c);
            }
        }
        if (!word.isEmpty()) said.add(word.toString());
        return said;
    }
}
