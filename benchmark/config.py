# configuration file for benchmark.py


class Dsl(object):
  def __init__(self, name, run_dir = None, publish_command = None, needs_publish = True):
    self.name = name
    if(publish_command == None):
      self.publish_command = "forge/bin/update ppl.dsl.forge.dsls.%s.%sDSLRunner %s" % (name.lower(), name, name)
    else:
      self.publish_command = publish_command
    if(run_dir == None):
      self.run_dir = "published/" + name
    else:
      self.run_dir = run_dir
    self.needs_publish = needs_publish

class App(object):
  def __init__(self, dsl, name, args, configs, runner_class = None, delite_options = "", delitec_options = ""):
    self.name = name
    self.dsl = dsl
    self.args = args
    if(runner_class == None):
      self.runner_class = name + "Compiler"
    else:
      self.runner_class = runner_class
    self.configs = configs
    self.delite_options = delite_options
    self.delitec_options = delitec_options

  def stage_command(self):
    return "bin/delitec -v --cpp %s %s" % (self.delitec_options, self.runner_class)

  def run_command(self, c, runs, verbose):
    if(c.run_only_once):
      extra_options = "-r 1"
    else:
      extra_options = "-r {0}".format(runs)
    if(verbose):
      extra_options += " -v"
    return "bin/delite %s %s %s %s %s" % (self.delite_options, c.delite_options, extra_options, self.runner_class, self.args)

class Config(object):
  def __init__(self, name, delite_options, run_only_once = False):
    self.name = name
    self.delite_options = delite_options
    self.run_only_once = run_only_once

  @staticmethod
  def smp(threads):
    return Config("smp%d" % threads, "-t %d" % threads)

  @staticmethod
  def cpp(threads):
    return Config("cpp%d" % threads, "-t 1 --cpp %d" % threads)

  @staticmethod
  def gpu():
    return Config("gpu", "-t 1 --gpu")

OptiML = Dsl("OptiML")
OptiQL = Dsl("OptiQL")
OptiGraph = Dsl("OptiGraph")
Delite = Dsl("Delite", "delite", "sbt \"; project tests; compile\"; rm -rf delite/lib_managed; cp -r lib_managed delite")

dsls = [OptiML, OptiQL ,OptiGraph, Delite]

configs = [ 
  Config.smp(1), Config.smp(2), Config.smp(4), Config.smp(8),
  Config.cpp(1), Config.cpp(2), Config.cpp(4), Config.cpp(8)
]

apps = {}

apps["gda"] = App(OptiML, "GDA", "/data/ml/gda/1024-1200x.dat /data/ml/gda/q1y.dat", configs)
apps["logreg"] = App(OptiML, "LogReg", "/data/ml/logreg/x1m10.dat /data/ml/logreg/y1m.dat", configs)
apps["kmeans"] = App(OptiML, "kMeans", "/data/ml/kmeans/mandrill-large.dat /data/ml/kmeans/initmu.dat", configs)
apps["rbm"] = App(OptiML, "RBM", "/data/ml/rbm/mnist2000.dat 2000 1000", configs)
apps["svm"] = App(OptiML, "SVM", "/data/ml/svm/MATRIX.TRAIN.100 /data/ml/svm/MATRIX.TEST", configs)
apps["naivebayes"] = App(OptiML, "NaiveBayes", "/data/ml/nb/MATRIX.TRAIN.RANDOM.250K /data/ml/nb/MATRIX.TEST", configs,
  runner_class="NBCompiler")
apps["query1"] = App(OptiQL, "TPCHQ1", "/data/query/SF1", configs)
apps["query6"] = App(OptiQL, "TPCHQ6", "/data/query/SF1", configs)
apps["query14"] = App(OptiQL, "TPCHQ14", "/data/query/SF1", configs)
apps["pagerank"] = App(OptiGraph, "PageRank", "/data/graph/higgs.edgelist /data/graph/prout.txt", configs)
apps["undirectedtrianglecounting"] = App(OptiGraph, "UndirectedTriangleCounting", "/data/graph/higgs.edgelist", configs)


apps["delite_gda"] = App(Delite, "DeliteGDA", "/data/ml/gda/1024-1200x.dat /data/ml/gda/q1y.dat", configs,
  runner_class="ppl.apps.ml.gda.GDARunner")
apps["delite_logreg"] = App(Delite, "DeliteLogReg", "/data/ml/logreg/x1m10.dat /data/ml/logreg/y1m.dat", configs, 
  runner_class="ppl.apps.ml.logreg.LogRegRunner", delitec_options="--ns")
apps["delite_kmeans"] = App(Delite, "DelitekMeans", "/data/ml/kmeans/mandrill-large.dat /data/ml/kmeans/initmu.dat", configs,
  runner_class="ppl.apps.ml.kmeans.kmeansRunner")
apps["delite_rbm"] = App(Delite, "DeliteRBM", "/data/ml/rbm/mnist2000.dat 2000 1000", configs,
  runner_class="ppl.apps.ml.rbm.RBMRunner")
apps["delite_svm"] = App(Delite, "DeliteSVM", "/data/ml/svm/MATRIX.TRAIN.100 /data/ml/svm/MATRIX.TEST", configs,
  runner_class="ppl.apps.ml.svm.SVMRunner")
apps["delite_naivebayes"] = App(Delite, "DeliteNaiveBayes", "/data/ml/nb/MATRIX.TRAIN.RANDOM.250K /data/ml/nb/MATRIX.TEST", configs,
  runner_class="ppl.apps.ml.nb.NaiveBayesRunner")
apps["delite_query1"] = App(Delite, "DeliteTPCHQ1", "/data/query/SF1", configs,
  runner_class="ppl.apps.dataquery.tpch.TPCHQ1")

default_apps = [ 
  "gda", "logreg", "kmeans", "rbm", "naivebayes", 
  "query1", "query6", "query14", "pagerank", "undirectedtrianglecounting", 
  "delite_query1", "delite_gda", "delite_logreg", "delite_kmeans", "delite_rbm", "delite_naivebayes" 
]

default_comparison_plots = [
  "gda,delite_gda",
  "logreg,delite_logreg",
  "kmeans,delite_kmeans",
  "rbm,delite_rbm",
  "naivebayes,delite_naivebayes",
  "query1,delite_query1"
]

