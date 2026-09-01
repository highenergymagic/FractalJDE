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
package org.fractalmicro.a11y;

import org.fractalmicro.alias.Alias;
import org.fractalmicro.alias.AliasRecord;
import org.fractalmicro.fs.FinderInfo;
import org.fractalmicro.fs.Labels;
import org.fractalmicro.fs.ResourceFork;
import org.fractalmicro.fs.Sidecar;
import org.fractalmicro.win.Streams;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Aliases and labels, driven against real files.
 *
 * The point of an alias over a symbolic link is that it follows what it points at, so
 * that is what is checked: the original is renamed, then moved, and the alias has to
 * find it both times. The label is checked by writing it, reading it back off the file,
 * and making sure the file's own contents did not change underneath.
 */
public final class AliasTest {
    private AliasTest() {}

    public static int count() { return 16; }

    public static int run(PrintStream out) {
        int failures = 0;
        out.println();
        out.println("aliases and labels:");

        Path dir;
        try {
            dir = Files.createTempDirectory("fractal-alias-check");
        } catch (Exception e) {
            out.println("FAIL  a folder to work in: " + e);
            return count();
        }

        try {
            File target = dir.resolve("Original.txt").toFile();
            Files.writeString(target.toPath(), "the original");
            boolean forks = Streams.supported(target);
            out.println("      this volume " + (forks ? "holds" : "does not hold")
                        + " a second fork on a file");

            /* ------------------------------------------------------ the record */
            AliasRecord written = Alias.recordFor(target);
            AliasRecord readBack = AliasRecord.parse(written.toBytes());
            failures += check(out, "an alias record survives being written and read",
                readBack != null
                && "Original.txt".equals(readBack.fileName)
                && readBack.kind == AliasRecord.KIND_FILE
                && target.getAbsolutePath().equals(
                    readBack.string(AliasRecord.TAG_POSIX_PATH)));

            /* ------------------------------------------------- the alias file */
            File alias = Alias.create(target, null);
            failures += check(out, "the alias is named the way the Finder names one",
                "Original.txt alias".equals(alias.getName()));
            failures += check(out, "an alias has nothing in its data fork",
                !forks || alias.length() == 0);
            failures += check(out, "an alias says it is one in its Finder information",
                FinderInfo.of(alias).isAlias()
                && "alis".equals(FinderInfo.of(alias).type())
                && "MACS".equals(FinderInfo.of(alias).creator()));
            failures += check(out, "the record is in the resource fork",
                ResourceFork.of(alias).get(Alias.RESOURCE_TYPE, 0) != null
                && Alias.recordIn(alias) != null);
            failures += check(out, "the Finder sees it as an alias", Alias.isAlias(alias)
                && org.fractalmicro.fs.FS.kindOf(alias) == org.fractalmicro.fs.Node.Kind.ALIAS);

            /* -------------------------------------------------------- following */
            Alias.Resolution found = Alias.resolve(alias);
            failures += check(out, "an alias finds a file that has not moved",
                found.ok() && found.how() == Alias.Found.PATH
                && found.target().getAbsolutePath().equals(target.getAbsolutePath()));

            File renamed = dir.resolve("Renamed.txt").toFile();
            boolean moved = target.renameTo(renamed);
            Alias.Resolution afterRename = Alias.resolve(alias);
            failures += check(out, "an alias follows a file that was renamed",
                !moved || !forks
                || (afterRename.ok() && afterRename.how() == Alias.Found.IDENTITY
                    && afterRename.target().getName().equals("Renamed.txt")));

            File sub = dir.resolve("Elsewhere").toFile();
            sub.mkdirs();
            File movedFile = new File(sub, "Renamed.txt");
            boolean movedAway = renamed.renameTo(movedFile);
            Alias.Resolution afterMove = Alias.resolve(alias);
            failures += check(out, "an alias follows a file that was moved",
                !movedAway || !forks
                || (afterMove.ok() && afterMove.target().getAbsolutePath()
                        .equals(movedFile.getAbsolutePath())));

            // A symbolic link is the thing being improved on, so say what it does here:
            // it holds the path it was given and nothing else.
            failures += check(out, "the record keeps more than a path",
                Alias.recordIn(alias).string(AliasRecord.TAG_FILE_REFERENCE) != null
                || !forks);

            movedFile.delete();
            Alias.Resolution afterDelete = Alias.resolve(alias);
            failures += check(out, "an alias to something deleted says it is lost",
                !afterDelete.ok() && afterDelete.how() == Alias.Found.LOST);

            /* -------------------------------------------- forks stay in the file */

            // A fork is written by opening "path:name". A path that is not quite a file
            // sends those bytes to a plain file sitting beside the real one, which has
            // happened; nothing should be able to make it happen again.
            String[] before = dir.toFile().list();
            File notAFile = new File(dir.toFile(), "no-such-file.txt");
            boolean refusedMissing = !Streams.write(notAFile, "AFP_Resource", new byte[]{1});
            boolean refusedFolder = !Streams.write(dir.toFile(), "AFP_Resource", new byte[]{1});
            boolean refusedOddName = !Streams.write(
                new File(dir.toFile(), "trailing "), "AFP_Resource", new byte[]{1});
            boolean refusedOddStream = !Streams.write(alias, "bad\\name", new byte[]{1})
                && !Streams.write(alias, "bad\nname", new byte[]{1});
            String[] after = dir.toFile().list();
            out.println("      refused: missing " + refusedMissing + ", folder " + refusedFolder
                        + ", odd name " + refusedOddName + ", odd stream " + refusedOddStream
                        + "; folder held " + before.length + " and now " + after.length);
            failures += check(out, "a fork is never written as a file beside the real one",
                refusedMissing && refusedFolder && refusedOddName && refusedOddStream
                && before.length == after.length
                && java.util.Arrays.stream(after).noneMatch(n -> n.startsWith("AFP_")));

            /* ------------------------------------------------------------ labels */
            File labelled = dir.resolve("Labelled.txt").toFile();
            Files.writeString(labelled.toPath(), "contents");
            Labels.set(labelled, 5);
            Labels.forget(labelled);
            failures += check(out, "a label is written to the file and read back",
                Labels.of(labelled) == 5);
            failures += check(out, "the label has a name",
                "Blue".equals(Labels.nameOf(5)) && Labels.colorOf(5) != null);
            failures += check(out, "labelling a file leaves its contents alone",
                "contents".equals(Files.readString(labelled.toPath())));

            Labels.set(labelled, Labels.NONE);
            Labels.forget(labelled);
            failures += check(out, "a label can be taken off again",
                Labels.of(labelled) == Labels.NONE);

            if (Sidecar.inUse()) {
                out.println("      some records went beside the files, not into them");
            }
        } catch (Exception e) {
            out.println("FAIL  the checks ran to the end: " + e);
            failures++;
        } finally {
            deleteTree(dir.toFile());
        }

        out.println("      " + (failures == 0 ? "aliases follow what they point at"
                                              : failures + " failed"));
        return failures;
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        file.delete();
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
