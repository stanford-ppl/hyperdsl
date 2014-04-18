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
  parser = argparse.ArgumentParser(description="Generate performance reports for Delite apps.")
  parser.add_argument("-v", "--verbose", action="store_true")
  parser.add_argument("-b", "--bars", type=int, default="3", 
    help="number of bars to display in version comparison plots")
  app_c_group = parser.add_mutually_exclusive_group()
  app_c_group.add_argument("-a", "--app-comparison", type=str, nargs="*", default=[],
    help="comma-separated list of apps to generate an app comparison plot for")
  app_c_group.add_argument("-A", "--app-comparison-default", action="store_true",
    help="use default apps for app comparison")
  ver_c_group = parser.add_mutually_exclusive_group()
  ver_c_group.add_argument("-c", "--ver-comparison", type=str, nargs="*", default=[],
    help="app to generate version comparison plot for")
  ver_c_group.add_argument("-C", "--ver-comparison-default", action="store_true",
    help="use default apps for version comparison")
  xml_group = parser.add_mutually_exclusive_group()
  xml_group.add_argument("-x", "--xml", type=str, nargs="*", default=[],
    help="app to generate spreadsheet for")
  xml_group.add_argument("-X", "--xml-default", action="store_true",
    help="use default apps for spreadsheet generation")

  args = parser.parse_args()

  # collect the apps to analyze
  vc_apps_arg = args.ver_comparison
  if (args.ver_comparison_default):
    vc_apps_arg = config.default_apps
  xml_apps_arg = args.xml
  if (args.xml_default):
    xml_apps_arg = config.default_apps
  ac_apps_arg = args.app_comparison
  if (args.app_comparison_default):
    ac_apps_arg = config.default_comparison_plots

  apps = []
  vc_apps = []
  for a in vc_apps_arg:
    if(a not in config.apps):
      print("error: app {0} not found in config file".format(a), file=sys.stderr)
      exit(-1)
    vc_apps.append(config.apps[a])
    if(args.verbose):
      print("notice: identified version-comparison app {0}".format(config.apps[a].name), file=sys.stderr)
    if(config.apps[a] not in apps):
      apps.append(config.apps[a])
  xml_apps = []
  for a in xml_apps_arg:
    if(a not in config.apps):
      print("error: app {0} not found in config file".format(a), file=sys.stderr)
      exit(-1)
    xml_apps.append(config.apps[a])
    if(args.verbose):
      print("notice: identified spreadsheet app {0}".format(config.apps[a].name), file=sys.stderr)
    if(config.apps[a] not in apps):
      apps.append(config.apps[a])
  ac_apps = []
  for c in ac_apps_arg:
    ac = []
    for a in c.split(","):
      if(a not in config.apps):
        print("error: app {0} not found in config file".format(a), file=sys.stderr)
        exit(-1)
      ac.append(config.apps[a])
      if(config.apps[a] not in apps):
        apps.append(config.apps[a])
    ac_apps.append(ac)
    if(args.verbose):
      print("notice: identified app-comparison {0}".format(",".join(a.name for a in ac)), file=sys.stderr)

  # chdir to the hyperdsl root directory
  hyperdsl_root = util.chdir_hyperdsl_root()

  # identify the most recent timed hashes
  if(args.verbose):
    print("notice: searching for previous times", file=sys.stderr)
  report_hashes = []
  git_log = subprocess.check_output("git log --pretty=format:%h", shell=True).strip().split("\n")
  for gh in git_log:
    if(os.path.isdir("benchmark/times/" + gh)):
      if(args.verbose):
        print("notice: identified timed hash " + gh, file=sys.stderr)
      report_hashes.append(gh)
      if(len(report_hashes) >= args.bars):
        break
  else:
    if(args.verbose):
      print("notice: ran out of previous hashes", file=sys.stderr)

  git_hash = report_hashes[0]

  # load data for all hashes
  report_data = [loadData(h, apps, args.verbose) for h in report_hashes]

  # generate the plots
  plot_data = []
  for aa in ac_apps:
    vc_plot = {}
    vc_plot["title"] = "{0} Performance Comparison".format(" vs ".join(a.name for a in aa))
    vc_plot["series"] = [a.name for a in aa]
    vc_plot["data"] = []
    for c in aa[0].configs:
      cc_data = {}
      cc_data["xlabel"] = c.name
      for a in aa:
        cc_data[a.name] = discardWarmup(report_data[0][a.name + "/" + c.name])
      vc_plot["data"].append(cc_data)
    plot_data.append(vc_plot)

  for app in vc_apps:
    vc_plot = {}
    vc_plot["title"] = "{0}/{1} Performance Comparison".format(app.dsl.name, app.name)
    vc_plot["series"] = report_hashes
    vc_plot["data"] = []
    for c in app.configs:
      cc_data = {}
      cc_data["xlabel"] = c.name
      for (h, d) in zip(report_hashes, report_data):
        cc_data[h] = discardWarmup(d[app.name + "/" + c.name])
      vc_plot["data"].append(cc_data)
    plot_data.append(vc_plot)

  # make report directory
  subprocess.check_call("mkdir -p benchmark/times/{0}/report".format(git_hash), shell=True)

  # write out the report
  with open("benchmark/times/{0}/report/report.html".format(git_hash), "w") as freport:
    # write the report head
    print(report_head.format(git_hash), file=freport)
    # write the report title
    print("  <h1>Performance Comparison for Commit [{0}]</h1>".format(git_hash), file=freport)
    print("  <p>Plots generated at {0}.</p>".format(time.strftime("%c")), file=freport)
    # write out the plots
    print("  <script>", file=freport)
    print("    document.plotData = " + json.dumps(plot_data) + ";", file=freport)
    print("  </script>", file=freport)
    # and close the tags
    print("</body>", file=freport)
    print("</html>", file=freport)

  # make the xml report
  with open("benchmark/times/{0}/report/report.xml".format(git_hash), "w") as fxml:
    print(xml_head, file=fxml)
    print("  <Worksheet ss:Name=\"{0}\">".format(app.name), file=fxml)
    print("    <Table>", file=fxml)
    for app in xml_apps:
      print("      <Row>", file=fxml)
      print("        <Cell ss:MergeAcross=\"4\" ss:StyleID=\"s6\"><Data ss:Type=\"String\">{0}/{1}</Data></Cell>".format(app.dsl.name, app.name), file=fxml)
      print("      </Row>", file=fxml)
      print("      <Row>", file=fxml)
      print("        <Cell ss:StyleID=\"s3\"><Data ss:Type=\"String\">Run</Data></Cell>".format(c.name), file=fxml)
      for c in app.configs:
        print("        <Cell ss:StyleID=\"s3\"><Data ss:Type=\"String\">{0}</Data></Cell>".format(c.name), file=fxml)
      print("      </Row>", file=fxml)
      for i in range(max(len(report_data[0][app.name + "/" + c.name]) for c in app.configs)):
        print("      <Row>", file=fxml)
        print("        <Cell ss:StyleID=\"s4\"><Data ss:Type=\"Number\">{0}</Data></Cell>".format(i+1), file=fxml)
        for c in app.configs:
          print("        <Cell ss:StyleID=\"s1\"><Data ss:Type=\"Number\">{0}</Data></Cell>".format(report_data[0][app.name + "/" + c.name][i]), file=fxml)
        print("      </Row>", file=fxml)
      print("      <Row>", file=fxml)
      print("        <Cell ss:StyleID=\"s2\"><Data ss:Type=\"String\">Mean</Data></Cell>".format(c.name), file=fxml)
      for c in app.configs:
        lca = len(report_data[0][app.name + "/" + c.name])
        print("        <Cell ss:StyleID=\"s2\" ss:Formula=\"=AVERAGE(R{0}C:R{1}C)\"></Cell>".format(lca // 2 + 2, lca + 1), file=fxml)
      print("      </Row>", file=fxml)
      print("      <Row>", file=fxml)
      print("        <Cell ss:StyleID=\"s5\"><Data ss:Type=\"String\">Speedup</Data></Cell>".format(c.name), file=fxml)
      for c in app.configs:
        lca = len(report_data[0][app.name + "/" + c.name])
        print("        <Cell ss:StyleID=\"s5\" ss:Formula=\"=R[-1]C2/R[-1]C\"></Cell>", file=fxml)
      print("      </Row>", file=fxml)
      print("      <Row></Row>", file=fxml)
    print("    </Table>", file=fxml)
    print("  </Worksheet>", file=fxml)
    print("</Workbook>", file=fxml)


def loadData(git_hash, apps, verbose):
  if(verbose):
    print("notice: loading data for hash " + git_hash, file=sys.stderr)
  rv = {}
  for app in apps:
    for c in app.configs:
      cafn = "benchmark/times/{0}/{1}-{2}.times".format(git_hash, app.name, c.name)
      if(os.path.isfile(cafn)):
        with open(cafn, "r") as f:    
          rv[app.name + "/" + c.name] = [float(t)*1e-6 for t in f.read().strip().split("\n")]
      else:
        rv[app.name + "/" + c.name] = []
  return rv

def discardWarmup(catimes):
  return catimes[(len(catimes) // 2):]


report_head = """<!DOCTYPE html>
<html>
<head>
  <title>[{0}] Performance Comparison</title>
  <meta charset="utf-8">
  <link rel="stylesheet" href="http://arsenalfc.stanford.edu/kogroup/benchmark/benchmark.css">
  <script src="http://d3js.org/d3.v3.min.js"></script>
  <script src="http://arsenalfc.stanford.edu/kogroup/benchmark/d3-tip.js"></script>
  <script src="http://arsenalfc.stanford.edu/kogroup/benchmark/benchmark.js"></script>
</head>
<body>"""

xml_head = """<?xml version="1.0"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
    xmlns:o="urn:schemas-microsoft-com:office:office"
    xmlns:x="urn:schemas-microsoft-com:office:excel"
    xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
    xmlns:html="http://www.w3.org/TR/REC-html40">
  <Styles>
    <Style ss:ID="s1">
     <Alignment ss:Vertical="Bottom"/>
     <Font ss:FontName="Calibri" x:Family="Swiss" ss:Size="12" ss:Color="#000000"/>
     <NumberFormat ss:Format="0.000"/>
     <Interior/>
    </Style>
    <Style ss:ID="s2">
     <Font ss:FontName="Calibri" x:Family="Swiss" ss:Size="12" ss:Color="#000000" ss:Bold="1"/>
     <Interior ss:Color="#A2BD90" ss:Pattern="Solid"/>
     <NumberFormat ss:Format="0.000"/>
    </Style>
    <Style ss:ID="s3">
     <Font ss:FontName="Calibri" ss:Size="12" ss:Color="#FFFFFF" ss:Bold="1" ss:Italic="1"/>
     <Interior ss:Color="#000000" ss:Pattern="Solid"/>
    </Style>
    <Style ss:ID="s4">
     <Alignment ss:Horizontal="Left" ss:Vertical="Bottom"/>
     <Font ss:FontName="Calibri" x:Family="Swiss" ss:Size="12" ss:Color="#000000"/>
     <Interior ss:Color="#969696" ss:Pattern="Solid"/>
    </Style>
    <Style ss:ID="s5">
     <Font ss:FontName="Calibri" x:Family="Swiss" ss:Size="12" ss:Color="#000000" ss:Bold="1"/>
     <Interior ss:Color="#FFCC00" ss:Pattern="Solid"/>
     <NumberFormat ss:Format="0.000"/>
    </Style>
    <Style ss:ID="s7">
     <Font ss:FontName="Calibri" ss:Size="12" ss:Color="#FFFFFF" ss:Bold="1" ss:Underline="Single"/>
     <Interior ss:Color="#000000" ss:Pattern="Solid"/>
    </Style>
  </Styles>"""

if __name__ == "__main__":
  main()
