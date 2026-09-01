#CDDL HEADER START
# The contents of this file are subject to the terms of the
# Common Development and Distribution License, Version 1.0 only
# (the "License").  You may not use this file except in compliance
# with the License.
# You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
# or http://illumos.org/license/CDDL.
# See the License for the specific language governing permissions
# and limitations under the License.
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at usr/src/OPENSOLARIS.LICENSE.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: 
#
# CDDL HEADER END
# Copyright (C) 2026 by Fractal Microsystems, Inc.
# Use is subject to license terms.
# Starts the desktop. Arguments are passed through:
#   .\run.ps1 --open $env:USERPROFILE\Documents
#   .\run.ps1 --selftest              checks everything offscreen and prints a report
#   .\run.ps1 --screenshot shot.png   renders offscreen; nothing appears on screen

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$flags = @('--enable-preview', '--enable-native-access=ALL-UNNAMED')

# The checking modes print to the console, so they need java rather than javaw.
$needsConsole = $false
foreach ($a in $args) {
    if ($a -in @('--selftest', '--dump-accessibility', '--screenshot', '--native-report')) {
        $needsConsole = $true
    }
}
$launcher = if ($needsConsole) { 'java' } else { 'javaw' }

if (Test-Path 'FractalFinder.jar') {
    & $launcher @flags -jar FractalFinder.jar @args
} elseif (Test-Path 'out\fractal\Main.class') {
    & $launcher @flags -cp out org.fractalmicro.Main @args
} else {
    Write-Host "Nothing built yet. Run .\build.ps1 first."
}
