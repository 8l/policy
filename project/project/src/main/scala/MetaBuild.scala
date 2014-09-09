package policy
package metabuilding

import sbt._, Keys._

object MetaBuild extends sbt.Build {
  private def maven   = "paulp/meta/maven" at "https://dl.bintray.com/paulp/maven/"
  private def plugins = Resolver.url("paulp/sbt-plugins", url("https://dl.bintray.com/paulp/sbt-plugins/"))(Resolver.ivyStylePatterns)
  // lazy val libSbt  = RootProject(uri(s"git://github.com/paulp/psp-libsbt"))

  lazy val meta = project in file(".") settings (
              resolvers ++= Seq(maven, plugins),
    addSbtPlugin("org.improving" % "psp-libsbt" % "0.3.1-M9")
  )
}
