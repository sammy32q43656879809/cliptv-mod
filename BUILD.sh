#!/usr/bin/env bash
set -e
echo "ClipTV Mod Builder"
echo "=================="
echo
echo "Checking Java..."
java -version
echo
echo "Building Fabric jar..."
chmod +x gradlew
./gradlew :fabric:remapJar --no-daemon
echo
echo "Building NeoForge jar..."
./gradlew :neoforge:remapJar --no-daemon
echo
echo "Done! Your jars are in:"
echo "  fabric/build/libs/clapcraft-cliptv-fabric-1.0.0.jar"
echo "  neoforge/build/libs/clapcraft-cliptv-neoforge-1.0.0.jar"
