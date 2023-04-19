package org.mybatis.scala.mapping

import org.mybatis.scala.session.Session

/** A mapped SQL INSERT statement.
  * Basically this defines a function: (Param => Int)
  * @tparam Param Input parameter type of the apply method.
  * @version \$Revision$
  */
abstract class Insert[Param : Manifest] 
  extends Statement
     with SQLFunction1[Param,Int] {

  /** Key Generator used to retrieve database generated keys.
    * Defaults to null
    */
  var keyGenerator : KeyGenerator = null //JdbcGeneratedKey(null, "id")

  def parameterTypeClass = manifest[Param].runtimeClass

  /** Exceutes the SQL INSERT Statement
    * @param param Input paramenter of the statement
    * @param s Implicit Session
    * @return number of affected rows
    */
  def apply(param : Param)(implicit s : Session) : Int =
    execute { s.insert(fqi.id, param) }

}
