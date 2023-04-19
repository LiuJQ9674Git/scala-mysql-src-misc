package org.mybatis.scala.mapping

/** Fully Qualified Identifier */
private[scala] case class FQI(spaceId : String, localId : String) {
  def resolveIn(externalSpaceId : String) : String = {
    if (externalSpaceId == spaceId)
      localId
    else
      spaceId + "." + localId
  }
  def id = spaceId + "." + localId
}
