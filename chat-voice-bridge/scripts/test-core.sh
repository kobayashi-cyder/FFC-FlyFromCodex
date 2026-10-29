#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT
javac -encoding UTF-8 -d "$build_dir" \
  "$root/app/src/main/java/jp/three_domain/chatvoice/SpeechTextFormatter.java" \
  "$root/tests/SpeechTextFormatterTest.java"
java -cp "$build_dir" SpeechTextFormatterTest
