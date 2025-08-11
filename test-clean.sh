#!/bin/bash

# RTD GTFS Pipeline - Clean Test Runner
# Runs Maven tests with suppressed warnings for clean output

# Default to running all tests if no arguments provided
if [ $# -eq 0 ]; then
    echo "Running all tests with clean output..."
    mvn test 2>/dev/null
else
    # Pass all arguments to Maven test command
    echo "Running tests: $@"
    mvn test "$@" 2>/dev/null
fi

# Check exit code
if [ $? -eq 0 ]; then
    echo "✅ Tests completed successfully"
else
    echo "❌ Tests failed - run 'mvn test $@' to see full output"
    exit 1
fi