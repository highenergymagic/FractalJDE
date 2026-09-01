@echo off
rem Double-click launcher for the Fractal desktop.
cd /d "%~dp0"
start "" javaw --enable-preview --enable-native-access=ALL-UNNAMED -jar build\FractalJDE.jar
