all: build

build: ; @./feattest.sh

optiml: ; @./make-dsl.sh OptiML
optima: ; @./make-dsl.sh OptiMA
dhdl: ; @./make-dsl.sh DHDL

.PHONY: build
