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
echo "[test-all]: running Delite and Delite DSL tests"
sbt "; project tests; test"

# all Forge DSL tests
FORGE_BIN="$PWD/forge/bin/"

# update user path if hyperdsl Forge is not already there
if [[ ":$PATH:" == *":$FORGE_BIN:"* ]]; then
  export PATH=$FORGE_BIN:$PATH
fi

echo "[test-all]: running Forge DSL tests"

pushd .

for i in `seq 0 $((${#dsls[@]}-1))` 
do  
    dsl=${dsls[$i]} 
    update ${runners[$i]} $dsl 
    cd published/$dsl/
    sbt "; project $dsl-tests; test"
 done

popd 

echo "[test-all]: All tests finished!"
