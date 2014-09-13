package policy
package building

import sbt._, Keys._
import psp.libsbt._, Deps._
import psp.std.api._
import scala.sys.process.Process

object PolicyKeys {
  lazy val repl              = inputKey[Unit]("run policy repl")
  lazy val getScala          = inputKey[ScalaInstance]("download scala version, if not in ivy cache")
  lazy val bootstrapModuleId = settingKey[ModuleID]("module id of bootstrap compiler") in ThisBuild
  lazy val jarPaths          = inputKey[Classpath]("jars in given configuration")
}

object PolicyBuild extends sbt.Build with LibSbt {
  def mimaDefaultSettings = com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
  def binaryIssueFilters  = com.typesafe.tools.mima.plugin.MimaKeys.binaryIssueFilters
  def previousArtifact    = com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact
  def reportBinaryIssues  = com.typesafe.tools.mima.plugin.MimaKeys.reportBinaryIssues

  // def isRepoClean     = Process("git diff --quiet --exit-code HEAD").! == 0
  // def repoSha: String = Process("git rev-parse HEAD").!! take 10
  // def localSuffix     = "-%s-%s%s".format(dateTime, repoSha, if (isRepoClean) "" else "-dirty")

  private implicit class PolicyProjectOps(val p: Project) {
    def root = p in file(".")
    def addMima(m: ModuleID) = p settings (fullMimaSettings(m): _*)
    def fullMimaSettings(m: ModuleID) = mimaDefaultSettings ++ Seq(
      binaryIssueFilters ++= MimaPolicy.filters,
                    test <<= reportBinaryIssues,
        previousArtifact :=  Some(m)
    )
    def setup = p settings (projectSettings(p.id): _*) settings (version := publishVersion)
  }

  lazy val root = (
    project.root.setup.alsoToolsJar
      dependsOn ( library, compilerProject )
      aggregate ( library, compilerProject, compat )
      settings  ( policyCommands: _* )
  )
  lazy val library = project.setup addMima scalaLibrary

  // The source dependency has a one-character change vs. asm-debug-all 5.0.3.
  // Not using at present in favor of a binary blob in ~/lib.
  // lazy val asm = RootProject(uri("git://github.com/paulp/asm.git#scala-fixes"))
  // def asm = "org.ow2.asm" % "asm-debug-all" % "5.0.3"
  lazy val compilerProject = (
    Project("compiler", file("compiler")).setup
      dependsOn library
      settings (libraryDependencies ++= Seq(jline, testInterface, (diffutils % "test").intransitive))
  )

  lazy val compat = (
    project.setup.noArtifacts
      dependsOn ( compilerProject )
      settings  (libraryDependencies ++= Seq("org.scala-sbt" % "interface" % sbtVersion.value, "org.scala-sbt" % "compiler-interface" % sbtVersion.value))
  )

  def policyCommands = commands ++= Seq(
    cmd.effectful("dump")(_.dumpSettings mkString "\n"),
    cmd.effectful("diff", "<cmd>")(ShowDiff.diffSettings),
    cmd.effectful("jarsIn", "<config>")((s, c) => (s classpathIn c.toLowerCase).files map s.relativize mkString "\n")
  )
}
