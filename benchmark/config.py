# configuration file for benchmark.py


class Dsl(object):
  def __init__(self, name, runner_class = None):
    self.name = name
    if(runner_class == None):
      self.runner_class = "ppl.dsl.forge.dsls.%s.%sDSLRunner" % (name.lower(), name)
    else:
      self.runner_class = runner_class

  def update_command(self):
    return "update %s %s" % (self.runner_class, self.name)

class App(object):
  def __init__(self, dsl, name, args, configs, compiler_class = None, interpreter_class = None, delite_options = "", delitec_options = ""):
    self.name = name
    self.dsl = dsl
    self.args = args
    if(compiler_class == None):
      self.compiler_class = name + "Compiler"
    else:
      self.compiler_class = compiler_class
    if(interpreter_class == None):
      self.interpreter_class = name + "Interpreter"
    else:
      self.interpreter_class = interpreter_class
    self.configs = configs
    self.delite_options = delite_options
    self.delitec_options = delitec_options

  def stage_command(self):
    return "bin/delitec %s %s" % (self.delitec_options, self.compiler_class)

  def run_command(self, c, extra_options):
    return "bin/delite %s %s %s %s %s" % (self.delite_options, c.delite_options, extra_options, self.compiler_class, self.args)

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

dsls = [OptiML]

configs = [ Config.smp(1), Config.smp(4), Config.smp(16) ]

apps = [
  App(OptiML, "LogReg", "/kunle/ppl/delite/data/ml/logreg/x1m10.dat /kunle/ppl/delite/data/ml/logreg/y1m.dat", configs),
  App(OptiML, "NaiveBayes", "/kunle/ppl/delite/data/ml/nb/MATRIX.TRAIN /kunle/ppl/delite/data/ml/nb/MATRIX.TEST", configs,
    compiler_class="NBCompiler", interpreter_class="NBInterpreter"),
  App(OptiML, "GDA", "/kunle/ppl/delite/data/ml/gda/q1x.dat /kunle/ppl/delite/data/ml/gda/q1y.dat", configs)
]
