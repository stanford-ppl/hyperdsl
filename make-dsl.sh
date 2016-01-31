#!/bin/bash
dsl=$1
lcdsl=`echo "$dsl" | tr '[:upper:]' '[:lower:]'`

forge/bin/update ppl.dsl.forge.dsls.${lcdsl}.${1}DSLRunner $1
