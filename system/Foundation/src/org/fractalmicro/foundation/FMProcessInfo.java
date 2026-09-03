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

import org.fractalmicro.os.SystemProfile;
import org.fractalmicro.os.Version;

/**
 * What this machine is and what is running on it, for a program that wants to say so.
 *
 * The questions a program asks about the machine it is on are always the same handful:
 * what system is this, which version, what is it called, how much memory has it, how many
 * processors. NSProcessInfo is where a Cocoa program asks them, and this is that.
 *
 * It existed here already, spread over two classes with no prefix on either, which meant
 * a program asking any of it was reaching past the platform into its plumbing. The plumbing
 * is still where the answers come from; what is new is that there is one door and it is
 * named the way the rest of the vocabulary is.
 */
public final class FMProcessInfo {

    private static final FMProcessInfo SHARED = new FMProcessInfo();

    private FMProcessInfo() {}

    /** The one for this process, as NSProcessInfo has always been reached. */
    public static FMProcessInfo processInfo() { return SHARED; }

    /* ------------------------------------------------------------ the system */

    /** What the system is called: FractalJDE. */
    public FMString operatingSystemName() { return FMString.of(SystemProfile.OS_NAME); }

    /** Its version and build, the way About This Computer says them. */
    public FMString operatingSystemVersionString() { return FMString.of(Version.full()); }

    /** Just the number, for something comparing one version against another. */
    public FMString operatingSystemVersion() { return FMString.of(Version.number()); }

    /** The build, which is the moment it was built written short. */
    public FMString operatingSystemBuild() { return FMString.of(Version.build()); }

    /** When that was, in words. */
    public FMString operatingSystemBuiltAt() { return FMString.of(Version.builtAt()); }

    /** The name in full, which is what a specification sheet says. */
    public FMString operatingSystemLongName() { return FMString.of(SystemProfile.longName()); }

    /** Who makes it. */
    public FMString operatingSystemVendor() { return FMString.of(SystemProfile.vendor()); }

    /* ------------------------------------------------------------ the machine */

    /** What this machine is called on the network and to itself. */
    public FMString hostName() { return FMString.of(SystemProfile.computerName()); }

    /** The processor, as a person would read it rather than as a model number. */
    public FMString processorDescription() { return FMString.of(SystemProfile.processor()); }

    /** How many there are, which is the runtime's own answer and not a guess. */
    public int processorCount() { return Runtime.getRuntime().availableProcessors(); }

    /** How much memory the machine has, written the way a specification sheet writes it. */
    public FMString physicalMemoryDescription() { return FMString.of(SystemProfile.memory()); }

    /** Which disk it started from. */
    public FMString startupDisk() { return FMString.of(SystemProfile.startupDisk()); }

    /* ------------------------------------------------------------ this process */

    /** The number this system gave this process, which is not the host's. */
    public int taskIdentifier() { return org.fractalmicro.kernel.Tasks.self(); }

    /** And the host's, for a listing that shows both because both are real. */
    public long hostProcessIdentifier() { return ProcessHandle.current().pid(); }
}
