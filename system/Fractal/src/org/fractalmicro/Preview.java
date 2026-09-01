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
package org.fractalmicro;

/**
 * A class that will not load without --enable-preview.
 *
 * It does nothing and is never called. What matters is how it was compiled: anything built
 * with preview enabled is stamped as such, and a virtual machine started without the flag
 * refuses to load it. So trying to load this answers a question the runtime offers no
 * other way to ask, which the launcher needs before it decides whether to start itself
 * again with the flag on.
 *
 * The launcher used to ask about the layer that calls Windows, which is stamped the same
 * way for a real reason. That class is not in a released kernel, and a missing class looks
 * nothing like a refused one: the launcher would have read the difference as good news and
 * carried on into a system that could not load. This ships wherever the launcher does.
 */
final class Preview {
    private Preview() {}
}
