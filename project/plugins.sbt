resolvers ++= Seq(
  "paulp/maven" at "https://dl.bintray.com/paulp/maven",
  Resolver.url("paulp/sbt-plugins", url("https://dl.bintray.com/paulp/sbt-plugins"))(Resolver.ivyStylePatterns)
)

libraryDependencies += "org.improving" %% "psp-const" % "1.0.1"

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.2")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.6")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-javaversioncheck" % "0.1.0")

libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0"

lazy val root = project in file(".") dependsOn libSbt

lazy val libSbt = file("../libsbt")

//addSbtPlugin("org.improving" % "psp-libsbt" % "0.3.1-M4")
