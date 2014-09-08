package policy
package building

import sbt._, Keys._
import psp.libsbt._

trait Constants {
  lazy val buildProps = MutableProperties(file("project/build.properties"))
  lazy val localProps = MutableProperties(file("project/local.properties"))

  def sysOrBuild(name: String): Option[String] = (
    (sys.props get name) orElse (localProps get name) orElse (buildProps get name)
  )

  def NoTraceSuppression      = scala.sys.SystemProperties.noTraceSupression.key
  def pathSeparator           = java.io.File.pathSeparator
  def separator               = java.io.File.separator

  def SbtKnownVersion         = sysOrBuild("sbt.version") | "0.13.5"
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
}
