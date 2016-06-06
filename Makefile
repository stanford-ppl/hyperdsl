all: dhdl

vector: ; @./binary/make-dsl.sh SimpleVector
optila: ; @./binary/make-dsl.sh OptiLA
optiml: ; @./binary/make-dsl.sh OptiML
optima: ; @./binary/make-dsl.sh OptiMA
dhdl: ; @./binary/make-dsl.sh DHDL
dadl: ; @./binary/make-dsl.sh DADL
apps: ; @./binary/make-apps.sh

