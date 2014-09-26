package policy
package building

import sbt._, Keys._
import psp.libsbt._, Deps._, psp.std._

object PolicyKeys {
  lazy val bootstrapId = settingKey[ModuleID]("module id of bootstrap compiler") in ThisBuild
  lazy val repl        = inputKey[Unit]("run policy repl")
  lazy val jars        = inputKey[Classpath]("jars in given configuration")
}

object PolicyBuild extends sbt.Build with LibSbt {
  // We'd rather have a binary depenedency on asm,
  // but scala has added hacks which aren't in the mainline.
  // At present we build against a binary blob in ~/lib.
  //   https://github.com/paulp/asm
  //   "org.ow2.asm" % "asm-debug-all" % "5.0.3"
  def compilerDeps = Seq(jline, testInterface, diffutils % "test" intransitive).map(_.exceptScala)
  def libraryDeps  = Seq(pspStd.exceptScala)
  def compatDeps   = Def setting Seq(
    "org.scala-sbt" % "interface"          % sbtVersion.value,
    "org.scala-sbt" % "compiler-interface" % sbtVersion.value
  ).map(_.exceptScala)
  private def inProjects[A](f: String => Seq[A]): Seq[A] = List("library", "compiler") flatMap f

  def bootstrapCommand = Command.args("bootstrap", "<cmds>") { (state, args) =>
    val ss = inProjects(p => Seq(name in LocalProject(p) := s"bootstrap-$p", version in LocalProject(p) := sbtBuildProps.buildVersion))
    val cs = args flatMap (cmd => inProjects[String](p => Seq(s"$p/$cmd")))
    state set (ss: _*) run (cs: _*)
  }

  def scalaInstanceBuilt: TaskOf[ScalaInstance] = Def task {
    val rev    = sbtBuildProps.buildVersion
    val lib    = (classDirectory in Compile in library).value
    val comp   = (classDirectory in Compile in compiler).value
    val extras = buildJars.value.toList ++ state.value.download(jline).jarFiles
    val jars   = lib :: comp :: extras map (_.toURI.toURL) toArray
    val loader = new URLClassLoader(jars, null)

    printResult("Created ScalaInstance from built jars")(
      new ScalaInstance(
        version        = rev,
        loader         = loader,
        libraryJar     = lib,
        compilerJar    = comp,
        extraJars      = extras,
        explicitActual = Some(rev)
      )
    )
  }

  lazy val root = (
    projectSetup(project).root.alsoToolsJar
      dependsOn (library, compiler)
      aggregate (library, compiler, compat) also (
                    commands +=  bootstrapCommand,
        managedScalaInstance :=  false,
               scalaInstance <<= scalaInstanceBuilt
      )
  )
  lazy val library  = projectSetup(project) also libraryDeps.deps
  lazy val compiler = projectSetup(project) dependsOn library also compilerDeps.deps
  lazy val compat   = projectSetup(project).noArtifacts dependsOn compiler settings (libraryDependencies <++= compatDeps)
}
