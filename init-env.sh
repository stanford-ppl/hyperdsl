#!/bin/bash

export FORGE_HOME=`pwd`/forge
export DELITE_HOME=`pwd`/delite
export LMS_HOME=`pwd`/virtualization-lms-core

ln -sfh `pwd`/lib_managed/ `pwd`/delite/lib_managed
ln -sfh `pwd`/lib_managed/ `pwd`/forge/lib_managed

