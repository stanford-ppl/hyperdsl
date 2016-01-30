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
    //scalaOrganization := "org.scala-lang.virtualized",
    scalaVersion := virtScala,
    publishArtifact in (Compile, packageDoc) := false,
    libraryDependencies += "org.scala-lang" % "scala-library" % virtScala,
    libraryDependencies += "org.scala-lang" % "scala-compiler" % virtScala,
    libraryDependencies += scalaTest,

    libraryDependencies += "org.apache.commons" % "commons-math" % "2.2",
    libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.5.0",
    libraryDependencies += "org.apache.mesos" % "mesos" % "0.20.1",
    libraryDependencies += "org.apache.hadoop" % "hadoop-common" % "2.5.1",
    libraryDependencies += "org.apache.hadoop" % "hadoop-client" % "2.5.1",
    libraryDependencies += "org.apache.hadoop" % "hadoop-hdfs" % "2.5.1",
    libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.8.7",
    libraryDependencies += "junit" % "junit" % "4.11" % "test",


    retrieveManaged := true,
    scalacOptions += "-Yno-generic-signatures",
    //scalacOptions += "-Yvirtualize",

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
  lazy val hyperdsl = Project("hyperdsl", file("."), settings = deliteBuildSettings) aggregate(virtualization, lms, framework, runtime, deliteTest, forge) //, yinyang, yy_paradise)

  // additional settings are picked up in build.sbt of submodule

  lazy val virtualization = Project("virtualization", file("summer-of-lms-2014"))

  lazy val lms = Project("lms", file("virtualization-lms-core")) dependsOn(virtualization)

  lazy val runtime = Project("runtime", file("delite/runtime"), settings = deliteBuildSettings)
  lazy val framework = Project("framework", file("delite/framework"), settings = deliteBuildSettings) dependsOn(virtualization , runtime, lms, yinyang, example_dsls, records_core) // dependency on runtime because of Scopes?
  
  lazy val deliteTest = Project("delite-test", file("delite/framework/delite-test"), settings = deliteBuildSettings) dependsOn(framework, runtime)

  lazy val dsls = Project("dsls", file("delite/dsls"), settings = deliteBuildSettings) aggregate(optiql)
  lazy val optiql = Project("optiql", file("delite/dsls/optiql"), settings = deliteBuildSettings) dependsOn(framework, deliteTest, example_dsls) //make YY macro available

  lazy val apps = Project("apps", file("delite/apps"), settings = deliteBuildSettings) aggregate(optiqlApps)
  lazy val optiqlApps = Project("optiql-apps", file("delite/apps/optiql"), settings = deliteBuildSettings) dependsOn(optiql)

  lazy val forge = Project("forge", file("forge"), settings = forgeBuildSettings) dependsOn(lms) // additional settings are picked up in build.sbt of submodule

  // include all projects that should be built (dependsOn) and tested (aggregate)
  lazy val tests = Project("tests", file("project/boot"), settings = deliteBuildSettings) aggregate(framework, deliteTest, dsls, apps)

  //YinYang:

  // modules
  lazy val yy_root       = Project(id = "yinyng-root",      base = file("yinyang-vojin")                     , settings = Project.defaultSettings ++ Seq(publishArtifact := false)) aggregate (yinyang, yy_core, yy_paradise, example_dsls)
  lazy val yy_core       = Project(id = "yinyang-core",     base = file("yinyang-vojin/components/core")     , settings = defaults ++ Seq(name := "yinyang-core"))
  lazy val yy_paradise   = Project(id = "yinyang-paradise", base = file("yinyang-vojin/components/paradise") , settings = defaults ++ paradise ++ Seq(name := "yinyang-paradise")) dependsOn(yy_core)
  lazy val yinyang       = Project(id = "scala-yinyang",    base = file("yinyang-vojin/components/yin-yang") , settings = defaults ++ Seq(name := "scala-yinyang")) dependsOn(yy_core)
  lazy val example_dsls  = Project(id = "example-dsls",     base = file("yinyang-vojin/components/dsls")     , settings = defaults ++ Seq(publishArtifact := false)) dependsOn(yinyang, lms)
  lazy val yy_lms        = Project(id = "yinyang-lms",      base = file("yinyang-vojin/components/yy-lms")   , settings = defaults ++ Seq(name := "yinyang-lms")) dependsOn(yinyang, lms)
  //lazy val delite        = Project(id = "delite-test",      base = file("components/delite-test"),settings = defaults ++ paradise ++ Seq(name := "delite-test")) dependsOn(yinyang) //, yy_core, yy_paradise, example_dsls)

  lazy val defaults =
      projectSettings ++
      virtBuildSettingsBase ++ //scalaSettings ++
      //formatSettings ++
      //libraryDeps ++ //all included in virtBuildSettingsBase
      Seq(
    resolvers +=  "OSSH" at "https://oss.sonatype.org/content/groups/public",
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
    // paths - so we don't need to have src/main/scala ... just src/ test/ and resources/
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    scalaSource in Test <<= baseDirectory(_ / "test"),
    resourceDirectory in Compile <<= baseDirectory(_ / "resources"),
    // sbteclipse needs some info on source directories:
    unmanagedSourceDirectories in Compile <<= (scalaSource in Compile)(Seq(_)),
    unmanagedSourceDirectories in Test <<= (scalaSource in Test)(Seq(_)),
    parallelExecution in Test := false,
    incOptions := incOptions.value.withNameHashing(true)
  )

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

  lazy val projectSettings = Seq[Setting[_]](
    version              := "0.2.0-SNAPSHOT",
    organization         := "ch.epfl.lamp",
    licenses             := Seq("New BSD" ->
      url("https://raw.githubusercontent.com/scala-yinyang/scala-yinyang/master/LICENCE")),
    homepage             := Some(url("https://github.com/scala-yinyang/scala-yinyang")),
    organizationHomepage := Some(url("http://lamp.epfl.ch")),
    scmInfo              := Some(ScmInfo(
      url("https://github.com/scala-yinyang/scala-yinyang.git"),
      "scm:git:git://github.com/scala-yinyang/scala-yinyang.git"))
  )

  //Records:

  val buildSettings = virtBuildSettingsBase
  val sharedCoreSettings = virtBuildSettingsBase
  val macroBuildSettings = virtBuildSettingsBase //deliteBuildSettings?

  lazy val records_root = project.in(file("scala-records"))
    .aggregate(synthPlugin, records_core, records_tests)

  lazy val synthPlugin = project.in(file("scala-records/synthPlugin"))
    .settings(buildSettings: _*)
    .settings(
      exportJars := true,
      crossVersion := CrossVersion.full,
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        "org.scala-lang" % "scala-reflect" % scalaVersion.value)
    )

  lazy val records_core = project.in(file("scala-records/core"))
    .settings(sharedCoreSettings: _*)
    .dependsOn(synthPlugin % "plugin")


  lazy val records_tests = project.in(file("scala-records/tests"))
    .settings(macroBuildSettings: _*)
    .settings(
      name := "scala-records-tests",
      unmanagedSourceDirectories in Test ++= {
//        if (scalaVersion.value >= "2.11")
          Seq(sourceDirectory.value / "test-2.11" / "scala")
//        else
//          Seq(sourceDirectory.value / "test-2.10" / "scala")
      }
    )
    .dependsOn(records_core)
}
