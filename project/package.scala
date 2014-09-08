package policy

import sbt._, Keys._, building._
import psp.libsbt._

sealed abstract class PolicyPackage extends Constants  with Runners with Bootstrap

package object building extends PolicyPackage {
  def bintraySettings     = bintray.Plugin.bintraySettings
  def mimaDefaultSettings = com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
  def MimaKeys            = com.typesafe.tools.mima.plugin.MimaKeys
  def previousArtifact    = MimaKeys.previousArtifact
  def binaryIssueFilters  = MimaKeys.binaryIssueFilters

  def chain[A](g: A => A)(f: A => A): A => A = f andThen g
  def doto[A](x: A)(f: A => Unit): A = { f(x) ; x }

  // All interesting implicits should be in one place.
  implicit def symToLocalProject(sym: scala.Symbol): LocalProject                 = LocalProject(sym.name)
  implicit def projectToProjectOps(p: Project): PolicyProjectOps                  = new PolicyProjectOps(p)
  implicit def inputTaskValueDiscarding[A](in: InputTaskOf[A]): InputTaskOf[Unit] = in map (_ => ())

  def chooseBootstrap = sysOrBuild(BootstrapModuleProperty).fold(scalaModuleId("compiler"))(moduleId)

  def scalaInstanceFromModuleIDTask: TaskOf[ScalaInstance] = Def task {
    def isLib(f: File)  = f.getName contains "-library"
    def isComp(f: File) = f.getName contains "-compiler"
    def sorter(f: File) = if (isLib(f)) 1 else if (isComp(f)) 2 else 3

    val report     = update.value configuration ScalaTool.name getOrElse sys.error("No update report")
    val modReports = report.modules.toList
    val pairs      = modReports flatMap (_.artifacts)
    val files      = (pairs map (_._2) sortBy sorter).toList ::: buildJars.value.toList
    def firstRevision = modReports.head.module.revision

    files match {
      case lib :: comp :: extras if isLib(lib) && isComp(comp) =>
        state.value.log.debug(s"scalaInstanceFromModuleIDTask:\n$report" + (lib :: comp :: extras).mkString("\n  ", "\n  ", "\n"))
        ScalaInstance(firstRevision, lib, comp, extras: _*)(state.value.classLoaderCache.apply)
      case _                                  =>
        val v = scalaVersion.value
        state.value.log.info(s"Couldn't find scala instance: $report\nWill try $v instead")
        ScalaInstance(v, appConfiguration.value.provider.scalaProvider.launcher getScala v)
    }
  }

  def sbtModuleId(name: String)   = SbtOrg    %          name          %  SbtKnownVersion
  def scalaModuleId(name: String) = ScalaOrg  % dash(ScalaName, name)  % ScalaKnownVersion
  def scalaLibrary  = scalaModuleId("library")
  def scalaCompiler = scalaModuleId("compiler")

  // The source dependency has a one-character change vs. asm-debug-all 5.0.3.
  // Not using at present in favor of a binary blob in ~/lib.
  // lazy val asm = RootProject(uri("git://github.com/paulp/asm.git#scala-fixes"))
  // def asm = "org.ow2.asm" % "asm-debug-all" % "5.0.3"
}
