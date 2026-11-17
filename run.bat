@echo off
REM Builds with plain javac (no Maven needed) and runs the game.
REM Usage: run.bat           -> GUI
REM        run.bat --cli     -> command line
REM        run.bat --offline -> no LLM (testing only)
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\main\java\*.java > sources.txt
javac -d out @sources.txt
del sources.txt
java -cp out investigation.Main %*
