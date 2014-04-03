# configuration file for benchmark.py


class Dsl(object):
  def __init__(self, name, run_dir = None, publish_command = None, needs_publish = True):
    self.name = name
    if(publish_command == None):
      self.publish_command = "update ppl.dsl.forge.dsls.%s.%sDSLRunner %s" % (name.lower(), name, name)
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
    return "bin/delitec %s %s" % (self.delitec_options, self.runner_class)

  def run_command(self, c, extra_options):
    return "bin/delite %s %s %s %s %s" % (self.delite_options, c.delite_options, extra_options, self.runner_class, self.args)

class Config(object):
  def __init__(self, name, delite_options):
    self.name = name
    self.delite_options = delite_options

  @staticmethod
  def smp(threads):
    return Config("smp%d" % threads, "-t %d" % threads)

  @staticmethod
  def gpu():
    return Config("gpu", "-t 1 --gpu")

OptiML = Dsl("OptiML")
Delite = Dsl("Delite", "delite", "cd delite; sbt update; cd ..; sbt \"; project optiml-apps; compile\"")

dsls = [OptiML, Delite]

configs = [ Config.smp(1), Config.smp(2), Config.smp(4), Config.smp(8) ]

logreg = App(OptiML, "LogReg", "/kunle/ppl/delite/data/ml/logreg/x1m10.dat /kunle/ppl/delite/data/ml/logreg/y1m.dat", configs)
naivebayes = App(OptiML, "NaiveBayes", "/kunle/ppl/delite/data/ml/nb/MATRIX.TRAIN /kunle/ppl/delite/data/ml/nb/MATRIX.TEST", configs,
  runner_class="NBCompiler")
gda = App(OptiML, "GDA", "/kunle/ppl/delite/data/ml/gda/q1x.dat /kunle/ppl/delite/data/ml/gda/q1y.dat", configs)
delite_logreg = App(Delite, "DeliteLogReg", "/kunle/ppl/delite/data/ml/logreg/x1m10.dat /kunle/ppl/delite/data/ml/logreg/y1m.dat", configs, 
  runner_class="ppl.apps.ml.logreg.LogRegRunner")
delite_naivebayes = App(Delite, "DeliteNaiveBayes", "/kunle/ppl/delite/data/ml/nb/MATRIX.TRAIN /kunle/ppl/delite/data/ml/nb/MATRIX.TEST", configs,
  runner_class="ppl.apps.ml.nb.NaiveBayesRunner")

apps = [ naivebayes, delite_naivebayes ]

app_comparison_plots = [ [naivebayes, delite_naivebayes] ]


