#!/bin/bash

find src/main/java -name "*.java" -print0 | xargs -0 javac -d target/classes
java -cp target/classes pso.filesystem.ConsoleMain
