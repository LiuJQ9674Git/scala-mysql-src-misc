/** Future companion object.
 *
 *  @define nonDeterministic
 *  Note: using this method yields nondeterministic dataflow programs.
 */
object Future {

  /**
   * Utilities, hoisted functions, etc.
   */

  private[concurrent] final val toBoxed = Map[Class[_], Class[_]](
    classOf[Boolean] -> classOf[java.lang.Boolean],
    classOf[Byte]    -> classOf[java.lang.Byte],
    classOf[Char]    -> classOf[java.lang.Character],
    classOf[Short]   -> classOf[java.lang.Short],
    classOf[Int]     -> classOf[java.lang.Integer],
    classOf[Long]    -> classOf[java.lang.Long],
    classOf[Float]   -> classOf[java.lang.Float],
    classOf[Double]  -> classOf[java.lang.Double],
    classOf[Unit]    -> classOf[scala.runtime.BoxedUnit]
  )

  private[this] final val _cachedId: AnyRef => AnyRef = Predef.identity _

  private[concurrent] final def id[T]: T => T = _cachedId.asInstanceOf[T => T]

  private[concurrent] final val collectFailed =
    (t: Any) => throw new NoSuchElementException(" " + t) with NoStackTrace

  private[concurrent] final val filterFailure =
    Failure[Nothing](new NoSuchElementException(" ") with NoStackTrace)

  private[this] final val failedFailure =
    Failure[Nothing](new NoSuchElementException(" ") with NoStackTrace)

  private[concurrent] final val failedFailureFuture: Future[Nothing] =
    scala.concurrent.Future.fromTry(failedFailure)

  private[this] final val _failedFun: Try[Any] => Try[Throwable] =
    v => if (v.isInstanceOf[Failure[Any]]) Success(v.asInstanceOf[Failure[Any]].exception) else failedFailure

  private[concurrent] final def failedFun[T]: Try[T] => Try[Throwable]
    = _failedFun.asInstanceOf[Try[T] => Try[Throwable]]

  private[concurrent] final val recoverWithFailedMarker: Future[Nothing] =
    scala.concurrent.Future.failed(new Throwable with NoStackTrace)

  private[concurrent] final val recoverWithFailed = (t: Throwable) => recoverWithFailedMarker

  private[this] final val _zipWithTuple2: (Any, Any) => (Any, Any) = Tuple2.apply _
  private[concurrent] final def zipWithTuple2Fun[T,U]
    = _zipWithTuple2.asInstanceOf[(T,U) => (T,U)]

  private[this] final val _addToBuilderFun: (Builder[Any, Nothing], Any) => Builder[Any, Nothing] =
                            (b: Builder[Any, Nothing], e: Any) => b += e
  private[concurrent] final def addToBuilderFun[A, M] =
    _addToBuilderFun.asInstanceOf[Function2[Builder[A, M], A, Builder[A, M]]]

  /** A Future which is never completed.
   */
  object never extends Future[Nothing] {

    @throws[TimeoutException]
    @throws[InterruptedException]
    override final def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
      import Duration.{Undefined, Inf, MinusInf}
      atMost match {
        case u if u eq Undefined => throw new IllegalArgumentException("cannot wait for Undefined period")
        case `Inf`               =>
          while(!Thread.interrupted()) {
            LockSupport.park(this)
          }
          throw new InterruptedException
        case `MinusInf`          => // Drop out
        case f: FiniteDuration if f > Duration.Zero  =>
          var now = System.nanoTime()
          val deadline = now + f.toNanos
          while((deadline - now) > 0) {
            LockSupport.parkNanos(this, deadline - now)
            if (Thread.interrupted())
              throw new InterruptedException
            now = System.nanoTime()
          }
          // Done waiting, drop out
        case _: FiniteDuration    => // Drop out if 0 or less
        case x: Duration.Infinite => throw new MatchError(x)
      }
      throw new TimeoutException(s"Future timed out after [$atMost]")
    }

    @throws[TimeoutException]
    @throws[InterruptedException]
    override final def result(atMost: Duration)(implicit permit: CanAwait): Nothing = {
      ready(atMost)
      throw new TimeoutException(s"Future timed out after [$atMost]")
    }

    override final def onComplete[U](f: Try[Nothing] => U)
        (implicit executor: ExecutionContext): Unit = ()
    override final def isCompleted: Boolean = false
    override final def value: Option[Try[Nothing]] = None
    override final def failed: Future[Throwable] = this
    override final def foreach[U](f: Nothing => U)
        (implicit executor: ExecutionContext): Unit = ()
    override final def transform[S](s: Nothing => S, f: Throwable => Throwable)
        (implicit executor: ExecutionContext): Future[S] = this
    override final def transform[S](f: Try[Nothing] => Try[S])
        (implicit executor: ExecutionContext): Future[S] = this
    override final def transformWith[S](f: Try[Nothing] => Future[S])
        (implicit executor: ExecutionContext): Future[S] = this
    override final def map[S](f: Nothing => S)
        (implicit executor: ExecutionContext): Future[S] = this
    override final def flatMap[S](f: Nothing => Future[S])
        (implicit executor: ExecutionContext): Future[S] = this
    override final def flatten[S](implicit ev: Nothing <:< Future[S]): Future[S] = this
    override final def filter(p: Nothing => Boolean)
        (implicit executor: ExecutionContext): Future[Nothing] = this
    override final def collect[S](pf:
        PartialFunction[Nothing, S])(implicit executor: ExecutionContext): Future[S] = this
    override final def recover[U >: Nothing](pf:
        PartialFunction[Throwable, U])(implicit executor: ExecutionContext): Future[U] = this
    override final def recoverWith[U >: Nothing](pf:
        PartialFunction[Throwable, Future[U]])(implicit executor: ExecutionContext): Future[U] = this
    override final def zip[U](that: Future[U]): Future[(Nothing, U)] = this
    override final def zipWith[U, R](that: Future[U])(f: (Nothing, U) => R)
     (implicit executor: ExecutionContext): Future[R] = this
    override final def fallbackTo[U >: Nothing](that: Future[U]): Future[U] = this
    override final def mapTo[S](implicit tag: ClassTag[S]): Future[S] = this
    override final def andThen[U](pf: PartialFunction[Try[Nothing], U])
     (implicit executor: ExecutionContext): Future[Nothing] = this
    override final def toString: String = "Future(<never>)"
  }

  /** A Future which is completed with the Unit value.
   */
  final val unit: Future[Unit] = fromTry(Success(()))

  /** Creates an already completed Future with the specified exception.
   *
   *  @tparam T        the type of the value in the future
   *  @param exception the non-null instance of `Throwable`
   *  @return          the newly created `Future` instance
   */
  final def failed[T](exception: Throwable): Future[T] = Promise.failed(exception).future

  /** Creates an already completed Future with the specified result.
   *
   *  @tparam T       the type of the value in the future
   *  @param result   the given successful value
   *  @return         the newly created `Future` instance
   */
  final def successful[T](result: T): Future[T] = Promise.successful(result).future

  /** Creates an already completed Future with the specified result or exception.
   *
   *  @tparam T       the type of the value in the `Future`
   *  @param result   the result of the returned `Future` instance
   *  @return         the newly created `Future` instance
   */
  final def fromTry[T](result: Try[T]): Future[T] = Promise.fromTry(result).future

  /** Starts an asynchronous computation and returns a `Future` instance with the result of that computation.
  *
  *  The following expressions are equivalent:
  *
  *  {{{
  *  val f1 = Future(expr)
  *  val f2 = Future.unit.map(_ => expr)
  *  val f3 = Future.unit.transform(_ => Success(expr))
  *  }}}
  *
  *  The result becomes available once the asynchronous computation is completed.
  *
  *  @tparam T        the type of the result
  *  @param body      the asynchronous computation
  *  @param executor  the execution context on which the future is run
  *  @return          the `Future` holding the result of the computation
  */
  final def apply[T](body: => T)(implicit executor: ExecutionContext): Future[T] =
    unit.map(_ => body)

  /** Starts an asynchronous computation and
  *   returns a `Future` instance with the result of that computation once it completes.
  *
  *  The following expressions are semantically equivalent:
  *
  *  {{{
  *  val f1 = Future(expr).flatten
  *  val f2 = Future.delegate(expr)
  *  val f3 = Future.unit.flatMap(_ => expr)
  *  }}}
  *
  *  The result becomes available once the resulting Future of the asynchronous computation is completed.
  *
  *  @tparam T        the type of the result
  *  @param body      the asynchronous computation, returning a Future
  *  @param executor  the execution context on which the `body` is evaluated in
  *  @return          the `Future` holding the result of the computation
  */
  final def delegate[T](body: => Future[T])(implicit executor: ExecutionContext): Future[T] =
    unit.flatMap(_ => body)

  /** Simple version of `Future.traverse`.
  *         Asynchronously and non-blockingly transforms, in essence, a `IterableOnce[Future[A]]`
   *  into a `Future[IterableOnce[A]]`. Useful for reducing many `Future`s into a single `Future`.
   *
   * @tparam A        the type of the value inside the Futures
   * @tparam CC       the type of the `IterableOnce` of Futures
   * @tparam To       the type of the resulting collection
   * @param in        the `IterableOnce` of Futures which will be sequenced
   * @return          the `Future` of the resulting collection
   */
  final def sequence[A, CC[X] <: IterableOnce[X], To](in: CC[Future[A]])
    (implicit bf: BuildFrom[CC[Future[A]], A, To], executor: ExecutionContext): Future[To] =
    in.iterator.foldLeft(successful(bf.newBuilder(in))) {
      (fr, fa) => fr.zipWith(fa)(Future.addToBuilderFun)
    }.map(_.result())(if (executor.isInstanceOf[BatchingExecutor]) executor else parasitic)

  /** Asynchronously and non-blockingly returns a new `Future` to the result of the first future
   *  in the list that is completed. This means no matter if it is completed as a success or as a failure.
   *
   * @tparam T        the type of the value in the future
   * @param futures   the `IterableOnce` of Futures in which to find the first completed
   * @return          the `Future` holding the result of the future that is first to be completed
   */
  final def firstCompletedOf[T](futures: IterableOnce[Future[T]])
  (implicit executor: ExecutionContext): Future[T] = {
    val i = futures.iterator
    if (!i.hasNext) Future.never
    else {
      val p = Promise[T]()
      val firstCompleteHandler = new AtomicReference[Promise[T]](p) with (Try[T] => Unit) {
        override final def apply(v1: Try[T]): Unit =  {
          val r = getAndSet(null)
          if (r ne null)
            r tryComplete v1 // tryComplete is likely to be cheaper than complete
        }
      }
      while(i.hasNext && firstCompleteHandler.get != null) // exit early if possible
        i.next().onComplete(firstCompleteHandler)
      p.future
    }
  }

  /** Asynchronously and non-blockingly returns a `Future` that will hold the optional result
   *  of the first `Future` with a result that matches the predicate, failed `Future`s will be ignored.
   *
   * @tparam T        the type of the value in the future
   * @param futures   the `scala.collection.immutable.Iterable` of Futures to search
   * @param p         the predicate which indicates if it's a match
   * @return          the `Future` holding the optional result of the search
   */
  final def find[T](futures: scala.collection.immutable.Iterable[Future[T]])(p: T => Boolean)
    (implicit executor: ExecutionContext): Future[Option[T]] = {
    def searchNext(i: Iterator[Future[T]]): Future[Option[T]] =
      if (!i.hasNext) successful(None)
      else i.next().transformWith {
             case Success(r) if p(r) => successful(Some(r))
             case _ => searchNext(i)
           }

    searchNext(futures.iterator)
  }

  /** A non-blocking, asynchronous left fold over the specified futures,
   *  with the start value of the given zero.
   *  The fold is performed asynchronously in left-to-right order as the futures become completed.
   *  The result will be the first failure of any of the futures, or any failure in the actual fold,
   *  or the result of the fold.
   *
   *  Example:
   *  {{{
   *    val futureSum = Future.foldLeft(futures)(0)(_ + _)
   *  }}}
   *
   * @tparam T       the type of the value of the input Futures
   * @tparam R       the type of the value of the returned `Future`
   * @param futures  the `scala.collection.immutable.Iterable` of Futures to be folded
   * @param zero     the start value of the fold
   * @param op       the fold operation to be applied to the zero and futures
   * @return         the `Future` holding the result of the fold
   */
  final def foldLeft[T, R](futures: scala.collection.immutable.Iterable[Future[T]])
       (zero: R)(op: (R, T) => R)(implicit executor: ExecutionContext): Future[R] =
    foldNext(futures.iterator, zero, op)

  private[this] final def foldNext[T, R](i: Iterator[Future[T]], prevValue: R, op: (R, T) => R)
                             (implicit executor: ExecutionContext): Future[R] =
    if (!i.hasNext) successful(prevValue)
    else i.next().flatMap { value => foldNext(i, op(prevValue, value), op) }

  /** A non-blocking, asynchronous fold over the specified futures, with the start value of the given zero.
   *  The fold is performed on the thread where the last future is completed,
   *  the result will be the first failure of any of the futures, or any failure in the actual fold,
   *  or the result of the fold.
   *
   *  Example:
   *  {{{
   *    val futureSum = Future.fold(futures)(0)(_ + _)
   *  }}}
   *
   * @tparam T       the type of the value of the input Futures
   * @tparam R       the type of the value of the returned `Future`
   * @param futures  the `IterableOnce` of Futures to be folded
   * @param zero     the start value of the fold
   * @param op       the fold operation to be applied to the zero and futures
   * @return         the `Future` holding the result of the fold
   */
  @deprecated("use Future.foldLeft instead", "2.12.0")
  // not removed in 2.13, to facilitate 2.11/2.12/2.13 cross-building;
  //  remove further down the line (see scala/scala#6319)
  def fold[T, R](futures: IterableOnce[Future[T]])
        (zero: R)(@deprecatedName("foldFun") op: (R, T) => R)
        (implicit executor: ExecutionContext): Future[R] =
    if (futures.isEmpty) successful(zero)
    else sequence(futures)(ArrayBuffer, executor).map(_.foldLeft(zero)(op))

  /** Initiates a non-blocking, asynchronous, fold over the supplied futures
   *  where the fold-zero is the result value of the first `Future` in the collection.
   *
   *  Example:
   *  {{{
   *    val futureSum = Future.reduce(futures)(_ + _)
   *  }}}
   * @tparam T       the type of the value of the input Futures
   * @tparam R       the type of the value of the returned `Future`
   * @param futures  the `IterableOnce` of Futures to be reduced
   * @param op       the reduce operation which is applied to the results of the futures
   * @return         the `Future` holding the result of the reduce
   */
  @deprecated("use Future.reduceLeft instead", "2.12.0")
  // not removed in 2.13, to facilitate 2.11/2.12/2.13 cross-building;
  // remove further down the line (see scala/scala#6319)
  final def reduce[T, R >: T](futures: IterableOnce[Future[T]])(op: (R, T) => R)
    (implicit executor: ExecutionContext): Future[R] =
    if (futures.isEmpty) failed(new NoSuchElementException("reduce attempted on empty collection"))
    else sequence(futures)(ArrayBuffer, executor).map(_ reduceLeft op)

  /** Initiates a non-blocking, asynchronous, left reduction over the supplied futures
   *  where the zero is the result value of the first `Future`.
   *
   *  Example:
   *  {{{
   *    val futureSum = Future.reduceLeft(futures)(_ + _)
   *  }}}
   * @tparam T       the type of the value of the input Futures
   * @tparam R       the type of the value of the returned `Future`
   * @param futures  the `scala.collection.immutable.Iterable` of Futures to be reduced
   * @param op       the reduce operation which is applied to the results of the futures
   * @return         the `Future` holding the result of the reduce
   */
  final def reduceLeft[T, R >: T](futures: scala.collection.immutable.Iterable[Future[T]])
     (op: (R, T) => R)(implicit executor: ExecutionContext): Future[R] = {
    val i = futures.iterator
    if (!i.hasNext) failed(new NoSuchElementException("reduceLeft attempted on empty collection"))
    else i.next() flatMap { v => foldNext(i, v, op) }
  }

  /** Asynchronously and non-blockingly transforms a `IterableOnce[A]` into a `Future[IterableOnce[B]]`
   *  using the provided function `A => Future[B]`.
   *  This is useful for performing a parallel map. For example, to apply a function to all items of a list
   *  in parallel:
   *
   *  {{{
   *    val myFutureList = Future.traverse(myList)(x => Future(myFunc(x)))
   *  }}}
   * @tparam A        the type of the value inside the Futures in the collection
   * @tparam B        the type of the value of the returned `Future`
   * @tparam M        the type of the collection of Futures
   * @param in        the collection to be mapped over with the provided function
   *                      to produce a collection of Futures that is then sequenced into a Future collection
   * @param fn        the function to be mapped over the collection to produce a collection of Futures
   * @return          the `Future` of the collection of results
   */
  final def traverse[A, B, M[X] <: IterableOnce[X]](in: M[A])
       (fn: A => Future[B])
       (implicit bf: BuildFrom[M[A], B, M[B]], executor: ExecutionContext): Future[M[B]] =
    in.iterator.foldLeft(successful(bf.newBuilder(in))) {
      (fr, a) => fr.zipWith(fn(a))(Future.addToBuilderFun)
    }.map(_.result())(if (executor.isInstanceOf[BatchingExecutor]) executor else parasitic)
}

@deprecated("Superseded by `scala.concurrent.Batchable`", "2.13.0")
trait OnCompleteRunnable extends Batchable {
  self: Runnable =>
}