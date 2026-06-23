#!/bin/bash
# Cross-platform Maven wrapper selector for spotless

if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" || "$OSTYPE" == "cygwin" ]]; then
  # Windows
  ./mvnw.cmd spotless:$1 -q
else
  # Unix-like (macOS, Linux)
  ./mvnw spotless:$1 -q
fi
