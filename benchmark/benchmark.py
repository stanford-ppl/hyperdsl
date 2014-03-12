#!/usr/bin/env python
from __future__ import print_function, division

import sys
import os
import argparse
import subprocess
import time

import config


def main():
  parser = argparse.ArgumentParser(description="Gather performance numbers for Delite apps.")
  parser.add_argument("-v", "--verbose", action="store_true")
  parser.add_argument("-f", "--force", action="store_true",
    help="force execution even if the git repository contains uncommitted changes")
  parser.add_argument("-r", "--runs", type=int, default="10",
    help="number of times to run the apps")
  parser.add_argument("-s", "--skip-report", action="store_true", help="skip generating a report")
  parser.add_argument("-b", "--bars", type=int, default="3", help="number of bars to display in graphs")

  args = parser.parse_args()

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
  subprocess.check_call("mkdir -p benchmark/times/{0}".format(git_hash), shell=True)

  if(args.verbose):
    print("notice: compiling dsls", file=sys.stderr)
  for dsl in config.dsls:
    if(args.verbose):
      print("notice: compiling {0}".format(dsl.name), file=sys.stderr)
    subprocess.check_call(dsl.update_command(), stdout=sys.stderr, stderr=sys.stderr, shell=True)

  delite_options = "-r {0}".format(args.runs)
  if(args.verbose):
    delite_options += " -v"

  if(args.verbose):
    print("notice: running apps", file=sys.stderr)
  for app in config.apps:
    if(args.verbose):
      print("notice: staging {0}".format(app.name), file=sys.stderr)
    os.chdir("published/" + app.dsl.name)
    subprocess.check_call(app.stage_command(), stdout=sys.stderr, stderr=sys.stderr, shell=True)
    for c in app.configs:
      if(args.verbose):
        print("notice: running {0} under configuration {1}".format(app.name, c.name))
      opts = " -Dstats.dump -Dstats.dump.component=app -Dstats.dump.overwrite -Dstats.output.dir={0} -Dstats.output.filename={1}-{2}.times {3}".format(
        hyperdsl_root + "/benchmark/times/" + git_hash, app.name, c.name, os.getenv("JAVA_OPTS", ""))
      os.putenv("JAVA_OPTS", opts)
      subprocess.check_call(app.run_command(c, delite_options), stdout=sys.stderr, stderr=sys.stderr, shell=True)
    os.chdir(hyperdsl_root)

  if(args.skip_report):
    return 0

  # identify the most recent timed hashes
  if(args.verbose):
    print("notice: searching for previous times", file=sys.stderr)
  report_hashes = []
  git_log = subprocess.check_output("git log --pretty=format:%h", shell=True).strip().split("\n")
  for gh in git_log:
    if(os.path.isdir("benchmark/times/" + gh)):
      if(args.verbose):
        print("notice: identified timed hash " + gh, file=sys.sterrr)
      report_hashes.append(gh)
      if(len(report_hashes) >= args.bars):
        break
  else:
    if(args.verbose):
      print("notice: ran out of previous hashes", file=sys.sterrr)

  if(git_hash not in report_hashes):
    print("error: couldn't find data for the runs we just did")

  # load data for all hashes
  report_data = [loadData(h, args.verbose) for h in report_hashes]

  # write out the report
  with open("benchmark/times/{0}/report.html".format(git_hash), "w") as freport:
    # write the report head
    print(report_head.format(git_hash), file=freport)
    # write the report title
    print("  <h1>Performance Comparison for Commit [{0}]</h1>".format(git_hash), file=freport)
    print("  <p>Plots generated at {0}.</p>".format(time.strftime("%c")), file=freport)
    # start writing out the plots
    print("  <script>", file=freport)
    for app in config.apps:
      print("    makePerformancePlot([", file=freport)
      for c in app.configs:
        rs = "      {{ \"Configuration\": \"{0}\", ".format(c.name)
        rs += ", ".join("\"{0}\": \"{1:.4f}\"".format(h, v[app.name + "/" + c.name]) for (h, v) in zip(report_hashes, report_data))
        rs += " },"
        print(rs, file=freport)
      print("    ], \"{0}/{1} Performance Comparison\");".format(app.dsl.name, app.name), file=freport)
    #finish the plots
    print("  </script>", file=freport)
    # and close the tags
    print("</body>", file=freport)
    print("</html>", file=freport)


def loadData(git_hash, verbose):
  if(verbose):
    print("notice: loading data for hash " + git_hash, file=sys.stderr)
  rv = {}
  for app in config.apps:
    for c in app.configs:
      cafn = "benchmark/times/{0}/{1}-{2}.times".format(git_hash, app.name, c.name)
      if(os.path.isfile(cafn)):
        with open(cafn, "r") as f:
          catimes = f.read().strip().split("\n")
          # discard the first half of the entries
          catimes = catimes[(len(catimes) // 2):]         
          rv[app.name + "/" + c.name] = sum(float(t)*1e-6 for t in catimes)/len(catimes)
      else:
        rv[app.name + "/" + c.name] = 0
  return rv



report_head = """<!DOCTYPE html>
<html>
<head>
  <title>[{0}] Performance Comparison</title>
  <meta charset="utf-8">
  <style>
    body {{
      font: 10pt sans-serif;
    }}

    .axis path,
    .axis line {{
      fill: none;
      stroke: #000;
      shape-rendering: crispEdges;
    }}

    .d3-tip {{
      line-height: 1;
      font-weight: bold;
      padding: 12px;
      background: rgba(0, 0, 0, 0.8);
      color: #fff;
      border-radius: 2px;
    }}

    /* Creates a small triangle extender for the tooltip */
    .d3-tip:after {{
      box-sizing: border-box;
      display: inline;
      font-size: 10px;
      width: 100%;
      line-height: 1;
      color: rgba(0, 0, 0, 0.8);
      content: "\\25BC";
      position: absolute;
      text-align: center;
    }}

    /* Style northward tooltips differently */
    .d3-tip.n:after {{
      margin: -1px 0 0 0;
      top: 100%;
      left: 0;
    }}
  </style>
  <script src="http://d3js.org/d3.v3.min.js"></script>
  <script src="d3-tip.js"></script>
  <script>
    function makePerformancePlot(data, title) {{

      var margin = {{top: 40, right: 20, bottom: 30, left: 40}},
          width = 500 - margin.left - margin.right,
          height = 300 - margin.top - margin.bottom;

      var x0 = d3.scale.ordinal()
          .rangeRoundBands([0, width], .1);

      var x1 = d3.scale.ordinal();

      var y = d3.scale.linear()
          .range([height, 0]);

      var color = d3.scale.category10();

      var xAxis = d3.svg.axis()
          .scale(x0)
          .orient("bottom");

      var yAxis = d3.svg.axis()
          .scale(y)
          .orient("left")
          .tickFormat(d3.format(".2s"));

      var svg = d3.select("body").append("p")
        .append("svg")
          .attr("width", width + margin.left + margin.right)
          .attr("height", height + margin.top + margin.bottom)
        .append("g")
          .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

      var series = d3.keys(data[0]).filter(function(key) {{ return key !== "Configuration"; }});

      data.forEach(function(d) {{
        d.ages = series.map(function(name) {{ return {{name: name, value: +d[name]}}; }});
      }});

      x0.domain(data.map(function(d) {{ return d.Configuration; }}));
      x1.domain(series).rangeRoundBands([0, x0.rangeBand()]);
      y.domain([0, d3.max(data, function(d) {{ return d3.max(d.ages, function(d) {{ return d.value; }}); }})]);

      var tip = d3.tip()
        .attr('class', 'd3-tip')
        .offset([-10, 0])
        .html(function(d) {{
          return "<strong>Time:</strong> " + d.value + " s";
        }});

      svg.call(tip);

      svg.append("g")
          .attr("class", "x axis")
          .attr("transform", "translate(0," + height + ")")
          .call(xAxis);

      svg.append("g")
          .attr("class", "y axis")
          .call(yAxis)
        .append("text")
          .attr("transform", "rotate(-90)")
          .attr("y", 6)
          .attr("dy", ".71em")
          .style("text-anchor", "end")
          .text("run time (s)");

      var configuration = svg.selectAll(".configuration")
          .data(data)
        .enter().append("g")
          .attr("class", "g")
          .attr("transform", function(d) {{ return "translate(" + x0(d.Configuration) + ",0)"; }});

      configuration.selectAll("rect")
          .data(function(d) {{ return d.ages; }})
        .enter().append("rect")
          .attr("width", x1.rangeBand())
          .attr("x", function(d) {{ return x1(d.name); }})
          .attr("y", function(d) {{ return y(d.value); }})
          .attr("height", function(d) {{ return height - y(d.value); }})
          .style("fill", function(d) {{ return color(d.name); }})
          .on("mouseover", tip.show)
          .on("mouseout", tip.hide);

      svg.append("text")
          .attr("x", (width / 2))             
          .attr("y", 0 - (margin.top / 2))
          .attr("text-anchor", "middle")  
          .style("font-size", "16px") 
          .style("text-decoration", "underline")  
          .text(title);

      var legend = svg.selectAll(".legend")
          .data(series.slice())
        .enter().append("g")
          .attr("class", "legend")
          .attr("transform", function(d, i) {{ return "translate(0," + i * 20 + ")"; }});

      legend.append("rect")
          .attr("x", width - 18)
          .attr("width", 18)
          .attr("height", 18)
          .style("fill", color)
          .style("stroke", "black");

      legend.append("text")
          .attr("x", width - 24)
          .attr("y", 9)
          .attr("dy", ".35em")
          .style("text-anchor", "end")
          .text(function(d) {{ return d; }});
    }}
  </script>
</head>
<body>
"""


if __name__ == "__main__":
  main()
