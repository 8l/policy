package policy

import sbt._, Keys._, psp.libsbt._

package object building extends policy.building.PolicyPackage

package building {
  sealed abstract class PolicyPackage extends Runners {
    lazy val buildProps = MutableProperties(file("project/build.properties"))
    lazy val localProps = MutableProperties(file("project/local.properties"))

    def mimaDefaultSettings = com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
    def binaryIssueFilters  = com.typesafe.tools.mima.plugin.MimaKeys.binaryIssueFilters
    def previousArtifact    = com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact
    def reportBinaryIssues  = com.typesafe.tools.mima.plugin.MimaKeys.reportBinaryIssues


    def sysOrBuild(name: String): Option[String] = (
      (sys.props get name) orElse (localProps get name) orElse (buildProps get name)
    )

    def NoTraceSuppression      = scala.sys.SystemProperties.noTraceSupression.key
    def pathSeparator           = java.io.File.pathSeparator
    def separator               = java.io.File.separator

    def SbtKnownVersion         = sysOrBuild("sbt.version") | "0.13.6"
    def ScalaKnownVersion       = sysOrBuild("scala.version") | "2.11.2"
    def BootstrapModuleProperty = "bootstrap.module"
    def PartestRunnerClass      = "scala.tools.partest.nest.ConsoleRunner"
    def ReplRunnerClass         = "scala.tools.nsc.MainGenericRunner"
    def CompilerRunnerClass     = "scala.tools.nsc.Main"
    def PolicyOrg               = "org.improving"
    def ScalaOrg                = "org.scala-lang"
    def SbtOrg                  = "org.scala-sbt"
    def PolicyName              = "policy"
    def ScalaName               = "scala"

    def stdScalacArgs  = Nil //wordSeq("-Ywarn-unused -Ywarn-unused-import -Xdev")
    def stdPartestArgs = wordSeq("-deprecation -unchecked -Xlint")
    def stdJavacArgs   = wordSeq("-nowarn -XDignore.symbol.file")

    implicit def symToLocalProject(sym: scala.Symbol): LocalProject                 = LocalProject(sym.name)
    implicit def inputTaskValueDiscarding[A](in: InputTaskOf[A]): InputTaskOf[Unit] = in map (_ => ())

    def chooseBootstrap                         = sysOrBuild(BootstrapModuleProperty).fold(scalaCompiler)(Deps.moduleId)
    def scalaArtifactId(name: String): ModuleID = ScalaOrg % dash(ScalaName, name) % ScalaKnownVersion
    def scalaLibrary                            = scalaArtifactId("library")
    def scalaCompiler                           = scalaArtifactId("compiler")
  }
}
