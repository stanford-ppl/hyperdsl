#!/bin/bash

# test-all.sh
# Runs all hyperdsl tests: Delite framework, Delite DSL, and Forge DSL tests
# Used by Jenkins to verify commits.

# add new DSLs to test here
dsls=(
    "SimpleIntVector"
    "SimpleVector"
    "OptiML"
    "OptiQL"
    "OptiGraph"
    "OptiWrangler"
    )
runners=(
    "ppl.dsl.forge.examples.SimpleIntVectorDSLRunner"
    "ppl.dsl.forge.examples.SimpleVectorDSLRunner"
    "ppl.dsl.forge.dsls.optiml.OsptiMLDSLRunner"
    "ppl.dsl.forge.dsls.optiql.OptiQLDSLRunner"
    "ppl.dsl.forge.dsls.optigraph.OptiGraphDSLRunner"
    "ppl.dsl.forge.dsls.optiwrangler.OptiWranglerDSLRunner"
    )

# exit if any part of the script fails
#set -e

E_BADENV=65

RED='\033[0;31m'
NC='\033[0m' # No Color
echoerr() { echo -e "[${RED}error${NC}]: $@" 1>&2; } # 1>&2 redirects stdout to stderr. -e enables escape sequences (to read color)
echoinfo() { echo "[test-all]: $@"; } # test-all info
env_var_error() {
    echoerr "$1 environment variable is not defined. Please set it to the appropriate project root directory or run 'source init-env.sh'";
    exit $E_BADENV;
}
config_file_error() {
    echoerr "$1 is not present. Check ${DELITE_HOME}/config/delite/ for a configuration for your platform";
    exit $E_BADENV;
}
check_config_file() {
    if [ ! -f "${DELITE_HOME}/config/delite/$1" ]; then config_file_error $1; fi
}
listcontains() {
  for elem in $1; do
    [[ $elem = $2 ]] && return 0
  done
  return 1
}

# check for required env variables
if [ -z "${HYPER_HOME}" ]; then env_var_error HYPER_HOME; fi
if [ -z "${LMS_HOME}" ]; then env_var_error LMS_HOME; fi
if [ -z "${DELITE_HOME}" ]; then env_var_error DELITE_HOME; fi
if [ -z "${FORGE_HOME}" ]; then env_var_error FORGE_HOME; fi

# check for required configuration files
check_config_file CPP.xml
check_config_file BLAS.xml
if listcontains "$@" --cuda; then
    check_config_file CUDA.xml
    check_config_file cuBLAS.xml
fi

# remove previous delite runtime cache
rm -rf $DELITE_HOME/generatedCache

# run all built-in Delite tests (non-Forge tests)
echoinfo "running Delite and Delite DSL tests"
sbt -Dtests.threads=1,19 -Dtests.targets=scala,cpp "; project tests; test"
(( st = st || $? ))

# run delite test with GPU
if listcontains "$@" --cuda; then
	echoinfo "running Delite CUDA tests"
	sbt -Dtests.threads=1 -Dtests.targets=cuda "; project delite-test; test"
	(( st = st || $? ))
fi

# run all Forge DSL tests
echoinfo "running Forge DSL tests"
for i in `seq 0 $((${#dsls[@]}-1))` 
do  
    pushd .
    dsl=${dsls[$i]} 
    $FORGE_HOME/bin/update ${runners[$i]} $dsl 
    cd published/$dsl/
    echoinfo "running $dsl tests"
    sbt -Dtests.threads=1,19 -Dtests.targets=scala,cpp "; project $dsl-tests; test"
    (( st = st || $? ))
    if listcontains "$@" --cuda; then
    	echoinfo "running $dsl tests (CUDA)"
    	sbt -Dtests.threads=1 -Dtests.targets=cuda "; project $dsl-tests; test"
    	(( st = st || $? ))
    fi
    popd
done

echoinfo "All tests finished!"

if listcontains "$@" --benchmarks; then
	echoinfo "Running benchmarks"
	benchmark/benchmark.py -v -f
	(( st = st || $? ))
	echoinfo "Benchmarks finished!"
fi

exit $st
