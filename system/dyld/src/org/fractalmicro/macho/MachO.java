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

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reader and writer for Mach-O, the executable format this system's programs are in.
 *
 * A program here is one file: a 64 bit Mach-O header, the usual load commands, a __TEXT
 * segment holding a little machine code, and a segment of its own, __FRACTAL, whose
 * __bytecode section holds the code resources. Those resources are a zip appended whole at
 * a page boundary, so the file reads two ways: Mach-O from the front, zip from the back.
 *
 * The layout is:
 *
 *   0            mach_header_64
 *                LC_SEGMENT_64 __PAGEZERO
 *                LC_SEGMENT_64 __TEXT      the header, the commands and the code
 *                LC_SEGMENT_64 __FRACTAL   the code resources
 *                LC_LOAD_DYLINKER          /usr/lib/dyld
 *                LC_LOAD_DYLIB             one per framework the program links against
 *                LC_UUID                   taken from the contents, so builds repeat
 *                LC_MAIN                   the offset of the entry code
 *   aligned      the entry code
 *   page aligned the code resources
 *
 * The fields are real and at the documented offsets, so any Mach-O reader will parse this.
 * See {@link #ENTRY_CODE} for what the machine code actually does.
 */
public final class MachO {

    /* --------------------------------------------------------- the constants */

    public static final int MH_MAGIC_64 = 0xFEEDFACF;
    public static final int CPU_ARCH_ABI64 = 0x01000000;
    public static final int CPU_TYPE_X86 = 7;
    public static final int CPU_TYPE_X86_64 = CPU_ARCH_ABI64 | CPU_TYPE_X86;
    public static final int CPU_SUBTYPE_X86_64_ALL = 3;
    public static final int MH_EXECUTE = 2;

    /**
     * The other kinds of Mach-O this system writes.
     *
     * Not interchangeable: a dynamic library is MH_DYLIB, a loadable bundle is MH_BUNDLE,
     * and the loader is MH_DYLINKER. Executables name the loader in LC_LOAD_DYLINKER; the
     * loader names itself with LC_ID_DYLINKER.
     */
    public static final int MH_DYLIB = 6;
    public static final int MH_DYLINKER = 7;
    public static final int MH_BUNDLE = 8;

    public static final int MH_NOUNDEFS = 0x1;
    public static final int MH_DYLDLINK = 0x4;
    public static final int MH_TWOLEVEL = 0x80;
    public static final int MH_PIE = 0x200000;

    public static final int LC_SEGMENT_64 = 0x19;
    public static final int LC_LOAD_DYLIB = 0xC;
    public static final int LC_LOAD_DYLINKER = 0xE;
    public static final int LC_UUID = 0x1B;
    public static final int LC_REQ_DYLD = 0x80000000;
    public static final int LC_MAIN = LC_REQ_DYLD | 0x28;
    /** How the loader says which loader it is. */
    public static final int LC_ID_DYLINKER = 0xF;

    /**
     * How a library says what it is called, and how an umbrella passes one on.
     *
     * A library's install name is its own idea of where it lives, and that is what a
     * client records: LC_LOAD_DYLIB is copied from the library's LC_ID_DYLIB, not from
     * where the file happened to be. An umbrella re-exports what it covers, so linking
     * CoreServices brings LaunchServices without naming it.
     */
    public static final int LC_ID_DYLIB = 0xD;
    public static final int LC_REEXPORT_DYLIB = LC_REQ_DYLD | 0x1F;

    /**
     * Where to look when a name begins with @rpath.
     *
     * A placeholder. Each image says what it stands for with LC_RPATH commands, tried in
     * order, which is what lets a library be found in the system frameworks on one machine
     * and inside an application on another without either recording an absolute path.
     */
    public static final int LC_RPATH = LC_REQ_DYLD | 0x1C;

    /**
     * The symbol table, and the description of how it is divided.
     *
     * LC_SYMTAB says where the symbols and their names are. LC_DYSYMTAB says which run of
     * them is defined here and which run is undefined, because the loader needs the second
     * run and nothing else: those are the symbols it has to go and find.
     */
    public static final int LC_SYMTAB = 0x2;
    public static final int LC_DYSYMTAB = 0xB;

    /**
     * What a symbol table entry is.
     *
     * N_UNDF means the symbol is not defined here. N_SECT means it is, in the section
     * n_sect names. N_EXT means anything linking this image may use it: a symbol without
     * it is private to the image and never resolves from outside.
     */
    public static final int N_UNDF = 0x0;
    public static final int N_SECT = 0xE;
    public static final int N_EXT = 0x01;

    /**
     * Which library an undefined symbol is expected to come from.
     *
     * The two level namespace, in one byte: which of this image's load commands supplies
     * it, counting from one. The loader looks there and nowhere else, so two libraries
     * exporting the same name is not a conflict.
     */
    public static final int SELF_LIBRARY_ORDINAL = 0;
    public static final int DYNAMIC_LOOKUP_ORDINAL = 254;
    public static final int EXECUTABLE_ORDINAL = 255;

    /**
     * What a class is called in the symbol table.
     *
     * Mach-O symbols carry a leading underscore, and Objective-C writes a class as
     * _OBJC_CLASS_$_NSString rather than as the bare name, so that a class and a function
     * of the same name are different symbols. The same idea, for the classes here.
     */
    public static final String CLASS_SYMBOL_PREFIX = "_FM_CLASS_$_";

    private static final int NLIST_SIZE = 16;
    private static final int SYMTAB_SIZE = 24;
    private static final int DYSYMTAB_SIZE = 80;

    /** The section a class is defined in: __TEXT,__text is 1, so __FRACTAL,__bytecode is 2. */
    private static final int BYTECODE_SECTION = 2;

    public static final int VM_PROT_READ = 1;
    public static final int VM_PROT_WRITE = 2;
    public static final int VM_PROT_EXECUTE = 4;

    private static final int HEADER_SIZE = 32;
    private static final int SEGMENT_SIZE = 72;
    private static final int SECTION_SIZE = 80;
    private static final int PAGE = 0x1000;
    private static final long TEXT_ADDRESS = 0x100000000L;

    /** The segment and section the code resources live in. */
    public static final String CODE_SEGMENT = "__FRACTAL";
    public static final String CODE_SECTION = "__bytecode";

    /** The dynamic loader named in LC_LOAD_DYLINKER. */
    public static final String DYLINKER = "/usr/lib/dyld";

    /**
     * The entry code: twelve bytes of x86_64 that ask the kernel to exit with 0.
     *
     *   48 c7 c0 01 00 00 02    mov  rax, 0x2000001   the exit trap
     *   48 31 ff                xor  rdi, rdi         status 0
     *   0f 05                   syscall
     *
     * That is the entire text segment. The program proper is the code resources in
     * __FRACTAL, which {@link org.fractalmicro.dyld.Dyld} maps and runs. A kernel executing
     * this file directly would get a process that exits immediately.
     */
    public static final byte[] ENTRY_CODE = {
        (byte) 0x48, (byte) 0xC7, (byte) 0xC0, 0x01, 0x00, 0x00, 0x02,
        (byte) 0x48, (byte) 0x31, (byte) 0xFF,
        0x0F, 0x05
    };

    /* ------------------------------------------------------------- writing */

    /**
     * Builds one executable.
     *
     * @param installName  what the program calls itself, for LC_ID-style naming
     * @param frameworks   the frameworks it links against, as install paths
     * @param codeResource the zip of code resources to carry in __FRACTAL,__bytecode
     */
    public static byte[] build(String installName, List<String> frameworks, byte[] codeResource) {
        return build(installName, frameworks, codeResource, MH_EXECUTE);
    }

    /**
     * The same, saying what kind of Mach-O to write.
     *
     * @param fileType {@link #MH_EXECUTE} for a program, {@link #MH_DYLIB} for a library,
     *                 {@link #MH_BUNDLE} for something loadable, {@link #MH_DYLINKER} for
     *                 the loader itself
     */
    public static byte[] build(String installName, List<String> frameworks,
                               byte[] codeResource, int fileType) {
        return build(installName, frameworks, codeResource, fileType, List.of(), List.of());
    }

    /**
     * The whole of it: what this is called, what it links, what it passes on, and where
     * it looks.
     *
     * @param installName what this calls itself. For a library this is written as
     *                    LC_ID_DYLIB and is the name a client will record when it links.
     * @param frameworks  what it links, as install names, written as LC_LOAD_DYLIB
     * @param reexported  what it passes on to anything linking it, as an umbrella does
     * @param runpaths    what @rpath stands for, in the order to try them
     */
    public static byte[] build(String installName, List<String> frameworks,
                               byte[] codeResource, int fileType,
                               List<String> reexported, List<String> runpaths) {
        return build(installName, frameworks, codeResource, fileType, reexported, runpaths,
                     List.of(), Map.of());
    }

    /**
     * The whole of it, with the symbol table that makes linking mean something.
     *
     * Without it an image says which libraries it links and no more, and the loader guesses
     * which one a class came from by asking each in turn. With it the image says which load
     * command supplies each class it uses, so the loader looks there and stops. That is the
     * two level namespace, and why two libraries may export the same name.
     *
     * @param exports what this image defines, which anything linking it may use
     * @param imports what it expects to be given, and the install name of the library it
     *                expects each from. A name from no library this links is written as a
     *                flat lookup, because there is nowhere to point it.
     */
    public static byte[] build(String installName, List<String> frameworks,
                               byte[] codeResource, int fileType,
                               List<String> reexported, List<String> runpaths,
                               List<String> exports, Map<String, String> imports) {
        List<byte[]> commands = new ArrayList<>();
        boolean library = fileType == MH_DYLIB;

        // __TEXT covers the load commands, so the sizes have to be settled first.
        int dylinkerSize = align(12 + DYLINKER.length() + 1, 8);
        int dylibSize = 0;
        for (String f : frameworks) dylibSize += align(24 + f.length() + 1, 8);
        for (String r : reexported) dylibSize += align(24 + r.length() + 1, 8);
        int idSize = library ? align(24 + installName.length() + 1, 8) : 0;
        if (fileType == MH_DYLINKER) idSize = align(12 + installName.length() + 1, 8);
        int rpathSize = 0;
        for (String r : runpaths) rpathSize += align(12 + r.length() + 1, 8);
        int commandsSize = SEGMENT_SIZE                        // __PAGEZERO
                         + SEGMENT_SIZE + SECTION_SIZE          // __TEXT with __text
                         + SEGMENT_SIZE                          // __LINKEDIT
                         + SEGMENT_SIZE + SECTION_SIZE          // __FRACTAL with __bytecode
                         + dylinkerSize
                         + idSize
                         + dylibSize
                         + rpathSize
                         + 24                                    // LC_UUID
                         + 24                                    // LC_MAIN
                         + SYMTAB_SIZE
                         + DYSYMTAB_SIZE;
        // __PAGEZERO, __TEXT, __LINKEDIT, __FRACTAL, dylinker, uuid, main, symtab,
        // dysymtab, then what it links, what it passes on, where it looks, and for a
        // library its own name.
        int commandCount = 9 + frameworks.size() + reexported.size() + runpaths.size()
                         + (library || fileType == MH_DYLINKER ? 1 : 0);

        // A library's ordinal is where its load command falls, counting from one. The
        // commands that load a library are written in this order, so this is that list.
        List<String> ordinals = new ArrayList<>(frameworks);
        ordinals.addAll(reexported);
        byte[] linkedit = symbolTable(exports, imports, ordinals);
        int symbolCount = exports.size() + imports.size();

        int entryOffset = align(HEADER_SIZE + commandsSize, 16);
        int textFileSize = entryOffset + ENTRY_CODE.length;
        long textVmSize = align(textFileSize, PAGE);

        // __LINKEDIT comes before the code rather than after it, which is the one place
        // this departs from the layout a real Mach-O has. The code resources are a zip,
        // and a zip is found by reading backwards from the end of the file: leaving it
        // last is what lets the runtime open one of these images directly, which is how
        // the loader itself is loaded before there is a loader.
        int linkeditOffset = (int) align(textFileSize, PAGE);
        long linkeditAddress = TEXT_ADDRESS + textVmSize;
        long linkeditVmSize = align(Math.max(linkedit.length, 1), PAGE);
        int codeOffset = (int) align(linkeditOffset + linkedit.length, PAGE);
        long codeAddress = linkeditAddress + linkeditVmSize;

        commands.add(segment("__PAGEZERO", 0, 0L, TEXT_ADDRESS, 0L, 0L, 0, 0, null, 0L, 0L, 0L));
        commands.add(segment("__TEXT", 1, TEXT_ADDRESS, textVmSize, 0, textFileSize,
                             VM_PROT_READ | VM_PROT_EXECUTE, VM_PROT_READ | VM_PROT_EXECUTE,
                             "__text", TEXT_ADDRESS + entryOffset, ENTRY_CODE.length, entryOffset));
        commands.add(segment("__LINKEDIT", 0, linkeditAddress, linkeditVmSize,
                             linkeditOffset, linkedit.length,
                             VM_PROT_READ, VM_PROT_READ, null, 0L, 0L, 0L));
        commands.add(segment(CODE_SEGMENT, 1, codeAddress, align(codeResource.length, PAGE),
                             codeOffset, codeResource.length,
                             VM_PROT_READ, VM_PROT_READ,
                             CODE_SECTION, codeAddress, codeResource.length, codeOffset));
        commands.add(dylinker());
        // A library says its own name first, the way a real one does. The loader says
        // its own with a command of its own: it is not a library.
        if (library) commands.add(dylib(LC_ID_DYLIB, installName));
        if (fileType == MH_DYLINKER) commands.add(pathCommand(LC_ID_DYLINKER, installName));
        for (String f : frameworks) commands.add(dylib(LC_LOAD_DYLIB, f));
        for (String r : reexported) commands.add(dylib(LC_REEXPORT_DYLIB, r));
        for (String r : runpaths) commands.add(rpath(r));
        commands.add(uuid(installName, codeResource));
        commands.add(main(entryOffset));
        commands.add(symtab(linkeditOffset, symbolCount,
                            linkeditOffset + symbolCount * NLIST_SIZE,
                            linkedit.length - symbolCount * NLIST_SIZE));
        commands.add(dysymtab(exports.size(), imports.size()));

        int actual = 0;
        for (byte[] c : commands) actual += c.length;
        if (actual != commandsSize || commands.size() != commandCount) {
            throw new IllegalStateException("the load commands do not add up: "
                + actual + " bytes in " + commands.size() + " commands, expected "
                + commandsSize + " in " + commandCount);
        }

        ByteBuffer out = ByteBuffer.allocate(codeOffset + codeResource.length)
                                   .order(ByteOrder.LITTLE_ENDIAN);
        // MH_NOUNDEFS is a promise that nothing here needs resolving. An image with
        // imports cannot make it.
        int flags = MH_DYLDLINK | MH_TWOLEVEL | MH_PIE
                  | (imports.isEmpty() ? MH_NOUNDEFS : 0);
        out.putInt(MH_MAGIC_64);
        out.putInt(CPU_TYPE_X86_64);
        out.putInt(CPU_SUBTYPE_X86_64_ALL);
        out.putInt(fileType);
        out.putInt(commandCount);
        out.putInt(commandsSize);
        out.putInt(flags);
        out.putInt(0);
        for (byte[] c : commands) out.put(c);
        out.position(entryOffset);
        out.put(ENTRY_CODE);
        out.position(linkeditOffset);
        out.put(linkedit);
        out.position(codeOffset);
        out.put(codeResource);
        return out.array();
    }

    /**
     * The symbol table and the names that go with it.
     *
     * A fixed order, because LC_DYSYMTAB describes them as runs: everything defined here
     * first, everything undefined after, so a loader reads a start and a count instead of
     * testing each entry.
     *
     * Each entry is an nlist_64. A defined class has no useful address, being bytecode in
     * a zip, so what matters is that it is marked defined in the section that holds it. An
     * undefined one carries in its description the ordinal of the library expected to
     * supply it, which is the two level namespace in one byte. The string table begins
     * with a zero byte so offset zero means no name.
     */
    private static byte[] symbolTable(List<String> exports, Map<String, String> imports,
                                      List<String> ordinals) {
        List<byte[]> entries = new ArrayList<>();
        ByteArrayOutputStream strings = new ByteArrayOutputStream();
        strings.write(0);

        for (String className : exports) {
            entries.add(nlist(strings.size(), N_SECT | N_EXT, BYTECODE_SECTION, 0, 0L));
            writeName(strings, className);
        }
        for (Map.Entry<String, String> one : imports.entrySet()) {
            int ordinal = ordinals.indexOf(one.getValue()) + 1;
            // A library this image does not link cannot be pointed at, so the symbol is
            // written as a flat lookup: found by searching, the way things worked before
            // anyone recorded where a symbol came from.
            if (ordinal == 0) ordinal = DYNAMIC_LOOKUP_ORDINAL;
            entries.add(nlist(strings.size(), N_UNDF | N_EXT, 0, ordinal << 8, 0L));
            writeName(strings, one.getKey());
        }

        // The string table is padded so what follows it stays aligned.
        while (strings.size() % 8 != 0) strings.write(0);

        ByteBuffer out = ByteBuffer
            .allocate(entries.size() * NLIST_SIZE + strings.size())
            .order(ByteOrder.LITTLE_ENDIAN);
        for (byte[] e : entries) out.put(e);
        out.put(strings.toByteArray());
        return out.array();
    }

    /** A class as the symbol table spells it, null terminated the way a string table is. */
    private static void writeName(ByteArrayOutputStream strings, String className) {
        byte[] name = (CLASS_SYMBOL_PREFIX + className)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        strings.write(name, 0, name.length);
        strings.write(0);
    }

    private static byte[] nlist(int stringOffset, int type, int section, int description,
                                long value) {
        ByteBuffer b = ByteBuffer.allocate(NLIST_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(stringOffset);
        b.put((byte) type);
        b.put((byte) section);
        b.putShort((short) description);
        b.putLong(value);
        return b.array();
    }

    /** Where the symbols are and where their names are. */
    private static byte[] symtab(int symbolOffset, int symbolCount, int stringOffset,
                                 int stringSize) {
        ByteBuffer b = ByteBuffer.allocate(SYMTAB_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(LC_SYMTAB);
        b.putInt(SYMTAB_SIZE);
        b.putInt(symbolOffset);
        b.putInt(symbolCount);
        b.putInt(stringOffset);
        b.putInt(stringSize);
        return b.array();
    }

    /**
     * How the symbol table is divided.
     *
     * Eighteen counts, of which this uses four: where the defined symbols start and how
     * many there are, and the same for the undefined ones. The rest describe indirect
     * symbols, table of contents and module tables, which belong to a kind of library this
     * does not produce, and are left at zero rather than filled in with something untrue.
     */
    private static byte[] dysymtab(int definedCount, int undefinedCount) {
        ByteBuffer b = ByteBuffer.allocate(DYSYMTAB_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(LC_DYSYMTAB);
        b.putInt(DYSYMTAB_SIZE);
        b.putInt(0);                    // ilocalsym
        b.putInt(0);                    // nlocalsym
        b.putInt(0);                    // iextdefsym
        b.putInt(definedCount);         // nextdefsym
        b.putInt(definedCount);         // iundefsym
        b.putInt(undefinedCount);       // nundefsym
        while (b.position() < DYSYMTAB_SIZE) b.putInt(0);
        return b.array();
    }

    private static byte[] segment(String name, int nsects, long vmaddr, long vmsize,
                                  long fileoff, long filesize, int maxprot, int initprot,
                                  String section, long sectionAddr, long sectionSize,
                                  long sectionOffset) {
        int size = SEGMENT_SIZE + (nsects * SECTION_SIZE);
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(LC_SEGMENT_64);
        b.putInt(size);
        b.put(name16(name));
        b.putLong(vmaddr);
        b.putLong(vmsize);
        b.putLong(fileoff);
        b.putLong(filesize);
        b.putInt(maxprot);
        b.putInt(initprot);
        b.putInt(nsects);
        b.putInt(0);
        if (nsects > 0) {
            b.put(name16(section));
            b.put(name16(name));
            b.putLong(sectionAddr);
            b.putLong(sectionSize);
            b.putInt((int) sectionOffset);
            b.putInt(name.equals("__TEXT") ? 4 : 0);   // 2^4 alignment for code
            b.putInt(0);                                // reloff
            b.putInt(0);                                // nreloc
            b.putInt(name.equals("__TEXT") ? 0x80000400 : 0);  // pure instructions
            b.putInt(0);
            b.putInt(0);
            b.putInt(0);
        }
        return b.array();
    }

    private static byte[] dylinker() {
        byte[] path = DYLINKER.getBytes(StandardCharsets.UTF_8);
        int size = align(12 + path.length + 1, 8);
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(LC_LOAD_DYLINKER);
        b.putInt(size);
        b.putInt(12);
        b.put(path);
        return b.array();
    }

    /** One dylib command: linked, re-exported, or the library naming itself. */
    private static byte[] dylib(int command, String path) {
        byte[] name = path.getBytes(StandardCharsets.UTF_8);
        int size = align(24 + name.length + 1, 8);
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command);
        b.putInt(size);
        b.putInt(24);      // the name starts here
        b.putInt(1);       // timestamp
        b.putInt(0x10000); // current version 1.0.0
        b.putInt(0x10000); // compatible back to 1.0.0
        b.put(name);
        return b.array();
    }

    /** LC_RPATH: one place @rpath may stand for. */
    private static byte[] rpath(String path) {
        return pathCommand(LC_RPATH, path);
    }

    /** Any load command that is a command, a size, an offset and one string. */
    private static byte[] pathCommand(int command, String path) {
        byte[] name = path.getBytes(StandardCharsets.UTF_8);
        int size = align(12 + name.length + 1, 8);
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command);
        b.putInt(size);
        b.putInt(12);
        b.put(name);
        return b.array();
    }

    /**
     * LC_UUID, derived from the name and the code resources rather than a clock, so two
     * builds of the same program produce byte-identical files.
     */
    private static byte[] uuid(String installName, byte[] codeResource) {
        ByteBuffer b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(LC_UUID);
        b.putInt(24);
        byte[] digest;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(installName.getBytes(StandardCharsets.UTF_8));
            md.update(codeResource);
            digest = md.digest();
        } catch (Exception e) {
            digest = new byte[16];
        }
        b.put(digest, 0, 16);
        return b.array();
    }

    private static byte[] main(int entryOffset) {
        ByteBuffer b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(LC_MAIN);
        b.putInt(24);
        b.putLong(entryOffset);
        b.putLong(0);           // the default stack
        return b.array();
    }

    private static byte[] name16(String s) {
        byte[] out = new byte[16];
        byte[] src = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, out, 0, Math.min(16, src.length));
        return out;
    }

    private static int align(long value, int to) {
        long rounded = (value + to - 1) / to * to;
        return (int) rounded;
    }

    /* ------------------------------------------------------------- reading */

    /** One section inside a segment. */
    public static final class Section {
        public final String segment;
        public final String name;
        public final long address;
        public final long size;
        public final int offset;

        Section(String segment, String name, long address, long size, int offset) {
            this.segment = segment;
            this.name = name;
            this.address = address;
            this.size = size;
            this.offset = offset;
        }
    }

    private final Path file;
    private final int cpuType;
    private final int fileType;
    private final int commandCount;
    private final long entryOffset;
    private final List<String> segments = new ArrayList<>();
    private final List<String> linkedLibraries = new ArrayList<>();
    private final List<String> reexported = new ArrayList<>();
    private final List<String> runpaths = new ArrayList<>();
    private final List<String> dylibOrdinals = new ArrayList<>();
    private final List<String> exports = new ArrayList<>();
    private final Map<String, String> imports = new LinkedHashMap<>();
    private String installName = "";
    private final Map<String, Section> sections = new LinkedHashMap<>();
    private String dynamicLoader = "";
    private byte[] identifier = new byte[16];

    private MachO(Path file, int cpuType, int fileType, int commandCount, long entryOffset) {
        this.file = file;
        this.cpuType = cpuType;
        this.fileType = fileType;
        this.commandCount = commandCount;
        this.entryOffset = entryOffset;
    }

    /** Reads one, or throws if the file is not a 64 bit Mach-O. */
    public static MachO read(Path path) throws IOException {
        return parse(path, Files.readAllBytes(path));
    }

    public static MachO parse(Path path, byte[] bytes) throws IOException {
        if (bytes.length < HEADER_SIZE) throw new IOException("too short to be a program");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = b.getInt(0);
        if (magic != MH_MAGIC_64) {
            throw new IOException(String.format("not a 64 bit Mach-O: magic %08x", magic));
        }
        int cpuType = b.getInt(4);
        int fileType = b.getInt(12);
        int ncmds = b.getInt(16);
        int sizeofcmds = b.getInt(20);
        if (HEADER_SIZE + sizeofcmds > bytes.length) {
            throw new IOException("the load commands run past the end of the file");
        }

        MachO out = new MachO(path, cpuType, fileType, ncmds, -1);
        long entry = -1;
        int symbolOffset = 0, symbolCount = 0, stringOffset = 0, stringSize = 0;
        int at = HEADER_SIZE;
        for (int i = 0; i < ncmds; i++) {
            if (at + 8 > bytes.length) throw new IOException("a load command runs off the end");
            int cmd = b.getInt(at);
            int cmdsize = b.getInt(at + 4);
            if (cmdsize < 8 || at + cmdsize > bytes.length) {
                throw new IOException("load command " + i + " has an impossible size");
            }
            switch (cmd) {
                case LC_SEGMENT_64 -> out.readSegment(b, bytes, at);
                case LC_LOAD_DYLINKER -> out.dynamicLoader = string(bytes,
                    nameStart(at, b.getInt(at + 8), cmdsize, bytes.length), at + cmdsize);
                case LC_ID_DYLINKER -> out.installName = string(bytes,
                    nameStart(at, b.getInt(at + 8), cmdsize, bytes.length), at + cmdsize);
                case LC_ID_DYLIB -> out.installName = string(bytes,
                    nameStart(at, b.getInt(at + 8), cmdsize, bytes.length), at + cmdsize);
                case LC_RPATH -> out.runpaths.add(string(bytes,
                    nameStart(at, b.getInt(at + 8), cmdsize, bytes.length), at + cmdsize));
                case LC_REEXPORT_DYLIB -> {
                    String name = string(bytes,
                        nameStart(at, b.getInt(at + 8), cmdsize, bytes.length), at + cmdsize);
                    out.reexported.add(name);
                    out.dylibOrdinals.add(name);
                }
                case LC_LOAD_DYLIB -> {
                    String name = string(bytes,
                        nameStart(at, b.getInt(at + 8), cmdsize, bytes.length), at + cmdsize);
                    out.linkedLibraries.add(name);
                    out.dylibOrdinals.add(name);
                }
                case LC_SYMTAB -> {
                    symbolOffset = b.getInt(at + 8);
                    symbolCount = b.getInt(at + 12);
                    stringOffset = b.getInt(at + 16);
                    stringSize = b.getInt(at + 20);
                }
                case LC_UUID -> {
                    out.identifier = new byte[16];
                    System.arraycopy(bytes, at + 8, out.identifier, 0, 16);
                }
                case LC_MAIN -> entry = b.getLong(at + 8);
                default -> { }
            }
            at += cmdsize;
        }
        // The ordinals only mean anything once every load command has been read, so the
        // symbols are taken after the loop rather than during it.
        out.readSymbols(bytes, symbolOffset, symbolCount, stringOffset, stringSize);

        MachO result = new MachO(path, cpuType, fileType, ncmds, entry);
        result.exports.addAll(out.exports);
        result.imports.putAll(out.imports);
        result.dylibOrdinals.addAll(out.dylibOrdinals);
        result.segments.addAll(out.segments);
        result.linkedLibraries.addAll(out.linkedLibraries);
        result.reexported.addAll(out.reexported);
        result.runpaths.addAll(out.runpaths);
        result.installName = out.installName;
        result.sections.putAll(out.sections);
        result.dynamicLoader = out.dynamicLoader;
        result.identifier = out.identifier;
        return result;
    }

    private void readSegment(ByteBuffer b, byte[] bytes, int at) {
        String name = string16(bytes, at + 8);
        segments.add(name);
        int nsects = b.getInt(at + 64);
        int sectionAt = at + SEGMENT_SIZE;
        // nsects comes from the file, so trust it only as far as the file can hold:
        // sections are fixed size, and the last one has to fit.
        if (nsects < 0 || (long) sectionAt + (long) nsects * SECTION_SIZE > bytes.length) {
            return;
        }
        for (int i = 0; i < nsects; i++) {
            String sectionName = string16(bytes, sectionAt);
            String segmentName = string16(bytes, sectionAt + 16);
            long address = b.getLong(sectionAt + 32);
            long size = b.getLong(sectionAt + 40);
            int offset = b.getInt(sectionAt + 48);
            sections.put(segmentName + "," + sectionName,
                         new Section(segmentName, sectionName, address, size, offset));
            sectionAt += SECTION_SIZE;
        }
    }

    private static String string16(byte[] bytes, int at) {
        if (at < 0 || at + 16 > bytes.length) return "";
        int end = at;
        while (end < at + 16 && bytes[end] != 0) end++;
        return new String(bytes, at, end - at, StandardCharsets.UTF_8);
    }

    /**
     * Reads the symbol table into what this image defines and what it expects.
     *
     * Nothing here trusts the counts in the load command: they are four numbers in a file
     * that may have been written by anything, and multiplying two of them is how a reader
     * allocates whatever the file asks for. Each entry is checked against the length first.
     *
     * An undefined symbol carries the ordinal of the library meant to supply it. Ordinal
     * 254 means it was not pinned to one; past the end of the load commands is a file
     * disagreeing with itself, and is skipped.
     */
    private void readSymbols(byte[] bytes, int symbolOffset, int symbolCount,
                             int stringOffset, int stringSize) {
        if (symbolCount <= 0 || symbolOffset <= 0) return;
        long end = (long) symbolOffset + (long) symbolCount * NLIST_SIZE;
        if (end > bytes.length || stringOffset < 0
            || (long) stringOffset + stringSize > bytes.length) {
            return;
        }
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < symbolCount; i++) {
            int at = symbolOffset + i * NLIST_SIZE;
            int nameAt = b.getInt(at);
            int type = b.get(at + 4) & 0xFF;
            int description = b.getShort(at + 6) & 0xFFFF;
            String symbol = symbolName(bytes, stringOffset, stringSize, nameAt);
            if (symbol == null || !symbol.startsWith(CLASS_SYMBOL_PREFIX)) continue;
            String className = symbol.substring(CLASS_SYMBOL_PREFIX.length());

            if ((type & N_EXT) == 0) continue;
            if ((type & N_SECT) == N_SECT) {
                exports.add(className);
                continue;
            }
            int ordinal = (description >> 8) & 0xFF;
            if (ordinal == DYNAMIC_LOOKUP_ORDINAL || ordinal == SELF_LIBRARY_ORDINAL
                || ordinal == EXECUTABLE_ORDINAL) {
                imports.put(className, "");
            } else if (ordinal <= dylibOrdinals.size()) {
                imports.put(className, dylibOrdinals.get(ordinal - 1));
            }
        }
    }

    /** One name out of the string table, which ends where the first zero byte is. */
    private static String symbolName(byte[] bytes, int stringOffset, int stringSize,
                                     int nameAt) {
        if (nameAt <= 0 || nameAt >= stringSize) return null;
        int from = stringOffset + nameAt;
        int to = from;
        int limit = stringOffset + stringSize;
        while (to < limit && bytes[to] != 0) to++;
        return new String(bytes, from, to - from, StandardCharsets.UTF_8);
    }

    /**
     * The classes this image defines, which anything linking it may use.
     *
     * This is what makes a library a library rather than a jar that happens to be in the
     * right place: it says what it offers, and the loader has something to check against
     * rather than a directory listing to search.
     */
    public List<String> exports() { return List.copyOf(exports); }

    /**
     * The classes this image expects to be given, and which library it expects each from.
     *
     * An empty library name means the symbol was not pinned to one and has to be searched
     * for, which is what a flat namespace does for everything.
     */
    public Map<String, String> imports() { return Map.copyOf(imports); }

    /** Whether this image says which library each symbol it uses comes from. */
    public boolean isTwoLevel() { return !exports.isEmpty() || !imports.isEmpty(); }

    /**
     * Where a load command's string starts, rejecting an offset that points outside the
     * command. The name lives within the command's own span; anything landing before it or
     * past the end of the file is not a string offset.
     */
    private static int nameStart(int commandAt, int offset, int cmdsize, int length) {
        long start = (long) commandAt + offset;
        if (offset < 0 || start < commandAt || start >= commandAt + cmdsize || start >= length) {
            return length;                     // an empty read, which string() answers as ""
        }
        return (int) start;
    }

    private static String string(byte[] bytes, int at, int limit) {
        int end = at;
        while (end < limit && end < bytes.length && bytes[end] != 0) end++;
        return new String(bytes, at, end - at, StandardCharsets.UTF_8);
    }

    public Path file() { return file; }
    public int cpuType() { return cpuType; }
    public int fileType() { return fileType; }
    public int commandCount() { return commandCount; }
    public long entryOffset() { return entryOffset; }
    public List<String> segments() { return List.copyOf(segments); }
    public List<String> linkedLibraries() { return List.copyOf(linkedLibraries); }

    /** What this passes on to anything that links it. */
    public List<String> reexported() { return List.copyOf(reexported); }

    /** What @rpath stands for in this image, in the order to try. */
    public List<String> runpaths() { return List.copyOf(runpaths); }

    /** What a library calls itself, which is the name its clients record. */
    public String installName() { return installName; }
    public String dynamicLoader() { return dynamicLoader; }
    public byte[] identifier() { return identifier.clone(); }

    public Section section(String segment, String name) {
        return sections.get(segment + "," + name);
    }

    /** The code resources: the bytes of __FRACTAL,__bytecode. */
    public byte[] codeResource() throws IOException {
        Section s = section(CODE_SEGMENT, CODE_SECTION);
        if (s == null) throw new IOException("this program carries no code resources");
        byte[] all = Files.readAllBytes(file);
        // Check both ends against the file actually on disk. The header only asserts the
        // offset and size, and a crafted one is how the read ends up out of bounds.
        if (s.offset < 0 || s.size < 0 || s.size > Integer.MAX_VALUE
                || s.offset + s.size > all.length) {
            throw new IOException("the code resources do not fit the file");
        }
        byte[] out = new byte[(int) s.size];
        System.arraycopy(all, s.offset, out, 0, out.length);
        return out;
    }

    /** Where the code resources start, for a reader that wants to seek rather than copy. */
    public int codeResourceOffset() {
        Section s = section(CODE_SEGMENT, CODE_SECTION);
        return s == null ? -1 : s.offset;
    }

    public String architecture() {
        return cpuType == CPU_TYPE_X86_64 ? "x86_64" : "cputype " + cpuType;
    }

    @Override public String toString() {
        return "Mach-O 64 bit executable " + architecture()
             + ", " + commandCount + " load commands, "
             + segments.size() + " segments";
    }
}
