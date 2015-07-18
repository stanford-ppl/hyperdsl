# exit if any part of the script fails
if [ "$1" != "--no-benchmarks" ]; then set -e; fi

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

echo "[test-asplos]: running ASPLOS benchmarks"
sbt -Dtests.threads=1,4 -Dtests.targets=scala "; project asplos-apps; run"
