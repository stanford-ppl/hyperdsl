#!/bin/bash

# test-all.sh
# Runs all hyperdsl tests: Delite framework, Delite DSL, and Forge DSL tests
# Used by Jenkins to verify commits.

# add new DSLs to test here
dsls=( "OptiML" )
runners=( "ppl.dsl.forge.dsls.optiml.OptiMLDSLRunner" )

# exit if any part of the script fails
set -e

# all non-Forge tests
echo "[test-all]: running Delite and Delite DSL tests (1 thread)"
export JAVA_OPTS="-Dtests.threads=1"
sbt "; project tests; test"

# and again multi-threaded
echo "[test-all]: running Delite and Delite DSL tests (8 threads)"
export JAVA_OPTS="-Dtests.threads=8"
sbt "; project tests; test"

# all Forge DSL tests
echo "[test-all]: running Forge DSL tests"

pushd .

for i in `seq 0 $((${#dsls[@]}-1))` 
do  
    dsl=${dsls[$i]} 
    $FORGE_HOME/bin/update ${runners[$i]} $dsl 
    cd published/$dsl/
    echo "[test-all]: running $dsl tests (1 thread)"
    export JAVA_OPTS="-Dtests.threads=1"
    sbt "; project $dsl-tests; test"
    echo "[test-all]: running $dsl tests (8 threads)"
    export JAVA_OPTS="-Dtests.threads=8"
    sbt "; project $dsl-tests; test"
 done

popd 

echo "[test-all]: All tests finished!"
