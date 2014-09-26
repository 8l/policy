package policy
package building

import sbt._, Keys._, psp.libsbt._, psp.std.api._
import bintray.Plugin._, PolicyKeys._

object projectSetup {
  final val Root     = "root"
  final val Compiler = "compiler"
  final val Library  = "library"
  final val Compat   = "compat"

  def apply(p: Project): Project = p also universal also {
    p.id match {
      case Root     => rootSettings :+ policyCommands
      case Compiler => compilerSettings
      case Library  => librarySettings ++ fullMimaSettings(scalaLibrary)
      case Compat   => Seq(key.generators <+= createUnzipTask)
    }
  }

  // Boilerplate to get the prebuilt asm jar attached to the compiler metadata.
  val asmJarKey     = taskKey[File]("asm jar")
  def asm           = PolicyOrg % "asm" % asmVersion
  def asmVersion    = "5.0.4-SNAPSHOT"
  def asmJarSetting = fromBase(s"lib/asm-$asmVersion.jar")
  def asmSettings   = Seq(asmJarKey <<= asmJarSetting.task) ++ addArtifact(Artifact("asm"), asmJarKey).settings
  // def asmAttributed = asmJarSetting map (newCpElem(_, Artifact("asm"), asm, ScalaTool))

  def fullMimaSettings(m: ModuleID) = mimaDefaultSettings ++ Seq(
    binaryIssueFilters ++= MimaPolicy.filters,
                  test <<= reportBinaryIssues,
      previousArtifact :=  Some(m)
  )

  def policyCommands = commands ++= Seq(
    cmd.effectful("dump")(_.dumpSettings mkString "\n"),
    cmd.effectful("diff", "<cmd>")(ShowDiff.diffSettings),
    cmd.effectful("jarsIn", "<config>")((s, c) => (s classpathIn c.toLowerCase).files map s.relativize mkString "\n")
  )

  // Settings added to every project.
  def universal = bintraySettings ++ standardSettings ++ List(
                                    name ~=  (dash(PolicyName, _)),
                            organization :=  PolicyOrg,
                                 version :=  sbtBuildProps.buildVersion,
                            scalaVersion :=  scalaVersionLatest,
                      scalaBinaryVersion :=  "2.11",
                        autoScalaLibrary :=  false,
                              crossPaths :=  false,
     scalacOptions in (Compile, compile) ++= stdScalacArgs ++ Seq("-Ylog-classpath"),
           resourceGenerators in Compile <+= generateProperties,
                                key.jars ++= buildJars.value,
                                licenses :=  Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
                             logBuffered :=  false,
                             showSuccess :=  false,
                              showTiming :=  true,
                           sourcesInBase :=  false,
                              traceLevel :=  20
  )

  def compilerSettings = Seq(
             key.sourceDirs <++= allInSrc("compiler reflect repl"),
         key.testSourceDirs <+=  fromBase("partest/src"),
      unmanagedBase in Test <<=  fromBase("partest/testlib")
  )
  def librarySettings = Seq(
        key.mainSource <<=  inSrc(Library),
        key.sourceDirs <++= allInSrc("forkjoin library"),
        key.sourceDirs <+=  fromBase("policy/src/main/scala"),
      previousArtifact  :=  Some(scalaLibrary),
       key.mainOptions ++=  Seq("-sourcepath", key.mainSource.value.getPath)
  )

  def rootSettings = List(
                                 name :=  PolicyName,
                          bootstrapId :=  chooseBootstrap,
                                 repl <<= asInputTask(forkRepl),
                                  run <<= asInputTask(forkCompiler),
                         fork in Test :=  true,
                                 test <<= runAllTests,
                             testOnly <<= runTests,
           initialCommands in console +=  "\nimport scala.reflect.runtime.universe._, psp.std._",
    initialCommands in consoleProject +=  "\nimport policy.building._",
                         watchSources ++= sbtFilesInBuild.value ++ sourceFilesInProject.value
  )

  private def testJavaOptions                = partestProperties map ("-Xmx1g" +: _.commandLineArgs)
  private def compilePath: TaskOf[Seq[File]] = dependencyClasspath in Compile map (_.files filter isJar)
  private def explode(f: File, d: File)      = IO.unzip(f, d, isSourceName _).toSeq

  def createUnzipTask: TaskOf[Seq[File]] = Def task (compilePath.value flatMap (f => explode(f, sourceManaged.value / "compat")))

  def propertiesFile = Def setting (
    (resourceManaged in Compile).value / "%s.properties".format(name.value split '-' last)
  )

  def generateProperties: TaskOf[Seq[File]] = Def task {
    val file = propertiesFile.value
    val props = MutableProperties(file)
    def contents = Seq(
      "version.number"              -> version.value,
      "scala.version.number"        -> scalaVersion.value,
      "scala.binary.version.number" -> scalaBinaryVersion.value,
      "policy.bootstrap"            -> bootstrapId.value.toString
    )
    for ((k, v) <- contents) props(k) = v
    props.save()
    Seq(file)
  }

  def runTestsWithArgs(args: List[String]): TaskOf[Int] = forkPartest map (_ apply (args: _*))
  def runAllTests: TaskOf[Unit] = forkPartest map (_ apply "--all")
  def runTests: InputTaskOf[Unit] = Def inputTask {
    spaceDelimited("<arg>").parsed match {
      case Nil  => forkPartest.value("--failed", "--show-diff") // testOnly with no args we'll take to mean --failed
      case args => forkPartest.value(args: _*)
    }
  }
}
