package org.mybatis.scala.samples.select

import org.mybatis.scala.mapping._
import org.mybatis.scala.config._
import org.mybatis.scala.session._
import org.mybatis.scala.samples.util._

// Model beans =================================================================

class Person {
  var id : Int = _
  var firstName : String = _
  var lastName : String = _
}

// Data access layer ===========================================================

object DB {

  // Simple select function
  val findAll = new SelectListBy[String,Person] {
    def xsql =
      <xsql>
        <bind name="pattern" value="'%' + _parameter + '%'" />
        SELECT
          id_ as id,
          first_name_ as firstName,
          last_name_ as lastName
        FROM
          person
        WHERE
          first_name_ LIKE #{{pattern}}
      </xsql>
  }

  // Datasource configuration
  val config = Configuration(
    Environment(
      "default", 
      new JdbcTransactionFactory(), 
      new PooledDataSource(
        "org.hsqldb.jdbcDriver",
        "jdbc:hsqldb:mem:scala",
        "sa",
        ""
      )
    )
  )

  // Add the data access method to the default namespace
  config += findAll
  config ++= DBSchema
  config ++= DBSampleData

  // Build the session manager
  lazy val context = config.createPersistenceContext
  
}

// Application code ============================================================

object SelectSample {

  // Do the Magic ...
  def main(args : Array[String]) : Unit = {
    DB.context.transaction { implicit session =>

      DBSchema.create
      DBSampleData.populate

      DB.findAll("a").foreach { p => 
        println( "Person(%d): %s %s".format(p.id, p.firstName, p.lastName) )
      }
      
    }
  }

}
