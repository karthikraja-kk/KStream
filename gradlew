#!/usr/bin/env sh

# Gradle start script (simplified but working)

DIR="$(cd "$(dirname "$0")" && pwd)"

JAVA_EXEC="java"

exec "$JAVA_EXEC" -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"