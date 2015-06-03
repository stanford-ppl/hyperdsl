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
    scalaOrganization := "org.scala-lang.virtualized",
    scalaVersion := virtScala,
    publishArtifact in (Compile, packageDoc) := false,
    
    //normal scala for the runtime and compiling generated code
    libraryDependencies += "org.scala-lang" % "scala-library" % virtScala, 
    libraryDependencies += "org.scala-lang" % "scala-compiler" % virtScala,
    libraryDependencies += scalaTest,

    libraryDependencies += "org.apache.commons" % "commons-math" % "2.2",
    libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.5.0",
    libraryDependencies += "org.apache.mesos" % "mesos" % "0.20.1",
    libraryDependencies += "org.apache.hadoop" % "hadoop-common" % "2.5.1",
    libraryDependencies += "org.apache.hadoop" % "hadoop-client" % "2.5.1",
    libraryDependencies += "org.apache.hadoop" % "hadoop-hdfs" % "2.5.1",

    retrieveManaged := true,
    scalacOptions += "-Yno-generic-signatures",
    scalacOptions += "-Yvirtualize",

    //we need tests to run in isolation across all projects
    parallelExecution in Test := false,
    concurrentRestrictions in Global := (Tags.limitAll(1) :: Nil)
  )

  val deliteBuildSettings = virtBuildSettingsBase ++ Seq(
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    scalaSource in Test <<= baseDirectory(_ / "tests")
  )

  val forgeBuildSettings = virtBuildSettingsBase ++ Seq(
    sources in Compile <<= (sourceManaged in Compile, sources in Compile, streams) map { (dir,files,s) => files.map(preprocess(dir,_,s)) }
  )

  // build targets
  //root directory makes this the default project
  lazy val hyperdsl = Project("hyperdsl", file("."),
    settings = deliteBuildSettings) aggregate(lms, framework, runtime, deliteTest, forge)

  lazy val lms = Project("lms", file("virtualization-lms-core")) // additional settings are picked up in build.sbt of submodule

  lazy val framework = Project("framework", file("delite/framework"), settings = deliteBuildSettings) dependsOn(runtime, lms) // dependency on runtime because of Scopes
  lazy val deliteTest = Project("delite-test", file("delite/framework/delite-test"), settings = deliteBuildSettings) dependsOn(framework, runtime)

  lazy val dsls = Project("dsls", file("delite/dsls"), settings = deliteBuildSettings) aggregate(optiql)
  lazy val optiql = Project("optiql", file("delite/dsls/optiql"), settings = deliteBuildSettings) dependsOn(framework, deliteTest)

  lazy val apps = Project("apps", file("delite/apps"), settings = deliteBuildSettings) aggregate(optiqlApps)
  lazy val optiqlApps = Project("optiql-apps", file("delite/apps/optiql"), settings = deliteBuildSettings) dependsOn(optiql)

  lazy val runtime = Project("runtime", file("delite/runtime"), settings = deliteBuildSettings)

  lazy val forge = Project("forge", file("forge"), settings = forgeBuildSettings) dependsOn(lms) // additional settings are picked up in build.sbt of submodule

  // include all projects that should be built and tested in 'aggregate'
  lazy val tests = Project("tests", file("project/boot"), settings = deliteBuildSettings) aggregate(framework, deliteTest, dsls, apps)
}
