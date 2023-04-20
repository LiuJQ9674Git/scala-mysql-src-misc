/** Asynchronously processes the value in the future once the value becomes available.
   *
   *  WARNING: Will not be called if this future is never completed or if it is completed with a failure.
   *
   *  $swallowsExceptions
   *
   * @tparam U     only used to accept any return type of the given callback function
   * @param f      the function which will be executed if this `Future` completes with a result,
   *               the return value of `f` will be discarded.
   * @group Callbacks
   */
  def foreach[U](f: T => U)(implicit executor: ExecutionContext): Unit = onComplete { _ foreach f }

  /** Creates a new future by applying the 's' function to the successful result of
   *  this future, or the 'f' function to the failed result. If there is any non-fatal
   *  exception thrown when 's' or 'f' is applied, that exception will be propagated
   *  to the resulting future.
   *
   * @tparam S  the type of the returned `Future`
   * @param  s  function that transforms a successful result of the receiver
   *              into a successful result of the returned future
   * @param  f  function that transforms a failure of the receiver into a failure of the returned future
   * @return    a `Future` that will be completed with the transformed value
   * @group Transformations
   */
  def transform[S](s: T => S, f: Throwable => Throwable)(implicit executor: ExecutionContext): Future[S] =
    transform {
      t =>
        if (t.isInstanceOf[Success[T]]) t map s
        else throw f(t.asInstanceOf[Failure[T]].exception) // will throw fatal errors!
    }

  /** Creates a new Future by applying the specified function to the result
   * of this Future. If there is any non-fatal exception thrown when 'f'
   * is applied then that exception will be propagated to the resulting future.
   *
   * @tparam S  the type of the returned `Future`
   * @param  f  function that transforms the result of this future
   * @return    a `Future` that will be completed with the transformed value
   * @group Transformations
   */
  def transform[S](f: Try[T] => Try[S])(implicit executor: ExecutionContext): Future[S]

  /** Creates a new Future by applying the specified function, which produces a Future, to the result
   * of this Future. If there is any non-fatal exception thrown when 'f'
   * is applied then that exception will be propagated to the resulting future.
   *
   * @tparam S  the type of the returned `Future`
   * @param  f  function that transforms the result of this future
   * @return    a `Future` that will be completed with the transformed value
   * @group Transformations
   */
  def transformWith[S](f: Try[T] => Future[S])(implicit executor: ExecutionContext): Future[S]


  /** Creates a new future by applying a function to the successful result of
   *  this future. If this future is completed with an exception then the new
   *  future will also contain this exception.
   *
   *  Example:
   *
   *  {{{
   *  val f = Future { "The future" }
   *  val g = f map { x: String => x + " is now!" }
   *  }}}
   *
   *  Note that a for comprehension involving a `Future`
   *  may expand to include a call to `map` and or `flatMap`
   *  and `withFilter`.  See [[scala.concurrent.Future#flatMap]] for an example of such a comprehension.
   *
   *
   * @tparam S  the type of the returned `Future`
   * @param f   the function which will be applied to the successful result of this `Future`
   * @return    a `Future` which will be completed with the result of the application of the function
   * @group Transformations
   */
  def map[S](f: T => S)(implicit executor: ExecutionContext): Future[S] = transform(_ map f)

  /** Creates a new future by applying a function to the successful result of
   *  this future, and returns the result of the function as the new future.
   *  If this future is completed with an exception then the new future will
   *  also contain this exception.
   *
   *  $forComprehensionExamples
   *
   * @tparam S  the type of the returned `Future`
   * @param f   the function which will be applied to the successful result of this `Future`
   * @return    a `Future` which will be completed with the result of the application of the function
   * @group Transformations
   */
  def flatMap[S](f: T => Future[S])(implicit executor: ExecutionContext): Future[S] = transformWith {
    t =>
      if(t.isInstanceOf[Success[T]]) f(t.asInstanceOf[Success[T]].value)
      else this.asInstanceOf[Future[S]] // Safe cast
  }

  /** Creates a new future with one level of nesting flattened, this method is equivalent
   *  to `flatMap(identity)`.
   *
   * @tparam S  the type of the returned `Future`
   * @group Transformations
   */
  def flatten[S](implicit ev: T <:< Future[S]): Future[S] = flatMap(ev)(parasitic)

  /** Creates a new future by filtering the value of the current future with a predicate.
   *
   *  If the current future contains a value which satisfies the predicate, the new future will also hold that value.
   *  Otherwise, the resulting future will fail with a `NoSuchElementException`.
   *
   *  If the current future fails, then the resulting future also fails.
   *
   *  Example:
   *  {{{
   *  val f = Future { 5 }
   *  val g = f filter { _ % 2 == 1 }
   *  val h = f filter { _ % 2 == 0 }
   *  g foreach println // Eventually prints 5
   *  Await.result(h, Duration.Zero) // throw a NoSuchElementException
   *  }}}
   *
   * @param p   the predicate to apply to the successful result of this `Future`
   * @return    a `Future` which will hold the successful result of this `Future` if it matches the predicate or a `NoSuchElementException`
   * @group Transformations
   */
  def filter(p: T => Boolean)(implicit executor: ExecutionContext): Future[T] =
    transform {
      t =>
        if (t.isInstanceOf[Success[T]]) {
          if (p(t.asInstanceOf[Success[T]].value)) t
          else Future.filterFailure
        } else t
    }

  /** Used by for-comprehensions.
   * @group Transformations
   */
  final def withFilter(p: T => Boolean)(implicit executor: ExecutionContext): Future[T] = filter(p)(executor)

  /** Creates a new future by mapping the value of the current future, if the given partial function is defined at that value.
   *
   *  If the current future contains a value for which the partial function is defined, the new future will also hold that value.
   *  Otherwise, the resulting future will fail with a `NoSuchElementException`.
   *
   *  If the current future fails, then the resulting future also fails.
   *
   *  Example:
   *  {{{
   *  val f = Future { -5 }
   *  val g = f collect {
   *    case x if x < 0 => -x
   *  }
   *  val h = f collect {
   *    case x if x > 0 => x * 2
   *  }
   *  g foreach println // Eventually prints 5
   *  Await.result(h, Duration.Zero) // throw a NoSuchElementException
   *  }}}
   *
   * @tparam S    the type of the returned `Future`
   * @param pf    the `PartialFunction` to apply to the successful result of this `Future`
   * @return      a `Future` holding the result of application of the `PartialFunction` or a `NoSuchElementException`
   * @group Transformations
   */
  def collect[S](pf: PartialFunction[T, S])(implicit executor: ExecutionContext): Future[S] =
    transform {
      t =>
        if (t.isInstanceOf[Success[T]])
          Success(pf.applyOrElse(t.asInstanceOf[Success[T]].value, Future.collectFailed))
        else t.asInstanceOf[Failure[S]]
    }

  /** Creates a new future that will handle any matching throwable that this
   *  future might contain. If there is no match, or if this future contains
   *  a valid result then the new future will contain the same.
   *
   *  Example:
   *
   *  {{{
   *  Future (6 / 0) recover { case e: ArithmeticException => 0 } // result: 0
   *  Future (6 / 0) recover { case e: NotFoundException   => 0 } // result: exception
   *  Future (6 / 2) recover { case e: ArithmeticException => 0 } // result: 3
   *  }}}
   *
   * @tparam U    the type of the returned `Future`
   * @param pf    the `PartialFunction` to apply if this `Future` fails
   * @return      a `Future` with the successful value of this `Future` or the result of the `PartialFunction`
   * @group Transformations
   */
  def recover[U >: T](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): Future[U] =
    transform { _ recover pf }

  /** Creates a new future that will handle any matching throwable that this
   *  future might contain by assigning it a value of another future.
   *
   *  If there is no match, or if this future contains
   *  a valid result then the new future will contain the same result.
   *
   *  Example:
   *
   *  {{{
   *  val f = Future { Int.MaxValue }
   *  Future (6 / 0) recoverWith { case e: ArithmeticException => f } // result: Int.MaxValue
   *  }}}
   *
   * @tparam U    the type of the returned `Future`
   * @param pf    the `PartialFunction` to apply if this `Future` fails
   * @return      a `Future` with the successful value of this `Future`
   *                 or the outcome of the `Future` returned by the `PartialFunction`
   * @group Transformations
   */
  def recoverWith[U >: T](pf: PartialFunction[Throwable, Future[U]])(implicit executor: ExecutionContext): Future[U] =
    transformWith {
      t =>
        if (t.isInstanceOf[Failure[T]]) {
          val result = pf.applyOrElse(t.asInstanceOf[Failure[T]].exception, Future.recoverWithFailed)
          if (result ne Future.recoverWithFailedMarker) result
          else this
        } else this
    }

  /** Zips the values of `this` and `that` future, and creates
   *  a new future holding the tuple of their results.
   *
   *  If either input future fails, the resulting future is failed with the same
   *  throwable, without waiting for the other input future to complete.
   *
   *  If the application of `f` throws a non-fatal throwable, the resulting future
   *  is failed with that throwable.
   *
   * @tparam U      the type of the other `Future`
   * @param that    the other `Future`
   * @return        a `Future` with the results of both futures or the failure of the first of them that failed
   * @group Transformations
   */
  def zip[U](that: Future[U]): Future[(T, U)] =
    zipWith(that)(Future.zipWithTuple2Fun)(parasitic)

  /** Zips the values of `this` and `that` future using a function `f`,
   *  and creates a new future holding the result.
   *
   *  If either input future fails, the resulting future is failed with the same
   *  throwable, without waiting for the other input future to complete.
   *
   *  If the application of `f` throws a non-fatal throwable, the resulting future
   *  is failed with that throwable.
   *
   * @tparam U      the type of the other `Future`
   * @tparam R      the type of the resulting `Future`
   * @param that    the other `Future`
   * @param f       the function to apply to the results of `this` and `that`
   * @return        a `Future` with the result of the application of `f` to the results of `this` and `that`
   * @group Transformations
   */
  def zipWith[U, R](that: Future[U])(f: (T, U) => R)(implicit executor: ExecutionContext): Future[R] = {
    // This is typically overriden by the implementation in DefaultPromise, which provides
    // symmetric fail-fast behavior regardless of which future fails first.
    //
    // TODO: remove this implementation and make Future#zipWith abstract
    //  when we're next willing to make a binary incompatible change
    flatMap(r1 => that.map(r2 => f(r1, r2)))(if (executor.isInstanceOf[BatchingExecutor]) executor else parasitic)
  }