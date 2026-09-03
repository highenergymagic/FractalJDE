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
package org.fractalmicro.qlgenerators;

import org.fractalmicro.foundation.FMLocalized;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.plist.Plist;
import org.fractalmicro.quicklook.FMQuickLookGenerator;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * What a property list says, as the tree it is.
 *
 * A plist is data rather than text, so nothing that shows words claims it. Reading it as
 * one long line of XML is the thing this exists to avoid.
 */
public final class PropertyListGenerator implements FMQuickLookGenerator {

    /** Deep enough for anything on this volume, and a stop for anything else. */
    private static final int DEEP = 32;

    @Override
    public JComponent preview(File file) {
        Object root;
        try {
            root = Plist.read(file.toPath());
        } catch (java.io.IOException | RuntimeException notReadable) {
            return null;
        }
        if (root == null) return null;

        DefaultMutableTreeNode top = new DefaultMutableTreeNode(file.getName());
        fill(top, root, DEEP);
        JTree tree = new JTree(top);
        // The top level open and no further. A whole Info.plist expanded is a wall.
        for (int row = tree.getRowCount() - 1; row >= 0; row--) tree.expandRow(row);
        tree.collapseRow(0);
        tree.expandRow(0);
        tree.getAccessibleContext().setAccessibleName(
            FMLocalized.filled(FMString.of("quicklook.propertyList"),
                               FMString.of(file.getName())).toString());
        return new JScrollPane(tree);
    }

    private static void fill(DefaultMutableTreeNode into, Object value, int left) {
        if (left <= 0) return;
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> one : map.entrySet()) {
                add(into, String.valueOf(one.getKey()), one.getValue(), left);
            }
        } else if (value instanceof List<?> list) {
            int index = 0;
            for (Object one : list) add(into, String.valueOf(index++), one, left);
        }
    }

    /** One row: what it is called, and either what it says or how much is under it. */
    private static void add(DefaultMutableTreeNode into, String key, Object value, int left) {
        int count = countOf(value);
        if (count < 0) {
            into.add(new DefaultMutableTreeNode(key + " = " + describe(value)));
            return;
        }
        FMString said = count == 1
            ? FMLocalized.filled(FMString.of("quicklook.keyOneItem"), FMString.of(key))
            : FMLocalized.filled(FMString.of("quicklook.keySomeItems"), FMString.of(key),
                                 FMString.of(String.valueOf(count)));
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(said.toString());
        into.add(node);
        fill(node, value, left - 1);
    }

    /** How many things are inside, or -1 for a value that has no inside. */
    private static int countOf(Object value) {
        if (value instanceof Map<?, ?> map) return map.size();
        if (value instanceof List<?> list) return list.size();
        return -1;
    }

    private static String describe(Object value) {
        if (value instanceof byte[] bytes) {
            return org.fractalmicro.fs.FS.formatBytes(bytes.length);
        }
        return String.valueOf(value);
    }
}
