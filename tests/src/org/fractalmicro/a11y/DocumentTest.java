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

import org.fractalmicro.appkit.FMApplication;
import org.fractalmicro.appkit.FMDocument;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.nib.Nib;
import org.fractalmicro.nib.Nib.ControlClass;
import org.fractalmicro.theme.AquaInternalFrameUI;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.windowserver.WindowServer;

import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A document, and the dot that says it has changes.
 *
 * Two facts: where it came from, and whether it has changed. The title, the dot in the
 * close button, the question on closing and whether it is asked at all, and the recent
 * items entry all follow from them.
 *
 * Changed is worked out rather than remembered. A flag set by whatever edits the text is a
 * flag something forgets to set, and it fails towards closing without asking.
 *
 * The mark is checked through the window server: the program is in another process and
 * cannot reach its own close button.
 */
public final class DocumentTest {
    private DocumentTest() {}

    public static int count() { return 8; }

    public static int run(Desktop desktop, PrintStream out) {
        int failures = 0;
        out.println();
        out.println("a document:");

        Path folder;
        try {
            folder = Files.createTempDirectory("fractal-document-check");
        } catch (Exception e) {
            out.println("FAIL  a folder to work in: " + e);
            return count();
        }

        WindowServer server = WindowServer.sharedServer();
        if (!server.start() && !server.isRunning()) {
            out.println("FAIL  the window server is not running");
            return count();
        }

        try (FMApplication app = FMApplication.named(FMString.of("Editing"))) {
            app.showWindow(new Nib.Builder()
                .title(FMString.of("Editing")).size(320, 200)
                .add(ControlClass.FMTextView, FMString.of("body"), FMString.of("Text"),
                     FMString.EMPTY, 8, 8, 300, 150)
                .build());
            drain();

            FMDocument document = new FMDocument(app);

            failures += check(out, "a new one is Untitled and has been nowhere",
                document.fileURL() == null
                && document.displayName().sameAs(FMString.of("Untitled")));

            failures += check(out, "with nothing typed it has no changes",
                !document.isDocumentEdited(FMString.EMPTY));
            failures += check(out, "and with something typed it has",
                document.isDocumentEdited(FMString.of("a line")));

            // The mark on the window, which the program cannot see and has to be told.
            JInternalFrame frame = frameTitled(desktop, "Editing");
            document.showEdited(FMString.of("a line"));
            drain();
            failures += check(out, "the close button is marked while there are changes",
                frame != null && Boolean.TRUE.equals(
                    frame.getClientProperty(AquaInternalFrameUI.DOCUMENT_EDITED)));

            Path file = folder.resolve("Notes.txt");
            Files.writeString(file, "a line");
            document.noteWritten(FMURL.of(file.toFile()), FMString.of("a line"));
            drain();
            failures += check(out, "writing it takes the mark off and names the window",
                frame != null
                && Boolean.FALSE.equals(
                    frame.getClientProperty(AquaInternalFrameUI.DOCUMENT_EDITED))
                && "Notes.txt".equals(frame.getTitle()));

            failures += check(out, "and it is named for the file it came from",
                document.displayName().sameAs(FMString.of("Notes.txt")));

            failures += check(out, "what was written is what it now believes it holds",
                !document.isDocumentEdited(FMString.of("a line"))
                && document.isDocumentEdited(FMString.of("a line, changed")));

            // Closing with nothing to lose is not a question. The other two answers need
            // somebody to press a button, so they are not asked for here.
            failures += check(out, "closing it with no changes asks nothing",
                document.shouldClose(FMString.of("a line")) == FMDocument.Closing.DISCARD);

            app.hideWindow();
            drain();
        } catch (Exception e) {
            out.println("FAIL  the document could be kept: " + e);
            failures++;
        } finally {
            deleteTree(folder.toFile());
        }

        out.println("      " + (failures == 0
            ? "what has changed is known rather than remembered"
            : failures + " failed"));
        return failures;
    }

    private static JInternalFrame frameTitled(Desktop desktop, String title) {
        for (JInternalFrame frame : desktop.windows()) {
            if (title.equals(frame.getTitle())) return frame;
        }
        return null;
    }

    private static void drain() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(60);
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteTree(java.io.File file) {
        java.io.File[] children = file.listFiles();
        if (children != null) for (java.io.File child : children) deleteTree(child);
        file.delete();
    }

    private static int check(PrintStream out, String what, boolean ok) {
        out.println((ok ? "ok    " : "FAIL  ") + what);
        return ok ? 0 : 1;
    }
}
