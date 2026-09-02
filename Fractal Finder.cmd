@echo off
rem Double-click launcher for the Fractal desktop, out of a checkout.
rem
rem java rather than javaw, and in this window rather than beside it, because coming up
rem takes a few seconds and this is where it says how it is getting on. The window is the
rem system console: the kernel, launchd and the session all report into it, and closing it
rem stops the system, the way closing a terminal stops what was started from it.
rem
rem For a boot screen instead of a console, use Fractal.exe. It reads the same lines.
title Fractal console
cd /d "%~dp0"
java --enable-preview --enable-native-access=ALL-UNNAMED -jar build\FractalJDE.jar %*
if errorlevel 1 pause
