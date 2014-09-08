package policy
package building

import sbt._, Keys._, psp.libsbt._

final class PolicyProjectOps(val p: Project) {
  def addMima(m: ModuleID) = p also fullMimaSettings(m)

  def fullMimaSettings(m: ModuleID) = mimaDefaultSettings ++ Seq(
    binaryIssueFilters ++= MimaPolicy.filters,
                  test <<= MimaKeys.reportBinaryIssues,
      previousArtifact :=  Some(m)
  )

  def rootSetup                           = (p in file(".")).setup.noArtifacts.alsoToolsJar
  def setup                               = p also projectSettings(p.id)
  def deps(ms: ModuleID*)                 = p settings (libraryDependencies ++= ms.toSeq)
  def intransitiveDeps(ms: ModuleID*)     = deps(ms map (_.intransitive()): _*)
  def intransitiveTestDeps(ms: ModuleID*) = deps(ms map (m => (m % "test").intransitive): _*)
  def sbtDeps(ids: String*)               = intransitiveDeps(ids map sbtModuleId: _*)
  def scalaDeps(ids: String*)             = intransitiveDeps(ids map scalaModuleId: _*)
}

private object projectSettings {
  final val Root     = "root"
  final val Compiler = "compiler"
  final val Library  = "library"
  final val Compat   = "compat"

  def apply(id: String): SettingSeq = universal ++ (id match {
    case Root     => root
    // case Repl     => repl
    case Compat   => compat
    case Compiler => compiler
    case Library  => library
  })

  // Boilerplate to get the prebuilt asm jar attached to the compiler metadata.
  val asmJarKey     = taskKey[File]("asm jar")
  def asm           = PolicyOrg % "asm" % asmVersion
  def asmVersion    = "5.0.4-SNAPSHOT"
  def asmJarSetting = fromBase(s"lib/asm-$asmVersion.jar")
  def asmSettings   = Seq(asmJarKey <<= asmJarSetting.task) ++ addArtifact(Artifact("asm"), asmJarKey).settings
  def asmAttributed = asmJarSetting map (newCpElem(_, Artifact("asm"), asm, ScalaTool))

  // Assembled settings for projects which produce an artifact.
  def codeProject(others: Setting[_]*) = compiling ++ publishing ++ others

  // Settings added to every project.
  def universal = bintraySettings ++ List(
                        name ~=  (dash(PolicyName, _)),
                organization :=  PolicyOrg,
                     version :=  "1.0.0-M6",
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
                   resolvers +=  paulp.maven,
                    key.jars ++= buildJars.value,
               scalaInstance <<= scalaInstance in ThisBuild,
       cancelable in compile :=  true,
             fork in compile :=  true
  )

  def compiler = codeProject(
           mainSourceDirs <++= allInSrc("compiler reflect repl"),
             mainTestDirs <+=  fromBase("partest/src"),
    unmanagedBase in Test <<=  fromBase("partest/testlib"),
             fork in Test  :=  true,
                     test <<=  runAllTests,
                 testOnly <<=  runTests
  )

  def compat   = List(sourceGenerators in Compile <+= createUnzipTask)

  def library = codeProject(
            mainSource <<=  inSrc(Library),
        mainSourceDirs <++= allInSrc("forkjoin library"),
           mainOptions ++=  Seq("-sourcepath", mainSource.value.getPath),
      previousArtifact  :=  Some(scalaLibrary)
  )

  private def replJar  = artifactPath in (Compile, packageBin) in 'repl mapValue Attributed.blank

  def root = List(
                                 name :=  PolicyName,
                  PolicyKeys.getScala <<= scalaInstanceTask,
                      PolicyKeys.repl <<= asInputTask(forkRepl),
                                  run <<= asInputTask(forkCompiler),
           initialCommands in console :=  "import scala.reflect.runtime.universe._",
    initialCommands in consoleProject :=  "import policy.building._",
                         watchSources ++= sbtFilesInBuild.value ++ sourceFilesInProject.value,
         PolicyKeys.bootstrapModuleId :=  chooseBootstrap,
                  libraryDependencies <+= PolicyKeys.bootstrapModuleId mapValue (_ % ScalaTool.name),
           scalaInstance in ThisBuild <<= scalaInstanceFromModuleIDTask,
                             commands ++= bootstrapCommands
  )
  def publishing = List(
                     checksums in publishLocal := Nil,
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in (Compile, packageSrc) := false,
                     publishLocalConfiguration ~= (p => Classpaths.publishConfig(p.artifacts, p.ivyFile, p.checksums, p.resolverName, logging = UpdateLogging.Quiet, overwrite = false)),
                           updateConfiguration ~= (uc => new UpdateConfiguration(uc.retrieve, uc.missingOk, logging = UpdateLogging.Quiet))
  )
  def compiling = List(
           resourceGenerators in Compile <+= generateProperties(),
      javacOptions in (Compile, compile) ++= stdJavacArgs,
     scalacOptions in (Compile, compile) ++= stdScalacArgs,
         javacOptions in (Test, compile) :=  Seq("-nowarn"),
        scalacOptions in (Test, compile) :=  Seq("-Xlint"),
                              incOptions :=  stdIncOptions
  )

  private def testJavaOptions                = partestProperties map ("-Xmx1g" +: _.commandLineArgs)
  private def compilePath: TaskOf[Seq[File]] = dependencyClasspath in Compile map (_.files filter isJar)
  private def explode(f: File, d: File)      = IO.unzip(f, d, isSourceName _).toSeq

  def createUnzipTask: TaskOf[Seq[File]] = Def task (compilePath.value flatMap (f => explode(f, sourceManaged.value / "compat")))

  def generateProperties(): TaskOf[Seq[File]] = Def task {
    val id    = name.value split "[-]" last;
    val file  = (resourceManaged in Compile).value / s"$id.properties"
    val props = MutableProperties(file)
    props("version.number")              = version.value
    props("scala.version.number")        = scalaVersion.value
    props("scala.binary.version.number") = scalaBinaryVersion.value
    props("bootstrap.moduleid")          = PolicyKeys.bootstrapModuleId.value.toString
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
