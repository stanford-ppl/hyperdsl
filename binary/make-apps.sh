#!/bin/bash

cp -r $FORGE_HOME/apps/DHDL/src/* $HYPER_HOME/published/DHDL/apps/src
cd $HYPER_HOME/published/DHDL
sbt compile
