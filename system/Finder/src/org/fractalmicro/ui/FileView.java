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

import javax.swing.JComponent;
import java.util.List;

/** What every Finder view can do, whichever way it draws things. */
public interface FileView {
    JComponent component();

    void setContents(List<Node> nodes);

    List<Node> selection();

    void selectAll();

    void focusView();

    /** Sort key: Name, Date Modified, Size or Kind. */
    void arrangeBy(String key);

    void setIconSize(int px);

    /**
     * Lets files be dragged out of this view and dropped into it.
     *
     * The folder is asked for rather than handed over, because a view outlives any one of
     * them: the same icon view shows a different folder every time somebody opens one, and
     * a destination that remembered which folder it was when the window opened would file
     * things in the wrong place from the second folder onwards.
     */
    void allowDragging(java.util.function.Supplier<java.io.File> showing);
}
