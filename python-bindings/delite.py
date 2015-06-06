from __future__ import print_function

import hashlib
import os
import subprocess
import inspect
import sys


hyperdsl_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

class DSL(object):
  def __init__(self, name):
    self.name = name
    self.namespace = name.lower()
    self.compiler_class = name + "ApplicationCompiler"
    self.intepreter_class = name + "ApplicationInterpreter"
    self.application_class = name + "Application"

  def source_dir(self):
    source_dir = hyperdsl_root + "/published/" + self.name + "/apps/src/python-generated/"
    if not os.path.isdir(source_dir):
      os.mkdir(hyperdsl_root + "/published/" + self.name + "/apps/src/python-generated/")
    return source_dir

  def published_dir(self):
    return hyperdsl_root + "/published/" + self.name

  def emit_imports(self, f):
    print("import %s.compiler._" % self.namespace, file=f)
    print("import %s.library._" % self.namespace, file=f)
    print("import %s.shared._" % self.namespace, file=f)
    print("", file=f)

  def emit_input(self, f, input_name, input_type, input_id):
    raise NotImplementedError()

  def pass_input(self, input_valud, input_type):
    raise NotImplementedError()

  def process_inputs(self, i, ext_locals):
    if isinstance(i, str):
      n = i
      x = ext_locals[i]
      t = type(x)
    elif isinstance(i, tuple):
      if(len(i) == 2):
        n = i[0]
        x = ext_locals[i[0]]
        t = i[1]
      elif(len(i) == 3):
        n = i[0]
        t = i[1]
        x = i[2]
      else:
        raise Exception("invalid input %s" % i)
    else:
      raise Exception("invalid input %s" % i)
    return (n, x, t)

  def inline(self, code, inputs, output_type=str):
    # first, get the local environment of the caller
    ext_locals = inspect.currentframe().f_back.f_locals
    # process the inputs
    ext_inputs = [self.process_inputs(i, ext_locals) for i in inputs]
    # generate a hash to associate with this program
    h = hashlib.md5()
    h.update(code)
    codehash = h.hexdigest()
    # determine the source directory
    source_dir = self.source_dir()
    # generate an app source file
    f = open("%s/Py%s.scala" % (source_dir, codehash), "w")
    # write the app header
    print("// this file generated for python inline app %s\n" % codehash, file=f)
    self.emit_imports(f)
    print("object Py%sCompiler extends %s with Py%s" % (codehash, self.compiler_class, codehash), file=f)
    print("object Py%sInterpreter extends %s with Py%s" % (codehash, self.compiler_class, codehash), file=f)
    print("trait Py%s extends %s {" % (codehash, self.application_class), file=f)
    print("  def main() = {", file=f)
    # produce code to read the inputs
    for (k, (n, x, t)) in enumerate(ext_inputs):
      self.emit_input(f, n, t, k)
    print("\n", file=f)
    print("    val py_return_value = {", file=f)
    # indent the code
    print("\n".join("      " + x for x in code.split("\n")), file=f)
    print("}\n", file=f)
    print("println(\"[[BEGIN PROGRAM OUTPUT]]\")", file=f)
    print("println(py_return_value)", file=f)
    print("println(\"[[END PROGRAM OUTPUT]]\")", file=f)
    print("  }\n}\n", file=f)
    # and close the file
    f.close()
    # next, cd to the published directory
    old_cwd = os.getcwd()
    os.chdir(self.published_dir())
    # and compile the generated code
    subprocess.check_call("sbt compile >/dev/null", shell=True)
    # next, run delitec to generate code for the app
    subprocess.check_call("bin/delitec Py%sCompiler >/dev/null" % codehash, shell=True)
    # assemble the inputs
    a_inputs = " ".join(self.pass_input(x, t) for (n, x, t) in ext_inputs)
    # finally, run delite
    a_output = subprocess.check_output("bin/delite Py%sCompiler %s" % (codehash, a_inputs), shell=True)
    t_output = a_output.split("[[BEGIN PROGRAM OUTPUT]]\n")[1].split("\n[[END PROGRAM OUTPUT]]")[0]
    return output_type(t_output)

def escape_string(s):
  return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""


class OptiMLType(DSL):
  def __init__(self):
    super(OptiMLType, self).__init__("OptiML")

  def emit_input(self, f, input_name, input_type, input_id):
    if(input_type == int):
      print("val %s: Rep[Int] = args(%d).toInt" % (input_name, input_id), file=f)
    elif(input_type == str):
      print("val %s: Rep[String] = args(%d)" % (input_name, input_id), file=f)
    elif(input_type == float):
      print("val %s: Rep[Double] = args(%d).toDouble" % (input_name, input_id), file=f)
    else:
      raise NotImplmentedError()

  def pass_input(self, input_value, input_type):
    if(input_type == int):
      return "%d" % input_value
    elif(input_type == str):
      # need to escape the string this much to pass it safely through all the shell invocations
      return escape_string(escape_string(escape_string(input_value)))
    elif(input_type == float):
      return "%g" % input_value
    else:
      raise NotImplmentedError()

OptiML = OptiMLType()
