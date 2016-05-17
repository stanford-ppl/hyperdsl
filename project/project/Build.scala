import sbt._
object PluginDef extends Build {
  lazy val root = Project("plugins", file(".")) dependsOn(preprocessor)

  lazy val FORGE_HOME = "../forge"
  lazy val preprocessor = file(FORGE_HOME + "/preprocessor/")
}
