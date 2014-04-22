
import sbt._
import Keys._

object HUMAN_DSL_NAMEBuild extends Build {
  val virtScala = "2.10.2"

  val virtBuildSettingsBase = Project.defaultSettings ++ Seq(
    organization := "stanford-ppl",
    scalaOrganization := "org.scala-lang.virtualized",
    scalaVersion := virtScala,
    publishArtifact in (Compile, packageDoc) := false,
    libraryDependencies += "org.scala-lang.virtualized" % "scala-library" % virtScala,
    libraryDependencies += "org.scala-lang.virtualized" % "scala-compiler" % virtScala,
    libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.1.2",
    
    libraryDependencies += "org.apache.commons" % "commons-math" % "2.2",
    resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    libraryDependencies += "com.googlecode.netlib-java" % "netlib-java" % "0.9.3",
    libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.4.1",
    libraryDependencies += "org.apache.mesos" % "mesos" % "0.9.0-incubating",
    libraryDependencies += "org.apache.hadoop" % "hadoop-core" % "1.2.0",
    // used in delitec to access jars
    retrieveManaged := true,
    scalacOptions += "-Yno-generic-signatures",
    scalacOptions += "-Yvirtualize",
    initialCommands in console += "import LOWERCASE_DSL_NAME.library._; val HUMAN_DSL_NAME = new HUMAN_DSL_NAMEREPL { def main() = {} }; import HUMAN_DSL_NAME._"
  )

  val virtBuildSettings = virtBuildSettingsBase ++ Seq(
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    scalaSource in Test <<= baseDirectory(_ / "test-src"),
    parallelExecution in Test := false,
    concurrentRestrictions in Test += Tags.limitAll(1) // don't run anything in parallel
  )

  // build targets
  lazy val HUMAN_DSL_NAME = Project("HUMAN_DSL_NAME", file("."), settings = virtBuildSettings)
}
