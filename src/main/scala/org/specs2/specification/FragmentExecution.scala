package org.specs2
package specification

import control.Exceptions._
import text._
import main.Arguments
import time.SimpleTimer
import StandardFragments._
import execute._
import io.Location

/**
 * This trait executes Fragments
 *
 * It provides a function which executes fragments
 * and returns executed fragments
 */
trait FragmentExecution {

  /**
   * This method could be overriden to provide alternate behavior when executing an
   * Example
   */
  def executeBody(body: =>Result)(implicit arguments: Arguments): Result =
    if (arguments.plan)         Success("plan")
    else if (arguments.skipAll) Skipped()
    else                        ResultExecution.execute(body)

  /**
   * execute a Fragment.
   */
  def executeFragment(implicit arguments: Arguments): Function[Fragment, ExecutedFragment] = {
    val timer = new SimpleTimer().start
    (f: Fragment) => catchAllOr(execute(f))((e: Throwable) => ExecutedResult(NoMarkup("Fragment evaluation error"), Error(e), timer.stop, f.location, Stats(Error(e))))
  }

  /**
   * execute a Fragment.
   *
   * A Form is executed separately by executing each row and cell, setting the results on each cell
   */
  def execute(f: Fragment)(implicit arguments: Arguments = Arguments()) = f match {
    case Example(FormMarkup(form), _)     => {
      val timer = new SimpleTimer().start
      val executed = if (arguments.plan) form else form.executeForm
      val result = executed.execute
      ExecutedResult(FormMarkup(executed), result, timer.stop, f.location, Stats(result).copy(timer = timer.stop))
    }
	  case e @ Example(s, _)     => {
      val timer = new SimpleTimer().start
      val result = executeBody(e.execute)
      ExecutedResult(s, result, timer.stop, f.location, Stats(result).copy(timer = timer.stop))
    }
	  case Text(s)                       => ExecutedText(s, f.location)
	  case Br()                          => ExecutedBr(f.location)
    case Tab(n)                        => ExecutedTab(n, f.location)
    case Backtab(n)                    => ExecutedBacktab(n, f.location)
	  case End()                         => ExecutedEnd(f.location)
	  case s @ SpecStart(_,a,_)          => ExecutedSpecStart(s.withArgs(arguments.overrideWith(a)), f.location, Stats().startTimer)
	  case e @ SpecEnd(_,_)              => ExecutedSpecEnd(e, f.location, Stats().startTimer)
    case s @ Step(_,_)                 => executeStep("step", s, f.location)
    case s @ Action(_)                 => executeStep("action", s, f.location)
    case _                             => ExecutedNoText(isAction = true, new SimpleTimer, f.location)
  }

  private def executeStep(stepName: String, s: Executable, location: Location)(implicit args: Arguments) = {
    val timer = new SimpleTimer().start
    executeBody(s.execute) match {
      case err if err.isError  => ExecutedResult(NoMarkup(stepName+" error"), err, timer.stop, location, Stats(err))
      case f   if f.isFailure  => ExecutedResult(NoMarkup(stepName+" failure"), f, timer.stop, location, Stats(f))
      case sk  @ Skipped(_, _) => ExecutedResult(NoMarkup("skipped "+stepName), sk, timer.stop, location, Stats(sk))
      case other               => ExecutedNoText(isAction = stepName == "action", new SimpleTimer, location)
    }
  }

  /** these methods are used in tests */
  private[specs2]
  def executeBodies(exs: Fragments)(implicit arguments: Arguments=Arguments()): Seq[Result] = {
    exs.fragments.map(f => executeFragment(arguments)(f)). collect { case r: ExecutedResult => r.result }
  }

  private[specs2]
  def executeExamples(exs: Fragments)(implicit arguments: Arguments=Arguments()): Seq[ExecutedResult] = {
    exs.fragments.map(f => executeFragment(arguments)(f)). collect { case r: ExecutedResult => r }
  }

  private[specs2]
  def executeExamplesResult(exs: Fragments)(implicit arguments: Arguments=Arguments()): Result =
    executeExamples(exs)(arguments).foldLeft(Success():Result) { (res, cur) => res and cur.result }

  private[specs2]
  def executeSpecificationResult(spec: SpecificationStructure)(implicit arguments: Arguments=Arguments()): Result =
    executeExamplesResult(spec.content)(arguments)
}

private[specs2]
object FragmentExecution extends FragmentExecution

