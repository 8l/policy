package policy
package building

import sbt._, Keys._
import com.typesafe.tools.mima.core._
import psp.libsbt._

object MimaPolicy {
  private val removedPackages = wordSet("""
    scala.collection.script
  """)

  private val removedClasses = wordSet("""
    scala.collection.IterableProxy
    scala.collection.IterableProxyLike
    scala.collection.MapProxy
    scala.collection.MapProxyLike
    scala.collection.SeqProxy
    scala.collection.SeqProxyLike
    scala.collection.SetProxy
    scala.collection.SetProxyLike
    scala.collection.TraversableProxy
    scala.collection.TraversableProxyLike
    scala.collection.immutable.MapProxy
    scala.collection.immutable.SetProxy
    scala.collection.mutable.BufferProxy
    scala.collection.mutable.MapProxy
    scala.collection.mutable.ObservableBuffer
    scala.collection.mutable.ObservableMap
    scala.collection.mutable.ObservableSet
    scala.collection.mutable.PriorityQueueProxy
    scala.collection.mutable.QueueProxy
    scala.collection.mutable.SetProxy
    scala.collection.mutable.StackProxy
    scala.collection.mutable.SynchronizedBuffer
    scala.collection.mutable.SynchronizedMap
    scala.collection.mutable.SynchronizedPriorityQueue
    scala.collection.mutable.SynchronizedQueue
    scala.collection.mutable.SynchronizedSet
    scala.collection.mutable.SynchronizedStack
  """)

  // filterImpl, filteredTail exclusions are from the mainline.
  private val removedMethods = wordSet("""
    <<
    filterImpl
    filteredTail
  """)

  def isRemoved(name: String): Boolean = {
    def pkg = name take (name lastIndexOf '.')
    removedClasses(name) || removedPackages(pkg)
  }

  object HasName { def unapply(x: HasDeclarationName) = Some(x.decodedName) }
  object HasFullNames { def unapply(x: Iterable[ClassInfo]) = Some(x.toList map (_.fullName)) }
  object PackageOf {
    def unapply(x: HasDeclarationName): Option[String] = x match {
      case x: MemberInfo => unapply(x.owner)
      case x: ClassInfo  => Some(x.fullName split "[.]" dropRight 1 mkString ".")
      case _             => None
    }
  }

  private def arityFilters = {
    for (s <- Vector("Product", "Tuple", "Function", "runtime.AbstractFunction") ; n <- (10 to 22) ; d <- List("", "$")) yield
      ProblemFilters.exclude[MissingClassProblem](s"scala.$s$n$d")
  }
  private def deletedFilter: ProblemFilter = {
    case MissingClassProblem(cl) if removedPackages exists (cl.fullName startsWith _ + ".") => false
    case MissingClassProblem(cl) if removedClasses(cl.fullName split "[$#]" head)           => false
    case p: MissingMethodProblem if p.affectedVersion == Problem.ClassVersion.Old           => false // adding methods okay with me
    case MissingMethodProblem(HasName(name)) if removedMethods(name)                        => false
    case MissingTypesProblem(_, HasFullNames(missing)) if missing forall isRemoved          => false
    case IncompatibleMethTypeProblem(PackageOf("scala.sys.process"), _)                     => false // TODO - SyncVar
    case IncompatibleMethTypeProblem(HasName(name), _) if removedMethods(name)              => false
    case IncompatibleResultTypeProblem(HasName(name), _) if removedMethods(name)            => false
    case UpdateForwarderBodyProblem(meth)                                                   => false
    case _                                                                                  => true
  }
  private def individualFilters = Vector(
    ProblemFilters.exclude[MissingMethodProblem]("scala.collection.Iterator.corresponds")
  )

  def filters = (arityFilters ++ individualFilters) :+ deletedFilter

  def patternMatch(p: Problem) = p match {
    case MissingFieldProblem(oldfld: MemberInfo)                                      =>
    case MissingMethodProblem(meth: MemberInfo)                                       =>
    case UpdateForwarderBodyProblem(meth: MemberInfo)                                 =>
    case MissingClassProblem(oldclazz: ClassInfo)                                     =>
    case AbstractClassProblem(oldclazz: ClassInfo)                                    =>
    case FinalClassProblem(oldclazz: ClassInfo)                                       =>
    case FinalMethodProblem(newmemb: MemberInfo)                                      =>
    case IncompatibleFieldTypeProblem(oldfld: MemberInfo, newfld: MemberInfo)         =>
    case IncompatibleMethTypeProblem(oldmeth: MemberInfo, newmeths: List[MemberInfo]) =>
    case IncompatibleResultTypeProblem(oldmeth: MemberInfo, newmeth: MemberInfo)      =>
    case AbstractMethodProblem(newmeth: MemberInfo)                                   =>
    case IncompatibleTemplateDefProblem(oldclazz: ClassInfo, newclazz: ClassInfo)     =>
    case MissingTypesProblem(newclazz: ClassInfo, missing: Iterable[ClassInfo])       =>
    case CyclicTypeReferenceProblem(clz: ClassInfo)                                   =>
    case InaccessibleFieldProblem(newfld: MemberInfo)                                 =>
    case InaccessibleMethodProblem(newmeth: MemberInfo)                               =>
    case InaccessibleClassProblem(newclazz: ClassInfo)                                =>
    case x: MemberProblem                                                             =>
    case x: TemplateProblem                                                           =>
  }
}

