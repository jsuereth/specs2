package org.specs2
package runner
import specification._
import io._
import text.Plural._

trait Printer extends Statistics with Output {
  val print: Function[(Int, S, ExecutedFragment), ExecutedFragment]
}
trait NestedPrinter extends Printer with TotalStatistics {

  val print: Function[(Int, Stats, ExecutedFragment), ExecutedFragment] = { p: (Int, Stats, ExecutedFragment) => 
	p match { 
  	  case (level, stats, ExecutedText(s)) => println(("  " * level) + s)
	  case (level, stats, ExecutedResult(s, result)) => {
	    println("  " * level + status(result) + " " + s)
	    printMessage(level + 1, result)
	  }
	  case (_, _, ExecutedPar()) => println("")
	  case (_, Stats(examplesNumber), ExecutedSpecEnd(n)) => {
	    println("\nTotal for specification" + (if (n.isEmpty) n else " "+n))
	    println(examplesNumber qty "example")
	  }
	  case _ => ()
    }
	p._3
  }
  
  private def printMessage(level: Int, result: Result) = {
	result match {
	  case Success(_) => ()
	  case _ => println("  " * level + result.message)
	}
  }
  private def status(result: Result) = {
	result match {
	  case Success(_) => "+"
	  case Failure(_) => "x"
	  case Error(_)   => "x"
	  case Pending(_) => "o"
	  case Skipped(_) => "o"
	}
  }
}