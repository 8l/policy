package policy
package metabuilding

import sbt._, Keys._

object MetaBuild extends sbt.Build {
  def pspResolvers = Seq(
    Resolver.url("paulp/maven", url("https://dl.bintray.com/paulp/maven")),
    Resolver.url("paulp/sbt-plugins", url("https://dl.bintray.com/paulp/sbt-plugins"))(Resolver.ivyStylePatterns)
  )
  // lazy val libSbt = RootProject(uri(s"git://github.com/paulp/libsbt"))

  lazy val meta = project in file(".") settings (
              resolvers ++= pspResolvers,
    libraryDependencies +=  Defaults.sbtPluginExtra("org.improving" % "psp-libsbt" % "0.3.1-M7",  "0.13", "2.10")
  )
}
