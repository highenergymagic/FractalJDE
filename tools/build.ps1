# Copyright (C) 2026 by Fractal Microsystems, Inc.
# Use is subject to license terms. See LICENSE.
#
# Builds FractalJDE on Windows by running the one real build.
#
# There is deliberately no second build here. Keeping a PowerShell copy of a build that
# compiles a framework, six applications and the checks in the right order means keeping
# two of them correct, and the copy is the one that quietly stops being correct.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$sh = Get-Command sh -ErrorAction SilentlyContinue
if (-not $sh) {
    Write-Error "This needs 'sh', which comes with Git for Windows. Install Git, or run tools/build.sh from Git Bash."
    exit 1
}
& $sh.Source tools/build.sh @args
