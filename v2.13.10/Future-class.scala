package scala.concurrent

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

import scala.util.control.{NonFatal, NoStackTrace}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.collection.BuildFrom
import scala.collection.mutable.{Builder, ArrayBuffer}
import scala.reflect.ClassTag

import scala.concurrent.ExecutionContext.parasitic

/** A `Future` represents a value which may or may not *currently* be available,
 *  but will be available at some point, or an exception if that value could not be made available.
 *
 *  Asynchronous computations that yield futures are created with the `Future.apply`
 *  call and are computed using a supplied `ExecutionContext`,
 * which can be backed by a Thread pool.
 *
 *  {{{
 *  import ExecutionContext.Implicits.global
 *  val s = "Hello"
 *  val f: Future[String] = Future {
 *    s + " future!"
 *  }
 *  f foreach {
 *    msg => println(msg)
 *  }
 *  }}}
 *
 *  @see [[https://docs.scala-lang.org/overviews/core/futures.html Futures and Promises]]
 *
 *  @define multipleCallbacks
 *  Multiple callbacks may be registered; there is no guarantee that they will be
 *  executed in a particular order.
 *
 *  @define caughtThrowables
 *  This future may contain a throwable object and this means that the future failed.
 *  Futures obtained through combinators have the same exception as the future they were obtained from.
 *  The following throwable objects are not contained in the future:
 *  - `Error` - fatal errors are not contained within futures
 *  - `InterruptedException` - not contained within futures
 *  - all `scala.util.control.ControlThrowable` except `NonLocalReturnControl` - not contained within futures
 *
 *  Instead, the future is completed with a ExecutionException with one of the exceptions above
 *  as the cause.
 *  If a future is failed with a `scala.runtime.NonLocalReturnControl`,
 *  it is completed with a value from that throwable instead.
 *
 *  @define swallowsExceptions
 *  Since this method executes asynchronously and does not produce a return value,
 *  any non-fatal exceptions thrown will be reported to the `ExecutionContext`.
 *
 *  @define nonDeterministic
 *  Note: using this method yields nondeterministic dataflow programs.
 *
 *  @define forComprehensionExamples
 *  Example:
 *
 *  {{{
 *  val f = Future { 5 }
 *  val g = Future { 3 }
 *  val h = for {
 *    x: Int <- f // returns Future(5)
 *    y: Int <- g // returns Future(3)
 *  } yield x + y
 *  }}}
 *
 *  is translated to:
 *
 *  {{{
 *  f flatMap { (x: Int) => g map { (y: Int) => x + y } }
 *  }}}
 *
 * @define callbackInContext
 * The provided callback always runs in the provided implicit
 *`ExecutionContext`, though there is no guarantee that the
 * `execute()` method on the `ExecutionContext` will be called once
 * per callback or that `execute()` will be called in the current
 * thread. That is, the implementation may run multiple callbacks
 * in a batch within a single `execute()` and it may run
 * `execute()` either immediately or asynchronously.
 * Completion of the Future must *happen-before* the invocation of the callback.
 */
trait Future[+T] extends Awaitable[T] {

  /* Callbacks */

  /** When this future is completed, either through an exception, or a value,
   *  apply the provided function.
   *
   *  If the future has already been completed,
   *  this will either be applied immediately or be scheduled asynchronously.
   *
   *  Note that the returned value of `f` will be discarded.
   *
   *  $swallowsExceptions
   *  $multipleCallbacks
   *  $callbackInContext
   *
   * @tparam U    only used to accept any return type of the given callback function
   * @param f     the function to be executed when this `Future` completes
   * @group Callbacks
   */
  def onComplete[U](f: Try[T] => U)(implicit executor: ExecutionContext): Unit


  /* Miscellaneous */

  /** Returns whether the future had already been completed with
   *  a value or an exception.
   *
   *  $nonDeterministic
   *
   *  @return    `true` if the future was completed, `false` otherwise
   * @group Polling
   */
  def isCompleted: Boolean

  /** The current value of this `Future`.
   *
   *  $nonDeterministic
   *
   *  If the future was not completed the returned value will be `None`.
   *  If the future was completed the value will be `Some(Success(t))`
   *  if it contained a valid result, or `Some(Failure(error))` if it contained
   *  an exception.
   *
   * @return    `None` if the `Future` wasn't completed, `Some` if it was.
   * @group Polling
   */
  def value: Option[Try[T]]


  /* Projections */

  /** The returned `Future` will be successfully completed with the `Throwable` of the original `Future`
   *  if the original `Future` fails.
   *
   *  If the original `Future` is successful, the returned `Future` is failed with a `NoSuchElementException`.
   *
   *  $caughtThrowables
   *
   * @return a failed projection of this `Future`.
   * @group Transformations
   */
  def failed: Future[Throwable] = transform(Future.failedFun)(parasitic)


  /* Monadic operations */

  /** Creates a new future which holds the result of this future if it was completed successfully, or, if not,
   *  the result of the `that` future if `that` is completed successfully.
   *  If both futures are failed, the resulting future holds the throwable object of the first future.
   *
   *  Using this method will not cause concurrent programs to become nondeterministic.
   *
   *  Example:
   *  {{{
   *  val f = Future { throw new RuntimeException("failed") }
   *  val g = Future { 5 }
   *  val h = f fallbackTo g
   *  h foreach println // Eventually prints 5
   *  }}}
   *
   * @tparam U     the type of the other `Future` and the resulting `Future`
   * @param that   the `Future` whose result we want to use if this `Future` fails.
   * @return       a `Future` with the successful result of this or that `Future` or the failure of this `Future` if both fail
   * @group Transformations
   */
  def fallbackTo[U >: T](that: Future[U]): Future[U] =
    if (this eq that) this
    else {
      implicit val ec = parasitic
      transformWith {
        t =>
          if (t.isInstanceOf[Success[T]]) this
          else that transform { tt => if (tt.isInstanceOf[Success[U]]) tt else t }
      }
    }

  /** Creates a new `Future[S]` which is completed with this `Future`'s result if
   *  that conforms to `S`'s erased type or a `ClassCastException` otherwise.
   *
   * @tparam S     the type of the returned `Future`
   * @param tag    the `ClassTag` which will be used to cast the result of this `Future`
   * @return       a `Future` holding the casted result of this `Future` or a `ClassCastException` otherwise
   * @group Transformations
   */
  def mapTo[S](implicit tag: ClassTag[S]): Future[S] = {
    implicit val ec = parasitic
    val boxedClass = {
      val c = tag.runtimeClass
      if (c.isPrimitive) Future.toBoxed(c) else c
    }
    require(boxedClass ne null)
    map(s => boxedClass.cast(s).asInstanceOf[S])
  }

  /** Applies the side-effecting function to the result of this future, and returns
   *  a new future with the result of this future.
   *
   *  This method allows one to enforce that the callbacks are executed in a
   *  specified order.
   *
   *  Note that if one of the chained `andThen` callbacks throws
   *  an exception, that exception is not propagated to the subsequent `andThen`
   *  callbacks. Instead, the subsequent `andThen` callbacks are given the original
   *  value of this future.
   *
   *  The following example prints out `5`:
   *
   *  {{{
   *  val f = Future { 5 }
   *  f andThen {
   *    case r => throw new RuntimeException("runtime exception")
   *  } andThen {
   *    case Failure(t) => println(t)
   *    case Success(v) => println(v)
   *  }
   *  }}}
   *
   * $swallowsExceptions
   *
   * @tparam U     only used to accept any return type of the given `PartialFunction`
   * @param pf     a `PartialFunction` which will be conditionally applied to the outcome of this `Future`
   * @return       a `Future` which will be completed with the exact same outcome as this `Future`
   *                  but after the `PartialFunction` has been executed.
   * @group Callbacks
   */
  def andThen[U](pf: PartialFunction[Try[T], U])(implicit executor: ExecutionContext): Future[T] =
    transform {
      result =>
        try pf.applyOrElse[Try[T], Any](result, Future.id[Try[T]])
        catch { case t if NonFatal(t) => executor.reportFailure(t) }
        // TODO: use `finally`?
        result
    }
}



