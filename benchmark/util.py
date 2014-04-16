#!/usr/bin/env python
from __future__ import print_function, division

import sys
import os
import subprocess
import time
import json


def chdir_hyperdsl_root():
  # first, change the current working directory to the hyperdsl root
  hyperdsl_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
  if(os.path.abspath(os.getcwd()) != hyperdsl_root):
    print("warning: benchmark.py not invoked from root of hyperdsl directory.", file=sys.stderr)
    print("         attempting to cd to [{0}]".format(hyperdsl_root), file=sys.stderr)
    os.chdir(hyperdsl_root)
    if (os.path.abspath(os.getcwd()) != hyperdsl_root):
      print("error: unable to cd to hyperdsl root directory.", file=sys.stderr)
      exit(-1)
    print("         directory changed successfully!")

  # check that the root of the git repo is equal to the root of the hyperdsl repo
  try:
    git_root = subprocess.check_output("git rev-parse --show-toplevel", shell=True).strip()
    if(git_root != hyperdsl_root):
      print("error: git root in unexpected location")
      exit(-1)
  except subprocess.CalledProcessError as e:
    print("error: unable to call git.", file=sys.stderr)
    exit(-1)

  return hyperdsl_root

