package org.mybatis.scala.mapping

import org.mybatis.scala.session.Session

/** A mapped SQL UPDATE statement.
  * Basically this defines a function: (Param => Int)
  * @tparam Param Input parameter type of the apply method.
  * @version \$Revision$
  */
abstract class Update[Param : Manifest] 
  extends Statement 
  with SQLFunction1[Param,Int] {

  def parameterTypeClass = manifest[Param].runtimeClass

  /** Exceutes the SQL UPDATE Statement
    * @param param Input paramenter of the statement
    * @param s Implicit Session
    * @return number of affected rows
    */
  def apply(param : Param)(implicit s : Session) : Int =
    execute { s.update(fqi.id, param) }

}
