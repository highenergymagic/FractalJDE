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
package org.fractalmicro.app;

import org.fractalmicro.appkit.FMApplicationDelegate;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.foundation.FMURL;
import org.fractalmicro.foundation.FMArray;
import org.fractalmicro.ui.Finder;



import java.io.File;

/** The Finder. Opening it makes a window, the way clicking its Dock tile does. */
public final class FinderApp implements FMApplicationDelegate {

    @Override public void open() {
        Finder.newWindow(null);
    }

    /** The parts of the Finder something else can ask for by name. */
    @Override public void openPart(org.fractalmicro.foundation.FMString part) {
        if (part != null && "connect-to-server".equals(part.toString())) {
            Finder.connectToServer();
            return;
        }
        open();
    }

    @Override public void openURLs(FMArray<FMURL> urls) {
        for (FMURL url : urls) {
            File f = url.asFile();
            Finder.goTo(f.isDirectory() ? f : f.getParentFile());
        }
    }
}
