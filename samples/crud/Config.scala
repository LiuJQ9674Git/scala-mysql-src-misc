package org.mybatis.scala.samples.crud

import org.mybatis.scala.config._

object Config {

  // Load datasource configuration
  val config = Configuration("mybatis.xml")

  // Create a configuration space, add the data access method
  config.addSpace("item") { space =>
    space ++= ItemDAO
  }

  // Build the session manager
  val persistenceContext = config.createPersistenceContext


}
