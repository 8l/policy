package policy
package building

import sbt._, Keys._, psp.libsbt._

trait Bootstrap {
  self: PolicyPackage =>

  private def runSlurp(cmd: String): String = scala.sys.process.Process(cmd).lines.mkString

  // Creates a fresh version number, publishes bootstrap jars with that number to the local repository.
  // Records the version in project/local.properties where it has precedence over build.properties.
  // Reboots sbt under the new jars.
  def bootstrapCommands = Seq(
    Command.command("publishLocalBootstrap")(commonBootstrap(_, isLocal = true, Nil)),
    Command.command("publishBootstrap")(commonBootstrap(_, isLocal = false, applyProjects(_ + "/publish"))),
    Command.args("saveBootstrapVersion", "<version>")(saveBootstrapVersion)
  )

  private def saveBootstrapVersion(ws: State, args: Seq[String]): State = {
    val (props, newModule) = args.toList match {
      case Nil                  => localProps -> ws(PolicyKeys.bootstrapModuleId)
      case "local" :: v :: Nil  => localProps -> (PolicyOrg % "bootstrap-compiler" % v)
      case "remote" :: v :: Nil => buildProps -> (PolicyOrg % "bootstrap-compiler" % v)
      case _                    => return psp.libsbt.fail(args mkString ", ")
    }
    updateBootstrapModule(props, newModule)
    ws(sLog).info(s"Updating $BootstrapModuleProperty to $newModule in " + props.filename)
    ws
  }

  private def updateBootstrapModule(props: MutableProperties, newModule: ModuleID): Unit = {
    val m = newModule.toString
    props.write(BootstrapModuleProperty, m)
    sys.props(BootstrapModuleProperty) = m
  }

  private def bootstrapSettings(newVersion: String) = (
       applyProjects(s => name in LocalProject(s) := s"bootstrap-$s")
    ++ applyProjects(s => version in LocalProject(s) := newVersion)
  )

  private def applyProjects[A](f: String => A): Seq[A] = Seq("library", "compiler") map f

  private def commonBootstrap(ws: State, isLocal: Boolean, commands: Seq[String]): State = {
    val newVersion = ws(version) match {
      case v if isLocal => dash(v takeWhile (_ != '-'), runSlurp("bin/unique-version"))
      case v            => v
    }
    val saveCommand = "saveBootstrapVersion %s %s".format( if (isLocal) "local" else "remote" , newVersion )
    val newCommands = applyProjects(_ + "/publishLocal") ++ commands :+ saveCommand :+ "reboot full"

    ws set (bootstrapSettings(newVersion): _*) run (newCommands: _*)
  }
}
