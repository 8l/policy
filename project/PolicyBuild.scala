package policy
package building

import sbt._, Keys._
import psp.libsbt._, Deps._, psp.std._

object PolicyKeys {
  lazy val repl              = inputKey[Unit]("run policy repl")
  lazy val getScala          = inputKey[ScalaInstance]("download scala version, if not in ivy cache")
  lazy val bootstrapModuleId = settingKey[ModuleID]("module id of bootstrap compiler") in ThisBuild
  lazy val jarPaths          = inputKey[Classpath]("jars in given configuration")
}

object PolicyBuild extends sbt.Build with LibSbt {
  // We'd rather have a binary depenedency on asm,
  // but scala has added hacks which aren't in the mainline.
  // At present we build against a binary blob in ~/lib.
  //   https://github.com/paulp/asm
  //   "org.ow2.asm" % "asm-debug-all" % "5.0.3"
  def compilerDeps = Seq(jline, testInterface, diffutils % "test" intransitive)
  def libraryDeps  = Seq(pspOrg %% "psp-std" % "0.4.5")
  def compatDeps   = Def setting Seq(
    "org.scala-sbt" % "interface"          % sbtVersion.value,
    "org.scala-sbt" % "compiler-interface" % sbtVersion.value
  )
  private def inProjects[A](f: String => Seq[A]): Seq[A] = List("library", "compiler") flatMap f

  def bootstrapCommand = Command.args("bootstrap", "<cmds>") { (state, args) =>
    val ss = inProjects(p => Seq(name in LocalProject(p) := s"bootstrap-$p", version in LocalProject(p) := publishVersion))
    val cs = args flatMap (cmd => inProjects[String](p => Seq(s"$p/$cmd")))
    state set (ss: _*) run (cs: _*)
  }

  lazy val root     = projectSetup(project).root.alsoToolsJar dependsOn (library, compiler) aggregate (library, compiler, compat) also (commands += bootstrapCommand)
  lazy val library  = projectSetup(project) also (libraryDependencies ++= libraryDeps)
  lazy val compiler = projectSetup(project) dependsOn library also (libraryDependencies ++= compilerDeps)
  lazy val compat   = projectSetup(project).noArtifacts dependsOn compiler settings (libraryDependencies <++= compatDeps)
}
