package org.specs2
package specification

import control._
import Exceptions._
import LazyParameters._
import main.Arguments
import execute._
import text._
import text.Trim._
import org.specs2.internal.scalaz.Monoid
import io.Location
import scala.Either
import data.{SeparatedTags, IncludedExcluded}

/**
 * A Fragment is a piece of a specification. It can be a piece of text, an action or
 * an Example
 */
sealed trait Fragment {
  val linkedTo: Option[SpecificationStructure] = None
  def matches(s: String) = true
  val location: Location = new Location
}

/**
 * Start of a specification.
 *
 * This fragment keeps 2 important pieces of information:
 *
 *  - the name of the specification (which is derived from the specification class or from a user-defined title)
 *    That name stores a unique id for the specification
 *  - the arguments for that specification
 */
case class SpecStart(specName: SpecName, arguments: Arguments = Arguments(), linked: Linked = Linked()) extends Fragment {

  def name = specName.name
  def title = specName.title
  override def matches(s: String) = name matches s

  override def toString = "SpecStart("+title+linkToString+")"
  def linkToString = linked.linkToString

  /** the new arguments take precedence over the old ones */
  def withArgs(args: Arguments) = copy(arguments = args)
  /** the new arguments take override the old ones where defined */
  def overrideArgs(args: Arguments) = copy(arguments = arguments.overrideWith(args))
  
  /** @return true if this spec starts only contains a link referencing another specification */
  def isSeeOnlyLink = linked.isSeeOnlyLink
  /** @return true if this spec starts only contains a link including another specification */
  def isIncludeLink = linked.isIncludeLink
  /** @return true if this spec starts only contains a link to another specification */
  def isLink        = linked.isLink
  /** @return true if this spec must not be displayed */
  def hidden        = linked.hidden
  /** @return the html link if any */
  def link          = linked.link
  /** The name of the specification can be overriden with a user defined title */
  def withName(n: SpecName) = copy(specName = specName.overrideWith(n))
  /** @return a non-linked start*/
  def unlink = SpecStart(specName, arguments)
  /** set the url for the generated documentation */
  def urlIs(url: String) = copy(specName = specName.urlIs(url), linked = linked.urlIs(url))
  /** set the base directory for the generated documentation */
  def baseDirIs(dir: String) = copy(specName = specName.baseDirIs(dir), linked = linked.baseDirIs(dir))
}

/**
 * End of a specification.
 *
 * This marks the end of the Specification and must have the same name as the corresponding SpecStart.
 *
 * There is a Boolean flag on a SpecEnd indicating if the whole specification was just executed as a link (for an index page for example)
 * In this case we must not store statistics for this specification (see Storing.scala)
 */
case class SpecEnd(specName: SpecName, isSeeOnlyLink: Boolean = false) extends Fragment {
  def name = specName.name
  def title = specName.title
  def seeOnlyLinkIs(s: Boolean) = copy(isSeeOnlyLink = s)

  override def matches(s: String) = name matches s
  override def toString = "SpecEnd("+title+")"
}

/**
 * Free text, describing the system to specify
 */
case class Text(t: String) extends Fragment {
  override def matches(s: String) = t.matches(s)
}

/**
 * A Example is:
 *
 * - a description: some text, with possibly some markup annotations for rendering code fragments (used in AutoExamples)
 * - a body: some executable code returning a Result
 */
case class Example private[specification] (desc: MarkupString = NoMarkup(""), body: () => Result) extends Fragment with Executable with Isolable {
  val isolable = true

  def execute = body()
  override def matches(s: String) = desc.toString.removeAll("\n").removeAll("\r").matches(s)
  override def toString = "Example("+desc+")"
  override def map(f: Result => Result) = Example(desc, f(body()))

  override def equals(a: Any) = {
    a match {
      case e: Example => desc == e.desc
      case _          => false
    }
  }

  /** this fragment can not be executed in a separate specification */
  def global = new Example(desc, body) { override val isolable = false }
}

case object Example {
  def apply[T <% Result](desc: String, body: =>T) = new Example(NoMarkup(desc), () => body)
  def apply[T <% Result](markup: MarkupString, body: =>T) = new Example(markup, () => body)
}

/**
 * An Step creates a fragment that will either return an
 * Error Result if there is an exception or a Success.
 *
 * It is usually used to do some initialisation or cleanup before or after all
 * the Fragments.
 *
 * Note that a Step fragment will not be reported in the output.
 *
 * @see the ContextSpec specification
 *
 */
case class Step (step: LazyParameter[Result] = lazyfy(Success()), stopOnFail: Boolean = false) extends Fragment with Executable with Isolable {
  val isolable = true

  def execute = step.value
  override def toString = "Step"

  override def map(f: Result => Result) = Step(step map f)

  /** this fragment can not be executed in a separate specification */
  def global = new Step(step) { override val isolable = false }
}
case object Step {
  /** create a Step object from either a previous result, or a value to evaluate */
  def fromEither[T](r: =>Either[Result, T]) = new Step(either(r))

  private[specs2]
  def either[T](r: =>Either[Result, T]): LazyParameter[Result] = lazyfy {
    r match {
      case Left(l)               => l
      case Right(result: Result) => result
      case Right(other)          => Success()
    }
  }
  /** create a Step object from any value */
  def apply[T](r: =>T) = fromEither(trye(r)(Error(_)))
  /** create a Step object from a stopOnFail value */
  def apply(stopOnFail: Boolean) = new Step(stopOnFail = stopOnFail)
}
/**
 * An Action is similar to a Step but can be executed concurrently with other examples.
 *
 * It is only reported in case of a failure
 */
case class Action (action: LazyParameter[Result] = lazyfy(Success())) extends Fragment with Executable with Isolable {
  val isolable = true

  def execute = action.value
  override def toString = "Action"

  override def map(f: Result => Result) = Action(action map f)

  /** this fragment can not be executed in a separate specification */
  def global = new Action(action) { override val isolable = false }
}
case object Action {
  /** create an Action object from any value */
  def apply[T](r: =>T) = fromEither(trye(r)(Error(_)))
  /** create an Action object from either a previous result, or a value to evaluate */
  def fromEither[T](r: =>Either[Result, T]) = new Action(Step.either(r))
}

/**
 * Those standard Fragments are used to format the specification text:
 *  - End() can be used to "reset" the indentation of text
 *  - Br() can be used to insert a newline
 *  - Tab() can be used to increment the indentation level
 *  - Backtab() can be used to decrement the indentation level
 */
private[specs2]
object StandardFragments {
  case class End() extends Fragment
  case class Br() extends Fragment
  case class Tab(n: Int = 1) extends Fragment
  case class Backtab(n: Int = 1) extends Fragment
}

/**
 * Those fragments are used to tag other fragments in a specification\
 */
object TagsFragments {
  trait TaggingFragment extends Fragment {
    /** tagging names */
    def names: Seq[String]
    /** @return true if the fragment tagged with this must be kept */
    def keep(args: Arguments): Boolean = SeparatedTags(args.include, args.exclude).keep(names)
    /** @return true if this tagging fragment is a section */
    def isSection: Boolean
  }
  /** tags the next fragment */
  case class Tag(names: String*) extends TaggingFragment {
    def isSection = false
    override def toString = names.mkString("Tag(", ",", ")")
    override def equals(o: Any) = {
      o match {
        case t @ Tag(_*)      => names == t.names
        case t @ TaggedAs(_*) => names == t.names
        case _ => false
      }
    }
  }
  /** tags the previous fragment */
  case class TaggedAs(names: String*) extends TaggingFragment {
    def isSection = false
    override def toString = names.mkString("TaggedAs(", ",", ")")
    override def equals(o: Any) = {
      o match {
        case t @ Tag(_*)      => names == t.names
        case t @ TaggedAs(_*) => names == t.names
        case _ => false
      }
    }
  }
  /** the previous fragment starts a section */
  case class AsSection(names: String*) extends TaggingFragment {
    def isSection = true
    override def toString = names.mkString("AsSection(", ",", ")")
    override def equals(o: Any) = {
      o match {
        case s @ AsSection(_*) => names == s.names
        case s @ Section(_*)   => names == s.names
        case _ => false
      }
    }
  }
  /** the next fragment starts a section */
  case class Section(names: String*) extends TaggingFragment {
    def isSection = true
    override def toString = names.mkString("Section(", ",", ")")
    override def equals(o: Any) = {
      o match {
        case s @ AsSection(_*) => names == s.names
        case s @ Section(_*)   => names == s.names
        case _ => false
      }
    }
  }

  /**
   * define a very coarse Monoid for TaggingFragments where appending 2 TaggingFragments returns a Tag object
   * with both list of tags
   */
  implicit def TaggingFragmentsAreMonoid = new Monoid[TaggingFragment] {
    val zero = Tag()
    def append(t1: TaggingFragment, t2: =>TaggingFragment) = Tag((t1.names ++ t2.names):_*)
  }

  /** @return true if the object is a TaggingFragment */
  def isTag(f: Fragment) = f match {
    case t: TaggingFragment => true
    case other              => false
  }
}
