def libsbtModuleId = "org.improving" % "psp-libsbt" % sys.props.getOrElse("libsbt.version", "0.5.5")
def libsbtSource   = sys.props get "libsbt.path"
def libsbtPlugin   = libraryDependencies += Defaults.sbtPluginExtra(libsbtModuleId, "0.13", "2.10")

def finish(p: Project) = libsbtSource.fold(p settings libsbtPlugin)(f => p dependsOn ProjectRef(file(f), "libsbt")) settings (
            resolvers += Resolver.url("paulp/sbt-plugins", url("https://dl.bintray.com/paulp/sbt-plugins"))(Resolver.ivyStylePatterns),
            resolvers += "paulp/maven" at "https://dl.bintray.com/paulp/maven",
  libraryDependencies += Defaults.sbtPluginExtra("me.lessis" % "bintray-sbt" % "0.2.0", "0.13", "2.10") excludeAll ExclusionRule("org.scala-lang")
)

lazy val root = finish(project in file("."))
