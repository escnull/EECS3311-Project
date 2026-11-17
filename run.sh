#!/usr/bin/env sh
# Builds with plain javac (no Maven needed) and runs the game.
# Usage: ./run.sh            -> GUI
#        ./run.sh --cli      -> command line
#        ./run.sh --offline  -> no LLM (testing only); can be combined with --cli
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
java -cp out investigation.Main "$@"
