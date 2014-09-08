package policy
package building

import sbt.{ Show => _, _ }, Keys._, psp.libsbt._

object PolicyKeys {
  val repl              = inputKey[Unit]("run policy repl")
  val getScala          = inputKey[ScalaInstance]("download scala version, if not in ivy cache")
  val bootstrapModuleId = settingKey[ModuleID]("module id of bootstrap compiler") in ThisBuild
  val jarPaths          = inputKey[Classpath]("jars in given configuration")
}

// trait ShowDeltas {
//   import scala.Console._


// }


/**
 * Add the inline diffs for given delta
 * @param delta the given delta
 */
// private void addInlineDiffs(Delta<String> delta) {
//     List<String> orig = (List<String>) delta.getOriginal().getLines();
//     List<String> rev = (List<String>) delta.getRevised().getLines();
//     LinkedList<String> origList = new LinkedList<String>();
//     for (Character character : join(orig, "\n").toCharArray()) {
//         origList.add(character.toString());
//     }
//     LinkedList<String> revList = new LinkedList<String>();
//     for (Character character : join(rev, "\n").toCharArray()) {
//         revList.add(character.toString());
//     }
//     List<Delta<String>> inlineDeltas = DiffUtils.diff(origList, revList).getDeltas();
//     if (inlineDeltas.size() < 3) {
//         Collections.reverse(inlineDeltas);
//         for (Delta<String> inlineDelta : inlineDeltas) {
//             Chunk<String> inlineOrig = inlineDelta.getOriginal();
//             Chunk<String> inlineRev = inlineDelta.getRevised();
//             if (inlineDelta.getClass().equals(DeleteDelta.class)) {
//                 origList = wrapInTag(origList, inlineOrig.getPosition(), inlineOrig
//                         .getPosition()
//                         + inlineOrig.size() + 1, this.InlineOldTag, this.InlineOldCssClass);
//             } else if (inlineDelta.getClass().equals(InsertDelta.class)) {
//                 revList = wrapInTag(revList, inlineRev.getPosition(), inlineRev.getPosition()
//                         + inlineRev.size() + 1, this.InlineNewTag, this.InlineNewCssClass);
//             } else if (inlineDelta.getClass().equals(ChangeDelta.class)) {
//                 origList = wrapInTag(origList, inlineOrig.getPosition(), inlineOrig
//                         .getPosition()
//                         + inlineOrig.size() + 1, this.InlineOldTag, this.InlineOldCssClass);
//                 revList = wrapInTag(revList, inlineRev.getPosition(), inlineRev.getPosition()
//                         + inlineRev.size() + 1, this.InlineNewTag, this.InlineNewCssClass);
//             }
//         }
//         StringBuilder origResult = new StringBuilder(), revResult = new StringBuilder();
//         for (String character : origList) {
//             origResult.append(character);
//         }
//         for (String character : revList) {
//             revResult.append(character);
//         }
//         delta.getOriginal().setLines(Arrays.asList(origResult.toString().split("\n")));
//         delta.getRevised().setLines(Arrays.asList(revResult.toString().split("\n")));
//     }
// }

object ShowDiff {
  import difflib._
  import scala.Console._

  implicit class jlistOps[A](xs: jList[A]) {
    def to_s(implicit show: Show[A]): String = xs.asScala map (_.to_s) mkString "\n"
  }

  private def inRed(s: String)   = s"$RED[-$s-]$RESET"
  private def inGreen(s: String) = s"$GREEN{+$s+}$RESET"

  def covers[A](d: Delta[A], index: Int): Boolean = d.getOriginal.getPosition <= index && index <= d.getOriginal.last
  def allWhitespace(d: Delta[String]): Boolean = d.getOriginal.getLines.asScala ++ d.getRevised.getLines.asScala forall (s =>
    s.toCharArray forall (c => c.isWhitespace)
  )

  def wordBasedDiff(s1: String, s2: String): String = {
    val xs     = s1.toList.asJava
    val ys     = s2.toList.asJava
    var deltas = DiffUtils.diff(xs, ys).getDeltas.asScala.toList
    val chars = s1.toCharArray
    val keep = chars.indices.toSet filterNot (i => deltas exists (d => covers(d, i)))
    val res = new StringBuilder
    chars.indices foreach (i =>
      if (keep(i))
        res append chars(i)
      else deltas find (d => covers(d, i)) foreach { delta =>
        deltas = deltas filterNot (_ eq delta)
        delta.getOriginal.getLines.asScala.mkString match {
          case "" =>
          case s  => res append inRed(s)
        }
        delta.getRevised.getLines.asScala.mkString match {
          case "" =>
          case s  => res append inGreen(s)
        }
      }
    )
    res.toString
  }

  // private def inRed[A: Show](xs: jList[A]): String   = inRed(xs.asScala map (_.to_s) mkString "\n")
  // private def inRed[A: Show](xs: jList[A]): String = xs.asScala.toList match {
  //   case x :: Nil => inRed(x.to_s)
  //   case xs       => xs map (x => inRed(x.to_s) + "\n") mkString ""
  // }

  // private def inGreen[A: Show](xs: jList[A]): String = xs.asScala.toList match {
  //   case x :: Nil => inGreen(x.to_s)
  //   case xs       => xs map (x => inGreen(x.to_s) + "\n") mkString ""
  // }

  // implicit def showChunk[A: Show] : Show[Chunk[A]] = psp.std.api.Show[Chunk[A]](c =>
  //   "XXX"
  //   // wip
  // )

  implicit def showDelta[A: Show] : Show[Delta[A]] = psp.std.api.Show[Delta[A]](d =>
    d.getType match {
      case Delta.TYPE.CHANGE => wordBasedDiff(d.getOriginal.getLines.to_s, d.getRevised.getLines.asScala.to_s)
       // inRed(d.getOriginal.getLines) + inGreen(d.getRevised.getLines)
      case Delta.TYPE.DELETE => wordBasedDiff(d.getOriginal.getLines.to_s, "") //  inRed(d.getOriginal.getLines.to_s)
      case Delta.TYPE.INSERT => wordBasedDiff("", d.getRevised.getLines.to_s)  // inGreen(d.getRevised.getLines.to_s)
    }
  )
  def diffRows[A](original: Seq[A], revised: Seq[A]): Seq[Delta[A]] = {
    val patch = DiffUtils.diff(original.toList.asJava, revised.toList.asJava)
    patch.getDeltas.asScala.toList
  }
  // def genDiff(original: Seq[String], revised: Seq[String]): Seq[DiffRow] = {
  //   val xs = original.toList.asJava
  //   val ys = revised.toList.asJava
  //   val gen = new DiffRowGenerator.Builder showInlineDiffs true columnWidth 160 build; // showInlineDiffs true ignoreWhiteSpaces true columnWidth 160 build;
  //   gen.generateDiffRows(xs, ys, DiffUtils.diff(xs, ys)).asScala.toList
  // }
  // public enum Tag {
  //     INSERT, DELETE, CHANGE, EQUAL
  // }

  // /**
  //  * @return the tag
  //  */
  // public Tag getTag() {
  //     return tag;
  // }

  def diffSettings(state: State, command: String): String = {
    val s1 = state.dumpSettings
    val s2 = (command :: state).process(Command.process).dumpSettings
    val deltas = diffRows(s1, s2) filterNot allWhitespace
    // deltas foreach println
    deltas map (_.to_s) mkString ("\n", "\n", "\n")
  }
}


//       genDiff(s1 map ("" + _), s2 map ("" + _)) filter (_.getTag != DiffRow.Tag.EQUAL) map { row =>
//         pairs.foldLeft(row.getOldLine) { case (res, (from, to)) => res.replaceAllLiterally(from, to) }
//       } mkString "\n"


//        //  [CHANGE,version                      root         *            *               "<span class="editOldInline">1.0.0-M6</span>",version                      root         *            *               "<span class="editNewInline">my bippy</span>"]

//        // mkString "\n"
//       // public List<DiffRow> generateDiffRows(List<String> original, List<String> revised, Patch<String> patch) {

//       // gen

//       // *    DiffRowGenerator generator = new DiffRowGenerator.Builder().showInlineDiffs(true).
//       // *      ignoreWhiteSpaces(true).columnWidth(100).build();

//       // val lines = diffRows(s1, s2) flatMap {
//       //   case x: ChangeDelta[_] => x.getOriginal.getLines.asScala.map("- " + _) ++ x.getRevised.getLines.asScala.map("+ " + _)
//       //   case x: DeleteDelta[_] => x.getOriginal.getLines.asScala map ("- " + _)
//       //   case x: InsertDelta[_] => x.getRevised.getLines.asScala map ("+ " + _)
//       // }
//       // lines mkString "\n"
//     },
//     cmd.effectful("jarsIn", "<config>")((s, c) => (s classpathIn c.toLowerCase).files map s.relativize mkString "\n")
//   )

// }

object PolicyBuild extends sbt.Build {
  import Deps._
  lazy val root = (
    project.rootSetup
      dependsOn ( library, compilerProject )
      aggregate ( library, compilerProject, compat )
      also policyCommands
  )
  lazy val library = project.setup addMima scalaLibrary

  lazy val compilerProject = (
    Project("compiler", file("compiler")).setup
      dependsOn library
      deps (jline, testInterface, (diffutils % "test").intransitive)
  )

  lazy val compat = (
    project.setup.noArtifacts
      dependsOn ( compilerProject )
      sbtDeps ( "interface", "compiler-interface" )
  )

  def policyCommands = commands ++= Seq(
    cmd.effectful("dump")(_.dumpSettings mkString "\n"),
    cmd.effectful("diff", "<cmd>")(ShowDiff.diffSettings),
    cmd.effectful("jarsIn", "<config>")((s, c) => (s classpathIn c.toLowerCase).files map s.relativize mkString "\n")
  )
}

    //  { (state, command) =>
    //   val s1 = state.dumpSettings
    //   val s2 = (command :: state).process(Command.process).dumpSettings
    //   // val patch = diffRows(s1, s2)
    //   val Old = """<span class="editOldInline">"""
    //   val New = """<span class="editNewInline">"""
    //   val Reset = """</span>"""
    //   import scala.Console._
    //   val pairs = List(
    //     Old   -> RED,
    //     New   -> GREEN,
    //     Reset -> RESET
    //   )
    //   genDiff(s1 map ("" + _), s2 map ("" + _)) filter (_.getTag != DiffRow.Tag.EQUAL) map { row =>
    //     pairs.foldLeft(row.getOldLine) { case (res, (from, to)) => res.replaceAllLiterally(from, to) }
    //   } mkString "\n"


    //    //  [CHANGE,version                      root         *            *               "<span class="editOldInline">1.0.0-M6</span>",version                      root         *            *               "<span class="editNewInline">my bippy</span>"]

    //    // mkString "\n"
    //   // public List<DiffRow> generateDiffRows(List<String> original, List<String> revised, Patch<String> patch) {

    //   // gen

    //   // *    DiffRowGenerator generator = new DiffRowGenerator.Builder().showInlineDiffs(true).
    //   // *      ignoreWhiteSpaces(true).columnWidth(100).build();

    //   // val lines = diffRows(s1, s2) flatMap {
    //   //   case x: ChangeDelta[_] => x.getOriginal.getLines.asScala.map("- " + _) ++ x.getRevised.getLines.asScala.map("+ " + _)
    //   //   case x: DeleteDelta[_] => x.getOriginal.getLines.asScala map ("- " + _)
    //   //   case x: InsertDelta[_] => x.getRevised.getLines.asScala map ("+ " + _)
    //   // }
    //   // lines mkString "\n"
    // },
