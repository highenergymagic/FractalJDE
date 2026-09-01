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
package org.fractalmicro.ui;

import org.fractalmicro.fs.Node;

import java.util.Comparator;
import java.util.List;

/** Sort orders shared by every view and by the Arrange By menu. */
public final class Sorting {
    private Sorting() {}

    public static void sort(List<Node> nodes, String key) {
        Comparator<Node> c;
        switch (key == null ? "Name" : key) {
            case "Date Modified":
                c = Comparator.comparingLong((Node n) -> n.modified).reversed();
                break;
            case "Size":
                c = Comparator.comparingLong((Node n) -> n.size).reversed();
                break;
            case "Kind":
                c = Comparator.comparing(Node::kindLabel).thenComparing(n -> n.name, String.CASE_INSENSITIVE_ORDER);
                break;
            default:
                c = Comparator.comparing((Node n) -> !n.isContainer())
                              .thenComparing(n -> n.name, String.CASE_INSENSITIVE_ORDER);
        }
        nodes.sort(c);
    }
}
