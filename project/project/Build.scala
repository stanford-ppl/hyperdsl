import sbt._
object PluginDef extends Build {
  lazy val root = Project("plugins", file(".")) dependsOn(preprocessor)

  lazy val FORGE_HOME = sys.env.get("FORGE_HOME").getOrElse(error("Please set the FORGE_HOME environment variable"))
  lazy val preprocessor = file(FORGE_HOME + "/preprocessor/")
}
