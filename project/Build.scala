import sbt._
import Keys._
import sbt.std.TaskStreams

import java.io.File
import scala.collection.mutable.{ArrayBuffer,HashSet,HashMap}

object HyperDSLBuild extends Build with ForgePreprocessor {

  // -DshowSuppressedErrors=false
  System.setProperty("showSuppressedErrors", "false")

  val mavenLocal = "Maven Local" at "file://"+Path.userHome+"/.m2/repository" // for custom-built scala version

  val scalaTestCompile = "org.scalatest" % "scalatest_2.10" % "2.0.M5b"
  val scalaTest = scalaTestCompile % "test"

  val virtScala = Option(System.getenv("SCALA_VIRTUALIZED_VERSION")).getOrElse("2.10.2-RC2")
  val virtBuildSettingsBase = Defaults.defaultSettings ++ Seq(
    //resolvers := Seq(mavenLocal, prereleaseScalaTest, Resolver.sonatypeRepo("snapshots"), Resolver.sonatypeRepo("releases")),
    organization := "stanford-ppl",
    scalaOrganization := "org.scala-lang.virtualized",
    //scalaHome := Some(file(Path.userHome + "/scala/build/pack")),
    scalaVersion := virtScala,
    //scalaBinaryVersion := virtScala,
    publishArtifact in (Compile, packageDoc) := false,
    // needed for scala.tools, which is apparently not included in sbt's built in version
    libraryDependencies += "org.scala-lang.virtualized" % "scala-library" % virtScala,
    libraryDependencies += "org.scala-lang.virtualized" % "scala-compiler" % virtScala,
    libraryDependencies += "org.scala-lang" % "scala-actors" % virtScala, // for ScalaTest
    libraryDependencies += scalaTest,
    libraryDependencies += "org.apache.commons" % "commons-math" % "2.2",
    libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.4.1",
    libraryDependencies += "org.apache.mesos" % "mesos" % "0.9.0-incubating",    
    libraryDependencies += "org.apache.hadoop" % "hadoop-core" % "1.2.0",

    // used in delitec to access jars
    retrieveManaged := true,

    scalacOptions += "-Yno-generic-signatures",
    scalacOptions += "-Yvirtualize",

    //we need tests to run in isolation across all projects
    parallelExecution in Test := false,
    concurrentRestrictions in Global += Tags.limitAll(1) 
  )

  val deliteBuildSettings = virtBuildSettingsBase ++ Seq(
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    scalaSource in Test <<= baseDirectory(_ / "tests")
  )

  val forgeBuildSettings = virtBuildSettingsBase ++ Seq(
    sources in Compile <<= (sourceManaged in Compile, sources in Compile, streams) map { (dir,files,s) => files.map(preprocess(dir,_,s)) }
  )

  // build targets

  // _ forces sbt to choose it as default
  // useless base directory is to avoid compiling leftover .scala files in the project root directory
  lazy val _hyperdsl = Project("hyperdsl", file("project/boot"),
    settings = deliteBuildSettings) aggregate(lms, framework, dsls, runtime, apps, forge, tests)

  lazy val lms = Project("lms", file("virtualization-lms-core")) //additional settings are picked up in build.sbt of submodule

  lazy val framework = Project("framework", file("delite/framework"), settings = deliteBuildSettings) dependsOn(runtime, lms) // dependency on runtime because of Scopes

  lazy val deliteTest = Project("delite-test", file("delite/framework/delite-test"), settings = deliteBuildSettings ++ Seq(
    libraryDependencies += scalaTestCompile 
  )) dependsOn(framework, runtime)

  lazy val dsls = Project("dsls", file("delite/dsls"), settings = deliteBuildSettings) aggregate(optila, optiml, optiql, optimesh, optigraph, opticvx) 
  lazy val optila = Project("optila", file("delite/dsls/optila"), settings = deliteBuildSettings) dependsOn(framework, deliteTest)
  lazy val optiml = Project("optiml", file("delite/dsls/optiml"), settings = deliteBuildSettings) dependsOn(optila, deliteTest)
  lazy val optiql = Project("optiql", file("delite/dsls/optiql"), settings = deliteBuildSettings) dependsOn(framework, deliteTest)
  lazy val optimesh = Project("optimesh", file("delite/dsls/deliszt"), settings = deliteBuildSettings) dependsOn(framework, deliteTest)
  lazy val optigraph = Project("optigraph", file("delite/dsls/optigraph"), settings = deliteBuildSettings) dependsOn(framework, deliteTest)
  lazy val opticvx = Project("opticvx", file("delite/dsls/opticvx"), settings = deliteBuildSettings) dependsOn(framework, deliteTest)

  lazy val apps = Project("apps", file("delite/apps"), settings = deliteBuildSettings) aggregate(optimlApps, optiqlApps, optimeshApps, optigraphApps, opticvxApps, interopApps)
  lazy val optimlApps = Project("optiml-apps", file("delite/apps/optiml"), settings = deliteBuildSettings) dependsOn(optiml)
  lazy val optiqlApps = Project("optiql-apps", file("delite/apps/optiql"), settings = deliteBuildSettings) dependsOn(optiql)
  lazy val optimeshApps = Project("optimesh-apps", file("delite/apps/deliszt"), settings = deliteBuildSettings) dependsOn(optimesh)
  lazy val optigraphApps = Project("optigraph-apps", file("delite/apps/optigraph"), settings = deliteBuildSettings) dependsOn(optigraph)
  lazy val opticvxApps = Project("opticvx-apps", file("delite/apps/opticvx"), settings = deliteBuildSettings) dependsOn(opticvx)
  lazy val interopApps = Project("interop-apps", file("delite/apps/multi-dsl"), settings = deliteBuildSettings) dependsOn(optiml, optiql, optigraph) // dependsOn(dsls) not working

  lazy val runtime = Project("runtime", file("delite/runtime"), settings = deliteBuildSettings)

  lazy val forge = Project("forge", file("forge"), settings = forgeBuildSettings) dependsOn(lms) //additional settings are picked up in build.sbt of submodule

  //include all projects that should be built (dependsOn) and tested (aggregate)
  lazy val tests = (Project("tests", file("project/boot"), settings = deliteBuildSettings)
    dependsOn(optimlApps, optiqlApps, optigraphApps, interopApps) aggregate(framework, deliteTest, optiml, optiql, optigraph))

}

trait ForgePreprocessor {
  var preprocessorEnabled = true
  var preprocessorDebug = false

  // investigate: should the preprocessor be implemented using parser combinators or some other strategy?

  def preprocess(srcManaged: File, src: File, stream: TaskStreams[_]) = {
    def err(s: String) =
      error("[forge preprocessor]: in " + src.getName() + ", " + s)
      //stream.log.error("[forge preprocessor]: in " + src.getName() + ", " + s)
    def warn(s: String) = stream.log.warn("[forge preprocessor]: in " + src.getName() + ", " + s)

    // -- helpers

    abstract class CommentScope
    object NoComment extends CommentScope
    object CommentLine extends CommentScope
    object CommentBlock extends CommentScope {
      var nest = 0
    }

    // updates the comment scope based on the current characters
    def scanComment(scope: CommentScope, start: Int, input: Array[Byte]): (Int, CommentScope) = {
      if (start > input.length-1) (start, scope)
      else (input(start),input(start+1)) match {
        case ('/','/') if scope == NoComment => (start+2, CommentLine)
        case ('\n', _) if scope == CommentLine => (start+1, NoComment)
        case ('/','*') if scope != CommentLine =>
          CommentBlock.nest += 1
          (start+2, CommentBlock)
        case ('*','/') if scope == CommentBlock =>
          CommentBlock.nest -= 1
          if (CommentBlock.nest == 0) (start+2, NoComment)
          else (start+2, CommentBlock)
        case _ => (start, scope)
      }
    }

    def endOfWord(c: Byte): Boolean = c match {
      case ' ' | ',' | '.' | ':' | '+' | '*' | '-' | '/' | ')' | '}' | '(' | '{' | '\n' => true  // fixme: platform-specific line sep
      case _ => false
    }

    def blockIndentation(start: Int, input: Array[Byte]): Int = {
      var i = start

      // scan to first new line
      while (i < input.length && input(i) != '\n') i += 1

      // scan to next non-blank
      var j = i+1
      while (j < input.length && input(j) == ' ') j += 1

      j-i-1
    }

    def scanTo$(start: Int, input: Array[Byte]): Int = {
      var i = start
      var scope: CommentScope = NoComment
      while (i < input.length-1) {
        val (nextI, nextScope) = scanComment(scope, i, input)
        i = nextI; scope = nextScope;
        if (scope == NoComment && input(i) == '$' && input(i+1) == '{') return i
        i += 1
      }
      i+1 // not found
    }

    /**
     * These enumerate the possible lexically enclosing op statements to a formatted block,
     * which are needed as arguments in the rewritten quoted args. We only attempt to do
     * this when the preprocessor encounters a block symbol, $b{..}
     */
    abstract class OpEncoding
    case class OpFromTpeScope(name: String) extends OpEncoding {
      override def toString = "lookupOp(\""+name+"\")"
    }
    case class OpFromImpl(binding: String) extends OpEncoding {
      override def toString = binding
    }
    case class OpFromOp(grp: String, name: String) extends OpEncoding {
      override def toString = "lookupOp("+grp+",\""+name+"\")"
    }

    def scanBackToOp(start: Int, input: Array[Byte]): OpEncoding = {
      val words = Array("static","infix","direct","compiler","impl") // enclosing op definers
      var i = start
      var foundWord = ""
      val maxWordLength = words.map(_.length).max
      while (i > maxWordLength && foundWord == "") {
        for (w <- words) if (i > w.length) {
          if (input(i-w.length-1) == ' ' && endOfWord(input(i)) && input.slice(i-w.length,i).corresponds(w.getBytes){ _ == _ }) // could loop instead of slicing if we need to be more efficient
            foundWord = w
        }
        i -= 1
      }

      foundWord match {
        case "static" | "infix" | "direct" | "compiler"  =>
          var inTpeScope = true
          var startGrpIndex = -1
          var endGrpIndex = -1

          // scan forwards to extract the grp name, only if we are not in a tpe scope
          while (i < start && input(i) != '(') i += 1
          if (input(i+1) != '"') {
            inTpeScope = false
            startGrpIndex = i
            while (i < start && input(i) != ')') i += 1
            endGrpIndex = i
          }

          // scan forwards to extract the op name
          while (i < start && input(i) != '"') i += 1
          val startNameIndex = i
          i += 1
          while (i < start && input(i) != '"') i += 1
          val endNameIndex = i
          if (endNameIndex == start) err("could not find op name following op declaration")

          if (!inTpeScope) {
            if (startGrpIndex == -1 || endGrpIndex == -1) err("could not find grp name following op declaration")
            OpFromOp(new String(input.slice(startGrpIndex+1,endGrpIndex)), new String(input.slice(startNameIndex+1,endNameIndex)))
          }
          else {
            OpFromTpeScope(new String(input.slice(startNameIndex+1,endNameIndex)))
          }

        case "impl" =>
          // scan forwards to extract the op name binding
          while (i < start && input(i) != '(') i += 1
          val startOpIndex = i
          while (i < start && input(i) != ')') i += 1
          val endOpIndex = i

          if (endOpIndex == start) err("could not find op binding following 'impl' declaration")
          OpFromImpl(new String(input.slice(startOpIndex+1,endOpIndex)))

        case _ => err("could not find lexically enclosing op declaration for formatted block")
      }
    }

    def isBlockArg(arg: String) = arg.startsWith("b[")
    def endOfBlockArgName(arg: String) = if (arg.contains('(')) arg.indexOf('(') - 1 else arg.indexOf(']')
    def getCapturedArgs(arg: String, argMap: HashMap[String,String]) = {
      if (arg.contains('(')) {
        val s = arg.slice(arg.indexOf('(')+1,arg.lastIndexOf(')'))
        if (argMap.contains(s.drop(1))) Array(mapNestedArgs(s,argMap))
        else s.split(",").map(mapNestedArgs(_,argMap))
      }
      else Array[String]()
    }
    def mapNestedArgs(arg: String, argMap: HashMap[String,String]): String = {
      assert(arg.length > 0)
      "s\"\"\"" + arg(0) + argMap.getOrElse(arg.drop(1),arg.drop(1)) + "\"\"\"" // still has $ at this point
    }

    def parseBlockArguments(start: Int, input: Array[Byte], args: ArrayBuffer[String]): Int = {
      var j = start
      while (j < input.length && !endOfWord(input(j))) j += 1

      var arg = new String(input.slice(start,j))
      if (isBlockArg(arg) && input(j) == '(') {
        // add captured parameters as well
        var c = j
        var parenScope = 1
        while (c < input.length && parenScope != 0) {
          if (input(c) == '$') c = parseBlockArguments(c+1, input, args)
          else c += 1

          if (input(c) == '(') parenScope += 1
          else if (input(c) == ')') parenScope -= 1
        }
        arg = arg + new String(input.slice(j, c+1))
        j = c+1
      }
      if (!args.contains(arg)) { // slow, but order matters here
        args += arg
      }
      j
    }

    def endOfTypeArgName(arg: String) = arg.indexOf(']')
    def parseTypeArgument(start: Int, input: Array[Byte], tpeArgs: ArrayBuffer[String]): Int = {
      var j = start+2 // skip ahead of opening bracket
      while (j < input.length && input(j) != ']') {
        if (input(j) == '[')
          err("higher-kinded type arguments are not currently allowed in formatted blocks")
        j += 1
      }

      // a sketch, similar to parseBlockArguments, for allowing higher-kinded type args:
      // var bracketScope = 1
      // while (j < input.length && bracketScope != 0)) {
      //   // check for higher-kinded type arguments
      //   if (j+2 < input.length && input(j) == '$' && input(j+1) == 't' && input(j+2) == '[')
      //     j = parseTypeArgument(j+1, input, args)
      //   else
      //     j += 1
      //
      //   if (input(j) == '[') bracketScope += 1
      //   else if (input(j) == ']') bracketScope -= 1
      // }

      j += 1
      val tpeArg = new String(input.slice(start, j))
      if (!tpeArgs.contains(tpeArg)) {
        tpeArgs += tpeArg
      }
      j
    }

    def writeFormattedBlock(start: Int, input: Array[Byte], output: ArrayBuffer[Byte]): Int = {
      var i = start
      var bracketScope = 1
      var endBlock = false
      val args = new ArrayBuffer[String]()
      val argMap = new HashMap[String,String]()
      val tpeArgs = new ArrayBuffer[String]()
      val tpeArgMap = new HashMap[String,String]()
      val startCommentIndices = new ArrayBuffer[Int]()
      val endCommentIndices = new ArrayBuffer[Int]()

      // scan the block forwards, collecting identifiers
      var scope: CommentScope = NoComment
      while (i < input.length-1 && !endBlock) {
        val (nextI, nextScope) = scanComment(scope, i, input)
        if (scope == NoComment && nextScope != NoComment) {
          startCommentIndices += i
        }
        else if (scope != NoComment && nextScope == NoComment) {
          endCommentIndices += nextI
        }
        i = nextI; scope = nextScope;

        if (scope == NoComment) {
          if (input(i) == '{')
            bracketScope += 1
          else if (input(i) == '}') {
            bracketScope -= 1
          }
          if (bracketScope == 0) {
            endBlock = true
          }

          if (input(i) == '$' && input(i-1) != '\\') { // found identifier
            val j =
              if (i+2 < input.length && input(i+1) == 't' && input(i+2) == '[')
                parseTypeArgument(i+1, input, tpeArgs)
              else
                parseBlockArguments(i+1, input, args)
            i = j-1
          }
        }

        i += 1
      }

      // look for block functions that require us to have the op identifier
      // we don't do this in general because it's not necessary and doesn't play well with overloading
      val enclosingOp =
        if (args.exists(isBlockArg) || !tpeArgs.isEmpty) {
          // scan backwards until the lexically enclosing op identifier, which can be one of:
          //   static | direct | infix | compiler (grp) (name, ....)
          //   static | direct | infix | compiler (name) (....)
          //   impl (binding) (...)
          Some(scanBackToOp(i, input))
        }
        else None

      // write the formatted block
      val indent = " "*blockIndentation(start, input)
      val indentInterior = "  " + indent
      val nl = System.getProperty("line.separator")
      output ++= ("{" + nl).getBytes

      // need to add fix up block args and a prefix for positional args
      // we do this in 2 passes to handle nested args
      var argId = 1 // use a unique id, since blocks with different arguments need a different id
      for (a <- args) {
        try {
          if (isBlockArg(a)) {
            val a2 = a.slice(2,endOfBlockArgName(a))
            val i = a2.toInt
            val z: String = "arg"+argId // wtf? scala compiler error unless we break up z like this
            argMap += (a -> z)
          }
          else {
            val i = a.toInt
            val z: String = "arg"+argId
            argMap += (a -> z)
          }
          argId += 1
        }
        catch {
          case _:NumberFormatException =>
            if (isBlockArg(a)) {
              val a2 = a.slice(2,endOfBlockArgName(a))
              argMap += (a -> a2)
            }
            else {
              argMap += (a -> a)
            }
        }
      }

      for (a <- args) {
        def quoteArgName(t: String) = if (argMap(a).startsWith("arg")) t else "\""+t+"\""
        if (isBlockArg(a)) {
          val a2 = a.slice(2,endOfBlockArgName(a))
          output ++= (indentInterior + "val " + argMap(a) + " = quotedBlock("+quoteArgName(a2)+", "+enclosingOp.get+", List("+getCapturedArgs(a,argMap).mkString(",")+"))" + nl).getBytes
        }
        else {
          output ++= (indentInterior + "val " + argMap(a) + " = quotedArg("+quoteArgName(a)+")" + nl).getBytes
        }
      }

      // process type args in a similar way, but because we don't allow higher-kinded args now, only one pass is required
      for (t <- tpeArgs) {
        val t2 = t.slice(2,endOfTypeArgName(t))
        val z: String = "tpe"+t2
        tpeArgMap += (t -> z)
        try {
          val i = t2.toInt
          output ++= (indentInterior + "val " + z + " = quotedTpe("+t2+", "+enclosingOp.get+")" + nl).getBytes
        }
        catch {
          case _:NumberFormatException =>
            output ++= (indentInterior + "val " + z + " = quotedTpe(\""+t2+"\", "+enclosingOp.get+")" + nl).getBytes
        }
      }

      output ++= (indentInterior + "s\"\"\"").getBytes

      // swallow comments
      val strBlockBuf = new StringBuilder()
      var j = start
      for (k <- 0 until startCommentIndices.length) {
        strBlockBuf ++= new String(input.slice(j, startCommentIndices(k)))
        if (endCommentIndices.length > k) j = endCommentIndices(k)
        if (input.slice(startCommentIndices(k),endCommentIndices(k)).contains('\n')) strBlockBuf += '\n'
      }
      strBlockBuf ++= new String(input.slice(j, i-1))
      var strBlock = strBlockBuf.toString

      // remap args inside the input block, outside-in
      for (a <- args.reverse) {
        if (argMap.contains(a)) {
          strBlock = strBlock.replace("$"+a, "$"+argMap(a))
        }
      }
      // remap tpe args
      for (t <- tpeArgs) {
        if (tpeArgMap.contains(t)) {
          strBlock = strBlock.replace("$"+t, "$"+tpeArgMap(t))
        }
      }
      // replace escaped $
      strBlock = strBlock.replace("\\$", "$")

      // swallow the indentation, since we'll re-indent inside Forge anyways
      strBlock = strBlock.replace(indent, "").trim

      output ++= strBlock.getBytes
      output ++= ("\"\"\"" + nl).getBytes
      output ++= (indent + "}").getBytes

      // return block end index
      i
    }

    def expand(input: Array[Byte]): Option[Array[Byte]] = {
      val output = new ArrayBuffer[Byte]()

      var start = 0
      var i = scanTo$(start, input)
      val foundBlock = i < input.length

      while (i < input.length) {
        output ++= input.slice(start,i)
        val blockEnd = writeFormattedBlock(i+2, input, output)
        start = blockEnd
        i = scanTo$(start, input)
      }

      if (foundBlock) {
        // write remaining
        output ++= input.slice(start, i)
        Some(output.toArray)
      }
      else None
    }

    // --

    // preprocess entry

    if (preprocessorEnabled) {
      val dest = srcManaged / "preprocess" / src.getName()
      if (!preprocessorDebug && dest.exists() && dest.lastModified() > src.lastModified()) {
        dest
      }
      else {
        val input = IO.readBytes(src)
        val output = expand(input)
        if (output.isDefined) {
          IO.write(dest, output.get)
          dest
        }
        else {
          src
        }
      }
    }
    else {
      src
    }
  }


}
