lazy val plugin = ProjectRef(file("/r/psp/libsbt"), "libsbt")

lazy val root = project in file(".") dependsOn plugin

// addSbtPlugin("org.improving" % "psp-libsbt" % sys.props.getOrElse("libsbt.version", "0.4.1-M9"))

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.6")
