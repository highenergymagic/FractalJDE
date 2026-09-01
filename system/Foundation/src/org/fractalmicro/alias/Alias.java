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
package org.fractalmicro.alias;

import org.fractalmicro.fs.FinderInfo;
import org.fractalmicro.fs.ResourceFork;
import org.fractalmicro.win.Streams;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Aliases: making them, and finding what they point at.
 *
 * An alias file is a file with nothing in its data fork, an 'alis' resource in its
 * resource fork holding the record, and the alias flag set in its Finder information.
 * That is what an alias has always been, and it is what is written here: the resource
 * fork and the Finder information going into the two streams a Mac would use on a volume
 * like this one.
 *
 * Resolving works the way the Alias Manager was documented to work, and this is the
 * whole reason for preferring an alias to a symbolic link:
 *
 *   1. the path in the record, if something is still there and it is the same file
 *   2. the file reference number, which finds the file wherever it has been moved to
 *      and whatever it has been renamed to
 *   3. the recorded name inside the recorded parent folder, for a file that was replaced
 *
 * A symbolic link only ever has step one.
 */
public final class Alias {
    private Alias() {}

    public static final String RESOURCE_TYPE = "alis";
    public static final String FILE_TYPE = "alis";
    public static final String CREATOR = "MACS";
    /** What the Finder adds to the name of an alias it makes. */
    public static final String SUFFIX = " alias";

    /** How an alias was resolved, so the Finder can say what happened. */
    public enum Found { PATH, IDENTITY, NAME, LOST }

    /** The answer: what was found, and how. */
    public record Resolution(File target, Found how, AliasRecord record) {
        public boolean ok() { return target != null; }
    }

    /* -------------------------------------------------------------- making */

    /**
     * Makes an alias to a file, beside it, named the way the Finder names one. Answers
     * the alias file, or throws if it could not be written.
     */
    public static File create(File target, File parent) throws IOException {
        if (target == null || !target.exists()) throw new IOException("nothing to point at");
        File folder = parent == null ? target.getParentFile() : parent;
        File alias = new File(folder, target.getName() + SUFFIX);
        int n = 2;
        while (alias.exists()) alias = new File(folder, target.getName() + SUFFIX + " " + n++);
        return createAt(target, alias);
    }

    /** Makes an alias at one named place. */
    public static File createAt(File target, File alias) throws IOException {
        AliasRecord record = recordFor(target);
        ResourceFork fork = new ResourceFork();
        fork.put(RESOURCE_TYPE, 0, record.toBytes());

        // The data fork of an alias is empty. A line of text goes in only when the
        // record could not be kept in a fork, so the file still says what it is.
        Files.write(alias.toPath(), new byte[0]);
        boolean forkWritten = fork.writeTo(alias);
        boolean infoWritten = new FinderInfo()
            .type(FILE_TYPE).creator(CREATOR).alias(true)
            .writeTo(alias);

        if (!forkWritten) {
            Files.write(alias.toPath(),
                ("This is an alias to " + target.getAbsolutePath() + System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8));
            org.fractalmicro.core.Log.info("no fork available for " + alias
                                  + "; the alias record is kept beside it");
        }
        if (!infoWritten) {
            org.fractalmicro.core.Log.info("no Finder information stream on " + alias.getPath());
        }
        return alias;
    }

    /**
     * When a file was made, in seconds, which is what the record wants. Falls back to the
     * modification time on a file system that does not keep a creation time.
     */
    private static long createdSeconds(File file) {
        try {
            java.nio.file.attribute.BasicFileAttributes a = Files.readAttributes(
                file.toPath(), java.nio.file.attribute.BasicFileAttributes.class);
            long created = a.creationTime().toMillis();
            return (created > 0 ? created : file.lastModified()) / 1000;
        } catch (IOException | RuntimeException e) {
            return file.lastModified() / 1000;
        }
    }

    /** The record describing one file, filled in from what this volume knows. */
    public static AliasRecord recordFor(File target) {
        AliasRecord record = new AliasRecord();
        record.kind = target.isDirectory() ? AliasRecord.KIND_FOLDER : AliasRecord.KIND_FILE;
        record.fileName = target.getName();
        record.fileCreated = createdSeconds(target);

        File volume = volumeOf(target);
        record.volumeName = volumeName(volume);
        record.volumeCreated = volume == null ? 0 : createdSeconds(volume);

        Streams.Identity identity = Streams.identityOf(target);
        record.fileNumber = identity.fileReference();
        record.volumeFileSystemId = 0;
        record.levelsFrom = -1;
        record.levelsTo = -1;

        File parent = target.getParentFile();
        if (parent != null) {
            record.entry(AliasRecord.TAG_PARENT_NAME, parent.getName());
            record.parentDirectoryId = Streams.identityOf(parent).fileReference();
        }
        record.entry(AliasRecord.TAG_UNICODE_NAME, target.getName());
        record.entry(AliasRecord.TAG_UNICODE_VOLUME_NAME, record.volumeName);
        record.entry(AliasRecord.TAG_ABSOLUTE_PATH, target.getAbsolutePath());
        record.entry(AliasRecord.TAG_POSIX_PATH, target.getAbsolutePath());
        record.entry(AliasRecord.TAG_POSIX_VOLUME_PATH,
                     volume == null ? "" : volume.getAbsolutePath());
        // The identity is kept whole, because a file reference number is 64 bits and the
        // field in the fixed part of the record is 32.
        record.entry(AliasRecord.TAG_FILE_REFERENCE,
                     Long.toString(identity.fileReference()));
        return record;
    }

    /* ------------------------------------------------------------ reading */

    /** Whether this file is an alias: what its Finder information says it is. */
    public static boolean isAlias(File file) {
        if (file == null || !file.isFile()) return false;
        FinderInfo info = FinderInfo.of(file);
        if (info.isAlias()) return true;
        return recordIn(file) != null;
    }

    /** The record inside an alias file, or null if there is none. */
    public static AliasRecord recordIn(File file) {
        if (file == null || !file.isFile()) return null;
        byte[] resource = ResourceFork.of(file).get(RESOURCE_TYPE, 0);
        return resource == null ? null : AliasRecord.parse(resource);
    }

    /**
     * Follows an alias, trying each way in turn. Answers what was found and how, so the
     * caller can tell the difference between a file that never moved and one that was
     * chased down.
     */
    public static Resolution resolve(File file) {
        AliasRecord record = recordIn(file);
        if (record == null) return new Resolution(null, Found.LOST, null);

        String path = record.string(AliasRecord.TAG_POSIX_PATH);
        if (path == null) path = record.string(AliasRecord.TAG_ABSOLUTE_PATH);
        long wanted = wantedIdentity(record);

        // 1. Where it was. Only good enough if it is still the same file, when the
        //    record knows which file that was.
        if (path != null && !path.isEmpty()) {
            File there = new File(path);
            if (there.exists()) {
                long identity = Streams.identityOf(there).fileReference();
                if (wanted == 0 || identity == 0 || identity == wanted) {
                    return new Resolution(there, Found.PATH, record);
                }
            }
        }

        // 2. The file itself, wherever it went.
        if (wanted != 0) {
            String volumePath = record.string(AliasRecord.TAG_POSIX_VOLUME_PATH);
            File volume = volumePath == null || volumePath.isEmpty()
                ? volumeOf(file) : new File(volumePath);
            File found = Streams.byIdentity(volume, wanted);
            if (found != null && found.exists()) {
                return new Resolution(found, Found.IDENTITY, record);
            }
        }

        // 3. Something of that name, back where it was.
        String parent = path == null ? null : new File(path).getParent();
        if (parent != null && !record.fileName.isEmpty()) {
            File byName = new File(parent, record.fileName);
            if (byName.exists()) return new Resolution(byName, Found.NAME, record);
        }
        return new Resolution(null, Found.LOST, record);
    }

    private static long wantedIdentity(AliasRecord record) {
        String kept = record.string(AliasRecord.TAG_FILE_REFERENCE);
        if (kept != null) {
            try {
                return Long.parseLong(kept.trim());
            } catch (NumberFormatException ignored) {
                // Fall through to the field in the fixed part of the record.
            }
        }
        return record.fileNumber;
    }

    /* -------------------------------------------------------------- pieces */

    /** The volume a file is on: on this system, the root of its path. */
    public static File volumeOf(File file) {
        File at = file;
        while (at != null && at.getParentFile() != null) at = at.getParentFile();
        return at;
    }

    private static String volumeName(File volume) {
        if (volume == null) return "";
        String label = org.fractalmicro.win.Kernel32.volumeLabel(volume.getPath());
        if (label != null && !label.isBlank()) return label;
        String path = volume.getPath();
        return path.replace("\\", "").replace(":", "");
    }
}
