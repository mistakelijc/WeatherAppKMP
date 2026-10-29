#!/bin/sh
set -eu
if [ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-NO}" = "YES" ]; then
    exit 0
fi
cd "${SRCROOT:?Xcode must set SRCROOT}/.."
# Xcode launched from Finder may not inherit JAVA_HOME from the terminal.
if [ -z "${JAVA_HOME:-}" ]; then
    if java_home=$(/usr/libexec/java_home 2>/dev/null); then
        export JAVA_HOME="$java_home"
    else
        for jdk in /Applications/Android\ Studio*.app/Contents/jbr/Contents/Home; do
            if [ -x "$jdk/bin/java" ]; then
                export JAVA_HOME="$jdk"
                break
            fi
        done
    fi
fi
if [ -z "${JAVA_HOME:-}" ]; then
    echo "A JDK 17+ installation is required. Set JAVA_HOME before building." >&2
    exit 1
fi
./gradlew :shared:embedAndSignAppleFrameworkForXcode
