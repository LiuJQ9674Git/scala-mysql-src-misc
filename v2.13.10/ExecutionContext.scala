package scala.concurrent


import java.util.concurrent.{ ExecutorService, Executor }
import scala.annotation.implicitNotFound

trait ExecutionContext {

  /** Runs a block of code on this execution context.
   *
   *  @param runnable  the task to execute
   */
  def execute(runnable: Runnable): Unit

  /** Reports that an asynchronous computation failed.
   *
   *  @param cause  the cause of the failure
   */
  def reportFailure(@deprecatedName("t") cause: Throwable): Unit

  /** Prepares for the execution of a task. Returns the prepared
     *  execution context. The recommended implementation of
     *  `prepare` is to return `this`.
     *
     *  This method should no longer be overridden or called. It was
     *  originally expected that `prepare` would be called by
     *  all libraries that consume ExecutionContexts, in order to
     *  capture thread local context. However, this usage has proven
     *  difficult to implement in practice and instead it is
     *  now better to avoid using `prepare` entirely.
     *
     *  Instead, if an `ExecutionContext` needs to capture thread
     *  local context, it should capture that context when it is
     *  constructed, so that it doesn't need any additional
     *  preparation later.
     */
  @deprecated("preparation of ExecutionContexts will be removed", "2.12.0")
  // This cannot be removed until there is a suitable replacement
  def prepare(): ExecutionContext = this
}

/**
 * An [[ExecutionContext]] that is also a
 * Java [[java.util.concurrent.Executor Executor]].
 */
trait ExecutionContextExecutor extends ExecutionContext with Executor

/**
 * An [[ExecutionContext]] that is also a
 * Java [[java.util.concurrent.ExecutorService ExecutorService]].
 */
trait ExecutionContextExecutorService extends ExecutionContextExecutor with ExecutorService


/** Contains factory methods for creating execution contexts.
 */
object ExecutionContext {
  /**
   * The global [[ExecutionContext]]. This default `ExecutionContext` implementation is backed by a work-stealing thread
   * pool. It can be configured via the following system properties:
   *
   *   - `scala.concurrent.context.minThreads` = defaults to "1"
   *   - `scala.concurrent.context.numThreads` = defaults to "x1" (i.e. the current number of available processors * 1)
   *   - `scala.concurrent.context.maxThreads` = defaults to "x1" (i.e. the current number of available processors * 1)
   *   - `scala.concurrent.context.maxExtraThreads` = defaults to "256"
   *
   * The pool size of threads is then `numThreads` bounded by `minThreads` on the lower end and `maxThreads` on the high end.
   *
   * The `maxExtraThreads` is the maximum number of extra threads to have at any given time to evade deadlock,
   * see [[scala.concurrent.blocking]].
   *
   * The `global` execution context can be used explicitly, by defining an
   * `implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global`, or by importing
   * [[ExecutionContext.Implicits.global]].
   *
   * == Batching short-lived nested tasks ==
   *
   * Asynchronous code with short-lived nested tasks is executed more efficiently when using
   * `ExecutionContext.opportunistic` (continue reading to learn why it is `private[scala]` and how to access it).
   *
   * `ExecutionContext.opportunistic` uses the same thread pool as `ExecutionContext.global`. It attempts to batch
   * nested task and execute them on the same thread as the enclosing task. This is ideally suited to execute
   * short-lived tasks as it reduces the overhead of context switching.
   *
   * WARNING: long-running and/or blocking tasks should be demarcated within [[scala.concurrent.blocking]]-blocks
   *          to ensure that any pending tasks in the current batch can be executed by another thread on `global`.
   *
   * === How to use ===
   *
   * This field is `private[scala]` to maintain binary compatibility. It was added in 2.13.4, code that references it
   * directly fails to run with a 2.13.0-3 Scala library.
   *
   * Libraries should not reference this field directly because users of the library might be using an earlier Scala
   * version. In order to use the batching `ExecutionContext` in a library, the code needs to fall back to `global`
   * in case the `opportunistic` field is missing (example below). The resulting `ExecutionContext` has batching
   * behavior in all Scala 2.13 versions (`global` is batching in 2.13.0-3).
   *
   * {{{
   * implicit val ec: scala.concurrent.ExecutionContext = try {
   *   scala.concurrent.ExecutionContext.getClass
   *     .getDeclaredMethod("opportunistic")
   *     .invoke(scala.concurrent.ExecutionContext)
   *     .asInstanceOf[scala.concurrent.ExecutionContext]
   * } catch {
   *   case _: NoSuchMethodException =>
   *     scala.concurrent.ExecutionContext.global
   * }
   * }}}
   *
   * Application authors can safely use the field because the Scala version at run time is the same as at compile time.
   * Options to bypass the access restriction include:
   *
   *   1. Using a structural type (example below). This uses reflection at run time.
   *   1. Writing a Scala `object` in the `scala` package (example below).
   *   1. Writing a Java source file. This works because `private[scala]` is emitted as `public` in Java bytecode.
   *
   * {{{
   * // Option 1
   * implicit val ec: scala.concurrent.ExecutionContext =
   *   (scala.concurrent.ExecutionContext:
   *     {def opportunistic: scala.concurrent.ExecutionContextExecutor}
   *   ).opportunistic
   *
   * // Option 2
   * package scala {
   *   object OpportunisticEC {
   *     implicit val ec: scala.concurrent.ExecutionContext =
   *       scala.concurrent.ExecutionContext.opportunistic
   *   }
   * }
   * }}}
   *
   * @return the global [[ExecutionContext]]
   */
  final lazy val global: ExecutionContextExecutor = impl.ExecutionContextImpl.fromExecutor(null: Executor)

  /**
   * WARNING: Only ever execute logic which will quickly return control to the caller.
   *
   * This `ExecutionContext` steals execution time from other threads by having its
   * `Runnable`s run on the `Thread` which calls `execute` and then yielding back control
   * to the caller after *all* its `Runnable`s have been executed.
   * Nested invocations of `execute` will be trampolined to prevent uncontrolled stack space growth.
   *
   * When using `parasitic` with abstractions such as `Future` it will in many cases be non-deterministic
   * as to which `Thread` will be executing the logic, as it depends on when/if that `Future` is completed.
   *
   * Do *not* call any blocking code in the `Runnable`s submitted to this `ExecutionContext`
   * as it will prevent progress by other enqueued `Runnable`s and the calling `Thread`.
   * 
   * Symptoms of misuse of this `ExecutionContext` include, but are not limited to, deadlocks
   * and severe performance problems.
   *
   * Any `NonFatal` or `InterruptedException`s will be reported to the `defaultReporter`.
   */
  object parasitic extends ExecutionContextExecutor with BatchingExecutor {
    override final def submitForExecution(runnable: Runnable): Unit = runnable.run()
    override final def execute(runnable: Runnable): Unit = submitSyncBatched(runnable)
    override final def reportFailure(t: Throwable): Unit = defaultReporter(t)
  }

  /**
   * See [[ExecutionContext.global]].
   */
  private[scala] lazy val opportunistic: ExecutionContextExecutor = new ExecutionContextExecutor with BatchingExecutor {
    final override def submitForExecution(runnable: Runnable): Unit = global.execute(runnable)

    final override def execute(runnable: Runnable): Unit =
      if ((!runnable.isInstanceOf[impl.Promise.Transformation[_,_]] ||
        runnable.asInstanceOf[impl.Promise.Transformation[_,_]].benefitsFromBatching)
        && runnable.isInstanceOf[Batchable])
        submitAsyncBatched(runnable)
      else
        submitForExecution(runnable)

    override final def reportFailure(t: Throwable): Unit = global.reportFailure(t)
  }

  object Implicits {
    /**
     * An accessor that can be used to import the global `ExecutionContext` into the implicit scope,
     * see [[ExecutionContext.global]].
     */
    implicit final def global: ExecutionContext = ExecutionContext.global
  }

  /** Creates an `ExecutionContext` from the given `ExecutorService`.
   *
   *  @param e         the `ExecutorService` to use. If `null`,
   *     a new `ExecutorService` is created with [[scala.concurrent.ExecutionContext$.global default configuration]].
   *  @param reporter  a function for error reporting
   *  @return          the `ExecutionContext` using the given `ExecutorService`
   */
  def fromExecutorService(e: ExecutorService, reporter: Throwable => Unit): ExecutionContextExecutorService =
    impl.ExecutionContextImpl.fromExecutorService(e, reporter)

  /** Creates an `ExecutionContext` from the given `ExecutorService`
   *    with the [[scala.concurrent.ExecutionContext$.defaultReporter default reporter]].
   *
   *  If it is guaranteed that none of the executed tasks are blocking, a single-threaded `ExecutorService`
   *  can be used to create an `ExecutionContext` as follows:
   *
   *  {{{
   *  import java.util.concurrent.Executors
   *  val ec = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
   *  }}}
   *
   *  @param e the `ExecutorService` to use. If `null`,
   *    a new `ExecutorService` is created with [[scala.concurrent.ExecutionContext$.global default configuration]].
   *  @return  the `ExecutionContext` using the given `ExecutorService`
   */
  def fromExecutorService(e: ExecutorService): ExecutionContextExecutorService = fromExecutorService(e, defaultReporter)

  /** Creates an `ExecutionContext` from the given `Executor`.
   *
   *  @param e         the `Executor` to use. If `null`,
   *    a new `Executor` is created with [[scala.concurrent.ExecutionContext$.global default configuration]].
   *  @param reporter  a function for error reporting
   *  @return          the `ExecutionContext` using the given `Executor`
   */
  def fromExecutor(e: Executor, reporter: Throwable => Unit): ExecutionContextExecutor =
    impl.ExecutionContextImpl.fromExecutor(e, reporter)

  /** Creates an `ExecutionContext` from the given `Executor`
   *    with the [[scala.concurrent.ExecutionContext$.defaultReporter default reporter]].
   *
   *  @param e the `Executor` to use. If `null`,
   *    a new `Executor` is created with [[scala.concurrent.ExecutionContext$.global default configuration]].
   *  @return  the `ExecutionContext` using the given `Executor`
   */
  def fromExecutor(e: Executor): ExecutionContextExecutor = fromExecutor(e, defaultReporter)

  /** The default reporter simply prints the stack trace of the `Throwable` to [[java.lang.System#err System.err]].
   *
   *  @return the function for error reporting
   */
  final val defaultReporter: Throwable => Unit = _.printStackTrace()
}