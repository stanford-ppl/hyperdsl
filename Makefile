all: build

build: ; @./feattest.sh

optila: ; @./make-dsl.sh OptiLA
optiml: ; @./make-dsl.sh OptiML
optima: ; @./make-dsl.sh OptiMA
dhdl: ; @./make-dsl.sh DHDL
dadl: ; @./make-dsl.sh DADL
apps: ; @./make-apps.sh

.PHONY: build
