#!/bin/bash
dsl=$1
lcdsl=`echo "$dsl" | tr '[:upper:]' '[:lower:]'`

if [ $dsl = "SimpleVector" ]
then
  lcdsl="examples"
else
  lcdsl="dsls.$lcdsl"
fi

forge/bin/update ppl.dsl.forge.${lcdsl}.${1}DSLRunner $1
