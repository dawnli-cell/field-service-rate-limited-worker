#!/usr/bin/env sh
set -eu
classes="${TMPDIR:-/tmp}/field-service-worker-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" example.fieldservice.FieldServiceExample
