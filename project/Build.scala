import sbt._
import Keys._
import java.io.File

import forge.preprocessor._

object HyperDSLBuild extends Build with ForgePreprocessor {

  System.setProperty("showSuppressedErrors", "false")

  val virtScala = Option(System.getenv("SCALA_VIRTUALIZED_VERSION")).getOrElse("2.10.2")
  val scalaTest = "org.scalatest" % "scalatest_2.10" % "2.1.2"
  val virtBuildSettingsBase = Defaults.defaultSettings ++ Seq(
    organization := "stanford-ppl",
    scalaOrganization := "org.scala-lang.virtualized",
    scalaVersion := virtScala,
    publishArtifact in (Compile, packageDoc) := false,
    libraryDependencies += "org.scala-lang.virtualized" % "scala-library" % virtScala,
    libraryDependencies += "org.scala-lang.virtualized" % "scala-compiler" % virtScala,
    libraryDependencies += scalaTest,

    libraryDependencies += "org.apache.commons" % "commons-math" % "2.2",
    libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.4.1",
    libraryDependencies += "org.apache.mesos" % "mesos" % "0.9.0-incubating",
    libraryDependencies += "org.apache.hadoop" % "hadoop-core" % "1.2.0",

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

  lazy val forge = Project("forge", file("forge"), settings = forgeBuildSettings) dependsOn(lms) // additional settings are picked up in build.sbt of submodule

  // include all projects that should be built (dependsOn) and tested (aggregate)
  lazy val tests = (Project("tests", file("project/boot"), settings = deliteBuildSettings)
    dependsOn(optimlApps, optiqlApps, optigraphApps, interopApps) aggregate(framework, deliteTest, optiml, optiql, optigraph))
  // lazy val tests = Project("tests", file("project/boot"), settings = deliteBuildSettings) aggregate(lms)
}
