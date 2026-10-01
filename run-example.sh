#!/usr/bin/env sh
set -eu

classes="${TMPDIR:-/tmp}/creator-agent-classes"
mkdir -p "$classes"
javac -d "$classes" src/main/java/learning/agent/*.java
java -cp "$classes" learning.agent.CreatorAgentExample
