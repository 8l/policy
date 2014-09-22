package policy
package building

import sbt._, Keys._, psp.libsbt._, psp.std.api._
import bintray.Plugin._

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
  def asmAttributed = asmJarSetting map (newCpElem(_, Artifact("asm"), asm, ScalaTool))

  // Assembled settings for projects which produce an artifact.
  def codeProject(others: Setting[_]*) = compiling ++ publishing ++ others

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
                  version :=  publishVersion,
             organization :=  PolicyOrg,
             scalaVersion :=  ScalaKnownVersion,
       scalaBinaryVersion :=  "2.11",
                 licenses :=  Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
         autoScalaLibrary :=  false,
               crossPaths :=  false,
     managedScalaInstance :=  false,
            sourcesInBase :=  false,
              logBuffered :=  false,
              showSuccess :=  false,
               showTiming :=  true,
               traceLevel :=  20,
        ivyConfigurations +=  ScalaTool,
                 key.jars ++= buildJars.value,
            scalaInstance <<= scalaInstance in ThisBuild,
    cancelable in compile :=  true,
          fork in compile :=  true
  )

  def compilerSettings = codeProject(
           key.sourceDirs <++= allInSrc("compiler reflect repl"),
       key.testSourceDirs <+=  fromBase("partest/src"),
    unmanagedBase in Test <<=  fromBase("partest/testlib"),
             fork in Test  :=  true,
                     test <<=  runAllTests,
                 testOnly <<=  runTests
  )

  def librarySettings = codeProject(
        key.mainSource <<=  inSrc(Library),
        key.sourceDirs <++= allInSrc("forkjoin library"),
        key.sourceDirs <+=  fromBase("policy/src/main/scala"),
       // key.mainOptions ++=  Seq("-sourcepath", key.mainSource.value.getPath),
      previousArtifact  :=  Some(scalaLibrary)
  )

  def rootSettings = List(
                                 name :=  PolicyName,
                  PolicyKeys.getScala <<= scalaInstanceTask,
                      PolicyKeys.repl <<= asInputTask(forkRepl),
                                  run <<= asInputTask(forkCompiler),
           initialCommands in console +=  "\nimport scala.reflect.runtime.universe._, psp.std._",
    initialCommands in consoleProject +=  "\nimport policy.building._",
                         watchSources ++= sbtFilesInBuild.value ++ sourceFilesInProject.value,
         PolicyKeys.bootstrapModuleId :=  chooseBootstrap,
                  libraryDependencies <+= PolicyKeys.bootstrapModuleId mapValue (m => m.exceptScala % ScalaTool.name),
           scalaInstance in ThisBuild <<= scalaInstanceFromModuleIDTask
          // exportedProducts in Compile <<= exportedProducts in Compile dependsOn generateClasspath
  )
  def publishing = List(
      //                checksums in publishLocal := Nil,
      // publishArtifact in (Compile, packageDoc) := false,
      // publishArtifact in (Compile, packageSrc) := false,
                     publishLocalConfiguration ~= (p => Classpaths.publishConfig(p.artifacts, p.ivyFile, p.checksums, p.resolverName, logging = UpdateLogging.Quiet, overwrite = false)),
                           updateConfiguration ~= (uc => new UpdateConfiguration(uc.retrieve, uc.missingOk, logging = UpdateLogging.Quiet))
  )
  def compiling = List(
           resourceGenerators in Compile <+= generateProperties,
      javacOptions in (Compile, compile) ++= stdJavacArgs,
     scalacOptions in (Compile, compile) ++= stdScalacArgs,
                              incOptions :=  stdIncOptions
  )

  private def testJavaOptions                = partestProperties map ("-Xmx1g" +: _.commandLineArgs)
  private def compilePath: TaskOf[Seq[File]] = dependencyClasspath in Compile map (_.files filter isJar)
  private def explode(f: File, d: File)      = IO.unzip(f, d, isSourceName _).toSeq

  def scalaInstanceFromModuleIDTask: TaskOf[ScalaInstance] = Def task {
    def isLib(f: File)  = f.getName contains "bootstrap-library"
    def isComp(f: File) = f.getName contains "bootstrap-compiler"
    def sorter(f: File) = if (isLib(f)) 1 else if (isComp(f)) 2 else 3

    val report     = (update.value configuration ScalaTool.name) | sys.error("No update report")
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
      "bootstrap.moduleid"          -> PolicyKeys.bootstrapModuleId.value.toString
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
