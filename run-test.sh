#!/usr/bin/env sh
set -eu

classes="${TMPDIR:-/tmp}/creator-agent-test-classes"
mkdir -p "$classes"
javac -d "$classes" src/main/java/learning/agent/*.java src/test/java/learning/agent/*.java
java -cp "$classes" learning.agent.CreatorAgentServiceTest
