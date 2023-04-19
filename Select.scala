package org.mybatis.scala.mapping

import org.mybatis.scala.session.{Session, RowBounds, ResultHandlerDelegator, ResultContext}
import scala.collection.mutable._;
import java.util.{Map => JavaMap}
import scala.jdk.CollectionConverters._

/** Base class for all Select statements.
  */
sealed trait Select extends Statement {

  /** A reference to an external resultMap.
    * Result maps are the most powerful feature of MyBatis, and with a good understanding of them,
    * many difficult mapping cases can be solved.
    */
  var resultMap       : ResultMap[_] = null

  /** Any one of FORWARD_ONLY|SCROLL_SENSITIVE|SCROLL_INSENSITIVE. Default FORWARD_ONLY. */
  var resultSetType   : ResultSetType = ResultSetType.FORWARD_ONLY

  /** This is a driver hint that will attempt to cause the driver to return results in batches of rows
    * numbering in size equal to this setting. Default is unset (driver dependent).
    */
  var fetchSize       : Int = -1

  /** Setting this to true will cause the results of this statement to be cached.
    * Default: true for select statements.
    */
  var useCache        : Boolean = true

  flushCache = false

  def resultTypeClass : Class[_]

}

/** Query for a list of objects.
  *
  * == Details ==
  * This class defines a function: (=> List[Result])
  *
  * == Sample code ==
  * {{{
  *   val findAll = new SelectList[Person] {
  *     def xsql = "SELECT * FROM person ORDER BY name"
  *   }
  *
  *   // Configuration etc .. omitted ..
  *
  *   // Then use it
  *   db.readOnly {
  *     val list = findAll()
  *     ...
  *   }
  *
  * }}}
  * @tparam Result retult type
  */
abstract class SelectList[Result : Manifest] 
  extends Select 
  with SQLFunction0[Seq[Result]] {

  def parameterTypeClass = classOf[Nothing]
  def resultTypeClass = manifest[Result].runtimeClass

  def apply()(implicit s : Session) : Seq[Result] = 
    execute { s.selectList[Result](fqi.id) }

  def handle[T](callback : ResultContext[_ <: T] => Unit)(implicit s : Session) : Unit =
    execute { s.select(fqi.id, new ResultHandlerDelegator[T](callback)) }

}

/** Query for a list of objects using the input parameter.
  *
  * == Details ==
  * This class defines a function: (Param => List[Result])
  *
  * == Sample code ==
  * {{{
  *   val findByName = new SelectListBy[String,Person] {
  *     def xsql = "SELECT * FROM person WHERE name LIKE #{name}"
  *   }
  *
  *   // Configuration etc .. omitted ..
  *
  *   // Then use it
  *   db.readOnly {
  *     val list = findByName("John%")
  *     ...
  *   }
  *
  * }}}
  * @tparam Param input parameter type
  * @tparam Result retult type
  */
abstract class SelectListBy[Param : Manifest, Result : Manifest] 
  extends Select 
  with SQLFunction1[Param, Seq[Result]] {

  def parameterTypeClass = manifest[Param].runtimeClass
  def resultTypeClass = manifest[Result].runtimeClass

  def apply(param : Param)(implicit s : Session) : Seq[Result] = 
    execute { s.selectList[Param,Result](fqi.id, param) }

  def handle(param : Param, callback : ResultContext[_ <: Result] => Unit)(implicit s : Session) : Unit =
    execute { s.select(fqi.id, param, new ResultHandlerDelegator[Result](callback)) }

}

//删去结构类似代码