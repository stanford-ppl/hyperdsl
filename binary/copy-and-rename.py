#!/usr/bin/env python

from optparse import OptionParser
import os, sys, fileinput

def main():
    usage = "usage: %prog <dsl name> <source directory> <dest directory>"
    parser = OptionParser(usage)

    (opts, args) = parser.parse_args()
    if len(args) < 3:
        parser.error("not enough arguments")

    copy_and_rename(args[0], args[1], args[2])

def copy_and_rename(dsl, src, dest):
    dictionary = {'HUMAN_DSL_NAME': dsl, 'LOWERCASE_DSL_NAME': dsl.lower()}
    os.system("rsync -r " + src + " " + dest)
    infiles = fileinput.FileInput(get_files(dest), inplace = 1)
    for line in infiles:
        for orig,repl in dictionary.iteritems():
            line = line.replace(orig,repl)
        sys.stdout.write(line)

def get_files(path):
    return [d + "/" + f for (d, n, fs) in os.walk(path) for f in fs]

if __name__ == "__main__":
      main()
