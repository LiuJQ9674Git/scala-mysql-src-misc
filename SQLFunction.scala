package org.mybatis.scala.mapping

import org.mybatis.scala.session.Session
import org.mybatis.scala.session.ResultContext

trait SQLFunction0[+B] {
  def apply()(implicit s : Session) : B
}

trait SQLFunction1[-A, +B] {
  def apply(a : A)(implicit s : Session) : B
}

trait SQLFunction2[-A, -B, +C] {
  def apply(a : A, b : B)(implicit s : Session) : C
}
