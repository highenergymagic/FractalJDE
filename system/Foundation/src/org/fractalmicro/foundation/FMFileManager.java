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
package org.fractalmicro.foundation;

/**
 * What a program asks about files.
 *
 * Everything a program does to a file goes through here rather than through the runtime,
 * so that a program says what it wants and this decides how that is done on the volume it
 * is actually running on. Nothing here throws: a question about a file answers no, and an
 * operation answers whether it happened. A program that needs to know why asks
 * {@link #lastError}.
 */
public final class FMFileManager {

    private static final FMFileManager SHARED = new FMFileManager();

    private FMFileManager() {}

    /** The one every program uses. */
    public static FMFileManager defaultManager() { return SHARED; }

    private final ThreadLocal<FMString> why = new ThreadLocal<>();

    /** Why the last thing this thread asked for did not work. */
    public FMString lastError() {
        FMString said = why.get();
        return said == null ? FMString.EMPTY : said;
    }

    private boolean failed(Exception e) {
        why.set(FMString.of(e.getMessage() == null ? e.toString() : e.getMessage()));
        return false;
    }

    public boolean exists(FMURL url) { return url != null && url.asFile().exists(); }

    public boolean isDirectory(FMURL url) { return url != null && url.asFile().isDirectory(); }

    public boolean isReadable(FMURL url) { return url != null && url.asFile().canRead(); }

    /** How many bytes are in it, or -1 when that cannot be said. */
    public long sizeOf(FMURL url) {
        return url == null || !url.asFile().isFile() ? -1 : url.asFile().length();
    }

    /** What is in a directory, in no particular order. Empty when it is not one. */
    public FMArray<FMURL> contentsOf(FMURL directory) {
        if (directory == null) return FMArray.empty();
        java.io.File[] kids = directory.asFile().listFiles();
        if (kids == null) return FMArray.empty();
        FMMutableArray<FMURL> out = FMMutableArray.empty();
        for (java.io.File k : kids) out.add(FMURL.of(k));
        return out.asArray();
    }

    public FMData contentsOf(FMURL url, boolean unusedOverload) {
        return FMData.withContentsOf(url);
    }

    public boolean createDirectory(FMURL url) {
        try {
            java.nio.file.Files.createDirectories(url.asPath());
            return true;
        } catch (java.io.IOException e) {
            return failed(e);
        }
    }

    public boolean copy(FMURL from, FMURL to) {
        try {
            java.nio.file.Files.copy(from.asPath(), to.asPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (java.io.IOException e) {
            return failed(e);
        }
    }

    public boolean move(FMURL from, FMURL to) {
        try {
            java.nio.file.Files.move(from.asPath(), to.asPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (java.io.IOException e) {
            return failed(e);
        }
    }

    /** Removes it, and everything inside it when it is a directory. */
    public boolean remove(FMURL url) {
        java.io.File what = url.asFile();
        java.io.File[] kids = what.listFiles();
        if (kids != null) for (java.io.File k : kids) remove(FMURL.of(k));
        return what.delete();
    }

    /* ------------------------------------------------------------ the usual places */

    public FMURL home() { return FMURL.ofPath(System.getProperty("user.home")); }

    /**
     * The folders a person is given, rather than the ones a system needs.
     *
     * Every system has this list and every system has programs guessing at it instead,
     * which is how a document ends up somewhere nobody looks. Asking is one call and the
     * answer is right on a machine where the folders have been moved or renamed.
     */
    public FMURL documents() { return within("Documents"); }

    public FMURL desktop() { return within("Desktop"); }

    public FMURL downloads() { return within("Downloads"); }

    private FMURL within(String folder) {
        return home().appending(FMString.of(folder));
    }

    public FMURL temporaryDirectory() {
        return FMURL.ofPath(System.getProperty("java.io.tmpdir"));
    }
}
