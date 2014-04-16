#!/usr/bin/env python
from __future__ import print_function, division

import sys
import os
import argparse
import subprocess
import time
import json

import util
import config


def main():
  parser = argparse.ArgumentParser(description="Gather performance numbers for Delite apps.")
  parser.add_argument("-v", "--verbose", action="store_true")
  parser.add_argument("-f", "--force", action="store_true",
    help="force execution even if the git repository contains uncommitted changes")
  parser.add_argument("-r", "--runs", type=int, default="10",
    help="number of times to run the apps")
  parser.add_argument("apps", type=str, nargs="*", default=config.default_apps, help="apps to run")

  args = parser.parse_args()

  # collect the apps to run
  apps = []
  dsls = []
  for a in args.apps:
    if (a not in config.apps):
      print("error: app {0} not found in config file".format(a), file=sys.stderr)
      exit(-1)
    app = config.apps[a]
    if(app.dsl not in dsls):
      dsls.append(app.dsl)
      if(args.verbose):
        print("notice: identified dsl {0}".format(app.dsl.name))
    apps.append(app)
    if(args.verbose):
      print("notice: identified app {0}".format(app.name))

  # chdir to the hyperdsl root directory
  hyperdsl_root = util.chdir_hyperdsl_root()

  # check that there are no changes to the repository
  git_status = subprocess.check_output("git status -s", shell=True)
  if((not args.force) and (git_status != "")):
    print("error: hyperdsl repository contains uncommitted changes", file=sys.stderr)
    print("       commit these changes before running benchmark.py", file=sys.stderr)
    exit(-1)

  # identify the hash associated with the current branch
  git_hash = subprocess.check_output("git rev-parse --short HEAD", shell=True).strip()
  if(args.verbose):
    print("notice: identified git hash {0}".format(git_hash), file=sys.stderr)

  if(args.verbose):
    print("notice: creating directory for experimental results", file=sys.stderr)
  subprocess.check_call("rm -f benchmark/times/latest", shell=True)
  subprocess.check_call("mkdir -p benchmark/times/{0}".format(git_hash), shell=True)
  subprocess.check_call("ln -s {0} benchmark/times/latest".format(git_hash), shell=True)

  if(args.verbose):
    print("notice: publishing forge dsls", file=sys.stderr)
  for dsl in dsls:
    if dsl.needs_publish:
      if(args.verbose):
        print("notice: publishing {0}".format(dsl.name), file=sys.stderr)
      subprocess.check_call(dsl.publish_command, 
        stdout=open(hyperdsl_root + "/benchmark/times/{0}/{1}.publish.out".format(git_hash, dsl.name), "w"), 
        stderr=open(hyperdsl_root + "/benchmark/times/{0}/{1}.publish.err".format(git_hash, dsl.name), "w"), 
        shell=True)

  delite_options = "-r {0}".format(args.runs)
  if(args.verbose):
    delite_options += " -v"

  if(args.verbose):
    print("notice: running apps", file=sys.stderr)
  for app in apps:
    if(args.verbose):
      print("notice: staging {0}".format(app.name), file=sys.stderr)
    os.chdir(app.dsl.run_dir)
    subprocess.call(app.stage_command(), 
      stdout=open(hyperdsl_root + "/benchmark/times/{0}/{1}.delitec.out".format(git_hash, app.name), "w"), 
      stderr=open(hyperdsl_root + "/benchmark/times/{0}/{1}.delitec.err".format(git_hash, app.name), "w"), 
      shell=True)
    for c in app.configs:
      if(args.verbose):
        print("notice: running {0} under configuration {1}".format(app.name, c.name))
      opts = " -Dstats.dump -Dstats.dump.component=app -Dstats.dump.overwrite -Dstats.output.dir={0} -Dstats.output.filename={1}-{2}.times {3}".format(
        hyperdsl_root + "/benchmark/times/" + git_hash, app.name, c.name, os.getenv("JAVA_OPTS", ""))
      os.putenv("JAVA_OPTS", opts)
      subprocess.call(app.run_command(c, delite_options), 
        stdout=open(hyperdsl_root + "/benchmark/times/{0}/{1}-{2}.delite.out".format(git_hash, app.name, c.name), "w"), 
        stderr=open(hyperdsl_root + "/benchmark/times/{0}/{1}-{2}.delite.err".format(git_hash, app.name, c.name), "w"), 
        shell=True)
    os.chdir(hyperdsl_root)




if __name__ == "__main__":
  main()
