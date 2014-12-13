#!/bin/bash

# test-all.sh
# Runs all hyperdsl tests: Delite framework, Delite DSL, and Forge DSL tests
# Used by Jenkins to verify commits.

# add new DSLs to test here
dsls=( "SimpleVector" "OptiML" "OptiQL" "OptiGraph" "OptiWrangler" )
runners=( "ppl.dsl.forge.examples.SimpleVectorDSLRunner" "ppl.dsl.forge.dsls.optiml.OptiMLDSLRunner" "ppl.dsl.forge.dsls.optiql.OptiQLDSLRunner" "ppl.dsl.forge.dsls.optigraph.OptiGraphDSLRunner" "ppl.dsl.forge.dsls.optiwrangler.OptiWranglerDSLRunner" )

# exit if any part of the script fails
set -e

E_BADENV=65

# check for required env variables
if [ -z "${HYPER_HOME}" ]; then echo error: HYPER_HOME is not defined; exit $E_BADENV; fi
if [ -z "${LMS_HOME}" ]; then echo error: LMS_HOME is not defined; exit $E_BADENV; fi
if [ -z "${DELITE_HOME}" ]; then echo error: DELITE_HOME is not defined; exit $E_BADENV; fi
if [ -z "${FORGE_HOME}" ]; then echo error: FORGE_HOME is not defined; exit $E_BADENV; fi

# check for required configuration files
if [ ! -f "${DELITE_HOME}/config/delite/CPP.xml" ]; then echo error: CPP.xml is not present; exit $E_BADENV; fi
if [ ! -f "${DELITE_HOME}/config/delite/BLAS.xml" ]; then echo error: BLAS.xml is not present; exit $E_BADENV; fi
if [ ! -f "${DELITE_HOME}/config/delite/CUDA.xml" ]; then echo error: CUDA.xml is not present; exit $E_BADENV; fi
if [ ! -f "${DELITE_HOME}/config/delite/cuBLAS.xml" ]; then echo error: cuBLAS.xml is not present; exit $E_BADENV; fi

# remove previous delite runtime cache
rm -rf $DELITE_HOME/generatedCache

# Generate a neural network for testing
cd ${FORGE_HOME}/apps/OptiML/src/NeuralNetwork/
sed -i 's/apps\/src\/NeuralNetwork\/examples\/mnist/\/data\/ml\/nnet\/mnist_small/' mnist.xml
python generate_cnn.py mnist.xml
# Modify the parameters
cd mnist_tutorial/
sed -i 's/10\ \;\ Num\ epochs\ between\ testing\ on\ validation\ set/1\ \;\ Num\ epochs\ between\ testing\ on\ validation\ set/' global_params.txt
sed -i 's/1000\ \;\ Test\ mini-batch\ size,\ validation\ set/100\ \;\ Test\ mini-batch\ size,\ validation\ set/' global_params.txt
sed -i 's/100\ \;\ Mini-Batch\ size/40\ \;\ Mini-Batch\ size/' global_params.txt
sed -i 's/0.01\ \;\ Learning\ Rate/0.05\ \;\ Learning\ Rate/' layer_*_params.txt
cd ${HYPER_HOME}

# all non-Forge tests
echo "[test-all]: running Delite and Delite DSL tests (1 thread)"
sbt -Dtests.threads=1 -Dtests.targets=scala,cpp "; project tests; test"

# and again multi-threaded
echo "[test-all]: running Delite and Delite DSL tests (8 threads)"
sbt -Dtests.threads=8 -Dtests.targets=scala,cpp "; project tests; test"

# delite test with GPU
echo "[test-all]: running Delite Cuda tests"
sbt -Dtests.threads=1 -Dtests.targets=cuda "; project delite-test; test"

# all Forge DSL tests
echo "[test-all]: running Forge DSL tests"

for i in `seq 0 $((${#dsls[@]}-1))` 
do  
    pushd .
    dsl=${dsls[$i]} 
    $FORGE_HOME/bin/update ${runners[$i]} $dsl 
    cd published/$dsl/
    echo "[test-all]: running $dsl tests (1 thread)"
    sbt -Dtests.threads=1 -Dtests.targets=scala,cpp,cuda "; project $dsl-tests; test"
    echo "[test-all]: running $dsl tests (8 threads)"
    sbt -Dtests.threads=8 -Dtests.targets=scala,cpp "; project $dsl-tests; test"
    popd
 done

echo "[test-all]: All tests finished!"

echo "[test-all]: Running benchmarks"

benchmark/benchmark.py -v -f

echo "[test-all]: Benchmarks finished!"

