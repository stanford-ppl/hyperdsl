import sbt._
import Keys._
import java.io.File

import forge.preprocessor._

object HyperDSLBuild extends Build with ForgePreprocessor {

  if (System.getProperty("showSuppressedErrors") == null) System.setProperty("showSuppressedErrors", "false")

  val virtScala = Option(System.getenv("SCALA_VIRTUALIZED_VERSION")).getOrElse("2.11.2")
  val scalaTest = "org.scalatest" % "scalatest_2.11" % "2.2.2"
  val virtBuildSettingsBase = Defaults.defaultSettings ++ Seq(
    organization := "stanford-ppl",
    scalaVersion := virtScala,
    publishArtifact in (Compile, packageDoc) := false,
    libraryDependencies += "org.scala-lang" % "scala-library" % virtScala,
    libraryDependencies += "org.scala-lang" % "scala-compiler" % virtScala,
    libraryDependencies += scalaTest,

    libraryDependencies += "org.apache.commons" % "commons-math" % "2.2",
    libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.5.0",
    libraryDependencies += "org.apache.mesos" % "mesos" % "0.20.1",
    libraryDependencies += "org.apache.hadoop" % "hadoop-common" % "2.7.1",
    libraryDependencies += "org.apache.hadoop" % "hadoop-client" % "2.7.1",
    libraryDependencies += "org.apache.hadoop" % "hadoop-hdfs" % "2.7.1",
    libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.8.7",
    libraryDependencies += "junit" % "junit" % "4.11" % "test",


    retrieveManaged := true,
    scalacOptions += "-Yno-generic-signatures",

    //we need tests to run in isolation across all projects
    parallelExecution in Test := false,
    concurrentRestrictions in Global := (Tags.limitAll(1) :: Nil)
  )

  val deliteBuildSettings = virtBuildSettingsBase ++ Seq(
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    scalaSource in Test <<= baseDirectory(_ / "tests"),

    libraryDependencies ++= (
      if (scalaVersion.value.startsWith("2.10")) List("org.scalamacros" %% "quasiquotes" % "2.0.1")
      else Nil
    ),

    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "compile",

    addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)
  )

  val forgeBuildSettings = virtBuildSettingsBase ++ Seq(
    sources in Compile <<= (sourceManaged in Compile, sources in Compile, streams) map { (dir,files,s) => files.map(preprocess(dir,_,s)) }
  )

  // build targets
  //root directory makes this the default project
  lazy val hyperdsl = Project("hyperdsl", file("."), settings = deliteBuildSettings) aggregate(virtualization, lms, framework, runtime, deliteTest, forge)

  // additional settings are picked up in build.sbt of submodule

  lazy val virtualization = Project("virtualization", file("scala-virtualized"))

  lazy val lms = Project("lms", file("virtualization-lms-core")) dependsOn(virtualization)

  lazy val runtime = Project("runtime", file("delite/runtime"), settings = deliteBuildSettings)
  lazy val framework = Project("framework", file("delite/framework"), settings = deliteBuildSettings) dependsOn(virtualization , runtime, lms) // dependency on runtime because of Scopes?
  
  lazy val deliteTest = Project("delite-test", file("delite/framework/delite-test"), settings = deliteBuildSettings) dependsOn(framework, runtime)

  lazy val dsls = Project("dsls", file("delite/dsls"), settings = deliteBuildSettings) aggregate(optiql)
  lazy val optiql = Project("optiql", file("delite/dsls/optiql"), settings = deliteBuildSettings) dependsOn(framework, deliteTest)

  lazy val apps = Project("apps", file("delite/apps"), settings = deliteBuildSettings) aggregate(optiqlApps)
  lazy val optiqlApps = Project("optiql-apps", file("delite/apps/optiql"), settings = deliteBuildSettings) dependsOn(optiql)

  // additional settings are picked up in build.sbt of submodule
  lazy val forge = Project("forge", file("forge"), settings = forgeBuildSettings) dependsOn(virtualization, lms) 

  // include all projects that should be built and tested in 'aggregate'
  lazy val tests = Project("tests", file("project/boot"), settings = deliteBuildSettings) aggregate(runtime, lms, framework, deliteTest, dsls, apps)

  // add the macro paradise compiler plugin
  lazy val paradise = Seq(
    libraryDependencies += {
      val paradiseVersion =
        if (scalaVersion.value == "2.11.2") "2.0.1"
      else"2.0.0"
      compilerPlugin("org.scalamacros" % "paradise" %  paradiseVersion cross CrossVersion.full)
    },
    scalacOptions := defaultScalacOptions
  )

  lazy val defaultScalacOptions = Seq("-deprecation", "-feature", "-language:higherKinds", "-language:implicitConversions")
}
