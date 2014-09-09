package policy
package building

import sbt._, Keys._
import psp.libsbt._, Deps._
import psp.std.api._
import scala.sys.process.Process

object PolicyKeys {
  val repl              = inputKey[Unit]("run policy repl")
  val getScala          = inputKey[ScalaInstance]("download scala version, if not in ivy cache")
  val bootstrapModuleId = settingKey[ModuleID]("module id of bootstrap compiler") in ThisBuild
  val jarPaths          = inputKey[Classpath]("jars in given configuration")
}

object PolicyBuild extends sbt.Build {
  def isRepoClean     = Process("git diff --quiet --exit-code HEAD").! == 0
  def repoSha: String = Process("git rev-parse HEAD").!! take 10
  def localSuffix     = "-%s-%s%s".format(dateTime, repoSha, if (isRepoClean) "" else "-dirty")

  private implicit class PolicyProjectOps(val p: Project) {
    def addMima(m: ModuleID) = p also fullMimaSettings(m)
    def fullMimaSettings(m: ModuleID) = mimaDefaultSettings ++ Seq(
      binaryIssueFilters ++= MimaPolicy.filters,
                    test <<= MimaKeys.reportBinaryIssues,
        previousArtifact :=  Some(m)
    )
    def setup = p also projectSettings(p.id)
  }

  lazy val root = (
    project.root.setup.alsoToolsJar
      dependsOn ( library, compilerProject )
      aggregate ( library, compilerProject, compat )
      also policyCommands
  )
  lazy val library = project.setup addMima scalaLibrary

  // The source dependency has a one-character change vs. asm-debug-all 5.0.3.
  // Not using at present in favor of a binary blob in ~/lib.
  // lazy val asm = RootProject(uri("git://github.com/paulp/asm.git#scala-fixes"))
  // def asm = "org.ow2.asm" % "asm-debug-all" % "5.0.3"
  lazy val compilerProject = (
    Project("compiler", file("compiler")).setup
      dependsOn library
      deps (jline, testInterface, (diffutils % "test").intransitive)
  )

  lazy val compat = (
    project.setup.noArtifacts
      dependsOn ( compilerProject )
      sbtDeps ( "interface", "compiler-interface" )
  )

  def policyCommands = commands ++= Seq(
    cmd.effectful("dump")(_.dumpSettings mkString "\n"),
    cmd.effectful("diff", "<cmd>")(ShowDiff.diffSettings),
    cmd.effectful("jarsIn", "<config>")((s, c) => (s classpathIn c.toLowerCase).files map s.relativize mkString "\n")
  )
}
