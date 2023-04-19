package org.mybatis.scala.session

import org.apache.ibatis.logging.LogFactory
import SessionManager.log
import org.apache.ibatis.session.{SqlSession, SqlSessionFactory}

/** Session lifecycle manager.
  * Manages the lifecycle of the Session
  * == Usage ==
  *  - Rollback only
  *    {{{
  *    sessionManager.readOnly { implicit session =>
  *       // Your code ...
  *       // Always rollback at the end automatically.
  *    }
  *    }}}
  *  - Direct transaction
  *    {{{
  *    sessionManager.transaction { implicit session =>
  *       // Your code ...
  *       // Always commit at the end if no exceptions are thrown, else rollback.
  *    }
  *    }}}
  *  - External transaction
  *    {{{
  *    sessionManager.managed { implicit session =>
  *       // Your code ...
  *       // Never commit or rollback automatically.
  *       // The transaction can be managed externally or manually.
  *    }
  *    }}}
  *
  * @version \$Revision$
  */
sealed class SessionManager(factory : SqlSessionFactory) {

  type Callback[T] = (Session) => T
  type CloseSessionHook = (SqlSession) => Unit

  private var closeSession : CloseSessionHook = {(s : SqlSession) => s.close}

  def closeSessionHook(hook : CloseSessionHook) = {
    closeSession = hook
  }

  /** Executes the callback within a new session and rollback at the end. */
  def readOnly[T](
    executorType : ExecutorType = ExecutorType.SIMPLE,
    level: TransactionIsolationLevel = TransactionIsolationLevel.UNDEFINED)(callback : Callback[T]) : T = {
    val sqlSession = factory.openSession(executorType.unwrap, level.unwrap)
    try {
      val ret = callback(new Session(sqlSession))
      sqlSession.rollback
      ret
    }
    finally {
      closeSession(sqlSession)
    }
  }  
  
  /** Executes the callback within a new session and rollback at the end. */
  def readOnly[T](callback : Callback[T]) : T = readOnly[T]()(callback)

  private def transaction[T](sqlSession : SqlSession)(callback : Callback[T]) : T = {
    try {
      val t = callback(new Session(sqlSession))
      sqlSession.commit
      t
    }
    catch {
      case e : Throwable =>
        try {
          sqlSession.rollback
        }
        catch {
          case e2 : Exception =>
            // Ignore.  There's nothing that can be done at this point.
            // Throw the original exception as that's the one that matters.
            log.warn("Unexpected exception on rolling back transaction.  Cause: " + e2)
            throw e
        }
        throw e
    }
    finally {
      closeSession(sqlSession)
    }
  }

  /** Executes the callback within a new transaction and commit at the end, automatically calls rollback if any exception. */
  def transaction[T](executorType : ExecutorType, level : TransactionIsolationLevel)(callback : Callback[T]) : T = 
    transaction[T](factory.openSession(executorType.unwrap, level.unwrap))(callback)

  /** Executes the callback within a new transaction and commit at the end, automatically calls rollback if any exception. */
  def transaction[T](executorType : ExecutorType)(callback : Callback[T]) : T = 
    transaction[T](factory.openSession(executorType.unwrap))(callback)

  /** Executes the callback within a new transaction and commit at the end, automatically calls rollback if any exception. */
  def transaction[T](level : TransactionIsolationLevel)(callback : Callback[T]) : T = 
    transaction[T](factory.openSession(level.unwrap))(callback)

  /** Executes the callback within a new transaction and commit at the end, automatically calls rollback if any exception. */
  def transaction[T](executorType : ExecutorType, autoCommit : Boolean)(callback : Callback[T]) : T = 
    transaction[T](factory.openSession(executorType.unwrap, autoCommit))(callback)

  /** Executes the callback within a new transaction and commit at the end, automatically calls rollback if any exception. */
  def transaction[T](autoCommit : Boolean)(callback : Callback[T]) : T = 
    transaction[T](factory.openSession(autoCommit))(callback)

  /** Executes the callback within a new transaction and commit at the end, automatically calls rollback if any exception. */
  def transaction[T](callback : Callback[T]) : T = 
    transaction[T](ExecutorType.SIMPLE, TransactionIsolationLevel.UNDEFINED)(callback)

  /** Executes the callback within a new session. Does not call any transaction method. */
  def managed[T](executorType : ExecutorType)(callback : Callback[T]) : T = {
    val sqlSession = factory.openSession(executorType.unwrap)
    try {
      callback(new Session(sqlSession))
    }
    finally {
      closeSession(sqlSession)
    }
  }

  /** Executes the callback within a new session. Does not call any transaction method. */
  def managed[T](callback : Callback[T]) : T = managed[T](ExecutorType.SIMPLE)(callback)
  
}

object SessionManager {
  val log = LogFactory.getLog(classOf[SessionManager])
}