#!/usr/bin/env bash

mvn -q compile
cd target/classes
java myFileSystem "$@"
