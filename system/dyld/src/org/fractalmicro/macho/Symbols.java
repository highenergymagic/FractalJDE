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
package org.fractalmicro.macho;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * What a body of code defines, and what it expects to find elsewhere.
 *
 * A linker cannot take anyone's word for this. It reads the code and works out two sets:
 * the symbols defined here, which anything linking this may use, and the symbols mentioned
 * here but defined somewhere else, which have to be found in something this links or the
 * image will not load. On a Mach-O those become the external and undefined halves of the
 * symbol table.
 *
 * For this system a symbol is a class, and the code is class files inside a zip. Every
 * class file carries a constant pool, and every class it mentions is in there as a
 * CONSTANT_Class entry: the compiler has already done the work of listing what a class
 * depends on, and the pool is where it wrote it down.
 *
 * Reading the pool means walking it entry by entry, because entries are not fixed width
 * and there is no index. Long and double take two slots each, a rule from the first version
 * of the format that everything since has had to keep.
 */
public final class Symbols {
    private Symbols() {}

    private static final int MAGIC = 0xCAFEBABE;

    private static final int UTF8 = 1, INTEGER = 3, FLOAT = 4, LONG = 5, DOUBLE = 6;
    private static final int CLASS = 7, STRING = 8, FIELD_REF = 9, METHOD_REF = 10;
    private static final int INTERFACE_METHOD_REF = 11, NAME_AND_TYPE = 12;
    private static final int METHOD_HANDLE = 15, METHOD_TYPE = 16;
    private static final int DYNAMIC = 17, INVOKE_DYNAMIC = 18, MODULE = 19, PACKAGE = 20;

    /** What one body of code defines and what it expects to be given. */
    public record Set2(Set<String> defined, Set<String> referenced) {}

    /**
     * Reads a zip of class files and answers what it defines and what it mentions.
     *
     * A class mentioning itself, or a class defined in the same zip, is not a reference to
     * anywhere else, so the referenced set has the defined set taken out of it at the end.
     */
    public static Set2 of(byte[] codeResource) throws IOException {
        Set<String> defined = new LinkedHashSet<>();
        Set<String> referenced = new LinkedHashSet<>();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(codeResource))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class")) continue;
                defined.add(name.substring(0, name.length() - ".class".length())
                                .replace('/', '.'));
                readPool(zip.readAllBytes(), referenced);
            }
        }
        referenced.removeAll(defined);
        return new Set2(defined, referenced);
    }

    /**
     * Collects every class named in one class file's constant pool.
     *
     * A CONSTANT_Class entry points at a Utf8 entry holding the name, and that name may be
     * an array descriptor rather than a class: [Ljava/lang/String; names an array type, and
     * what is wanted from it is the element. Anything unreadable is skipped rather than
     * thrown, because a linker refusing to run over one odd file would be worse than a
     * linker that finds one symbol fewer.
     */
    private static void readPool(byte[] classFile, Set<String> out) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classFile))) {
            if (in.readInt() != MAGIC) return;
            in.readUnsignedShort();                       // minor version
            in.readUnsignedShort();                       // major version
            int count = in.readUnsignedShort();

            String[] strings = new String[count];
            int[] classNameAt = new int[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case UTF8 -> strings[i] = in.readUTF();
                    case CLASS -> classNameAt[i] = in.readUnsignedShort();
                    case STRING, METHOD_TYPE, MODULE, PACKAGE -> in.readUnsignedShort();
                    case METHOD_HANDLE -> skip(in, 3);
                    case INTEGER, FLOAT, FIELD_REF, METHOD_REF, INTERFACE_METHOD_REF,
                         NAME_AND_TYPE, DYNAMIC, INVOKE_DYNAMIC -> skip(in, 4);
                    case LONG, DOUBLE -> {
                        skip(in, 8);
                        i++;                              // takes two slots, from 1995
                    }
                    default -> { return; }                // an entry we do not know
                }
            }
            for (int i = 1; i < count; i++) {
                if (classNameAt[i] == 0) continue;
                String name = strings[classNameAt[i]];
                if (name != null) out.add(className(name));
            }
        } catch (IOException | RuntimeException unreadable) {
            // Not a class file, or not one this understands. Nothing to take from it.
        }
    }

    /** The class an entry names, with any array wrapping taken off. */
    private static String className(String raw) {
        String name = raw;
        while (name.startsWith("[")) name = name.substring(1);
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        return name.replace('/', '.');
    }

    private static void skip(InputStream in, int bytes) throws IOException {
        if (in.skip(bytes) != bytes) throw new IOException("the constant pool ends early");
    }
}
