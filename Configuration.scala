package org.mybatis.scala.config

import org.apache.ibatis.session.{ Configuration => MBConfig, SqlSessionFactoryBuilder }
import org.apache.ibatis.builder.xml.XMLConfigBuilder
import java.io.Reader
import org.apache.ibatis.io.Resources
import org.mybatis.scala.session.SessionManager
import java.util.Properties
import org.mybatis.scala.mapping.Statement
import org.mybatis.scala.mapping.T
import org.mybatis.scala.cache._
import org.apache.ibatis.`type`.TypeHandler

/**
 * Mybatis Configuration
 * @constructor Creates a new Configuration with a wrapped myBatis Configuration.
 * @param configuration A myBatis Configuration instance.
 */
sealed class Configuration(private val configuration: MBConfig) {

  if (configuration.getObjectFactory().getClass ==
    classOf[org.apache.ibatis.reflection.factory.DefaultObjectFactory]) {
    configuration.setObjectFactory(new DefaultObjectFactory())
  }

  if (configuration.getObjectWrapperFactory.getClass ==
      classOf[org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory]) {
    configuration.setObjectWrapperFactory(new DefaultObjectWrapperFactory())
  }

  registerCommonOptionTypeHandlers

  lazy val defaultSpace = new ConfigurationSpace(configuration, "_DEFAULT_")

  /**
   * Creates a new space of mapped statements.
   * @param name A Speca name (a.k.a. namespace)
   * @param f A configuration block.
   */
  def addSpace(name: String)(f: (ConfigurationSpace => Unit)): this.type = {
    val space = new ConfigurationSpace(configuration, name)
    f(space)
    this
  }

  /** Adds a statement to the default space */
  def +=(s: Statement) = defaultSpace += s

  /** Adds a sequence of statements to the default space */
  def ++=(ss: Seq[Statement]) = defaultSpace ++= ss

  /** Adds a mapper to the space */
  def ++=(mapper: { def bind: Seq[Statement] }) = defaultSpace ++= mapper

  /**
   * Adds cache support to this space.
   * @param impl Cache implementation class
   * @param eviction cache eviction policy (LRU,FIFO,WEAK,SOFT)
   * @param flushInterval any positive integer in milliseconds.
   *        The default is not set, thus no flush interval is used
   *           and the cache is only flushed by calls to statements.
   * @param size max number of objects that can live in the cache. Default is 1024
   * @param readWrite A read-only cache will return the same instance of the cached object to all callers.
   *        Thus such objects should not be modified.  This offers a significant performance advantage though.
   *        A read-write cache will return a copy (via serialization) of the cached object,
   *        this is slower, but safer, and thus the default is true.
   * @param props implementation specific properties.
   */
  def cache(
    impl: T[_ <: Cache] = DefaultCache,
    eviction: T[_ <: Cache] = Eviction.LRU,
    flushInterval: Long = -1,
    size: Int = -1,
    readWrite: Boolean = true,
    blocking : Boolean = false,
    props: Properties = null) =
    defaultSpace.cache(impl, eviction, flushInterval, size, readWrite, blocking, props)

  /** Reference to an external Cache */
  def cacheRef(that: ConfigurationSpace) = defaultSpace.cacheRef(that)

  /** Builds a Session Manager */
  def createPersistenceContext = {
    val builder = new SqlSessionFactoryBuilder
    new SessionManager(builder.build(configuration))
  }

  private def registerOptionTypeHandler[T <: Option[_]](h: TypeHandler[T],
           jdbcTypes: Seq[org.apache.ibatis.`type`.JdbcType]) = {
    import org.mybatis.scala.mapping.OptionTypeHandler
    val registry = configuration.getTypeHandlerRegistry
    val cls = classOf[Option[_]]
    for (jdbcType <- jdbcTypes) {
      registry.register(cls, jdbcType, h)
    }
  }

  private def registerCommonOptionTypeHandlers = {
    import org.mybatis.scala.mapping.OptionTypeHandler
    import org.mybatis.scala.mapping.TypeHandlers._
    import org.apache.ibatis.`type`._
    import org.apache.ibatis.`type`.JdbcType._
    registerOptionTypeHandler(new OptBooleanTypeHandler(), Seq(BOOLEAN, BIT))
    registerOptionTypeHandler(new OptByteTypeHandler(), Seq(TINYINT))
    registerOptionTypeHandler(new OptShortTypeHandler(), Seq(SMALLINT))
    registerOptionTypeHandler(new OptIntegerTypeHandler(), Seq(INTEGER))
    registerOptionTypeHandler(new OptFloatTypeHandler(), Seq(FLOAT))
    registerOptionTypeHandler(new OptDoubleTypeHandler(), Seq(DOUBLE))
    registerOptionTypeHandler(new OptLongTypeHandler(), Seq(BIGINT))
    registerOptionTypeHandler(new OptStringTypeHandler(), Seq(VARCHAR, CHAR))
    registerOptionTypeHandler(new OptClobTypeHandler(), Seq(CLOB, LONGVARCHAR))
    registerOptionTypeHandler(new OptNStringTypeHandler(), Seq(NVARCHAR, NCHAR))
    registerOptionTypeHandler(new OptNClobTypeHandler(), Seq(NCLOB))
    registerOptionTypeHandler(new OptBigDecimalTypeHandler(), Seq(REAL, DECIMAL, NUMERIC))
    registerOptionTypeHandler(new OptBlobTypeHandler(), Seq(BLOB, LONGVARBINARY))
    registerOptionTypeHandler(new OptDateTypeHandler(), Seq(DATE))
    registerOptionTypeHandler(new OptTimeTypeHandler(), Seq(TIME))
    registerOptionTypeHandler(new OptTimestampTypeHandler(), Seq(TIMESTAMP))
    registerOptionTypeHandler(
       new OptionTypeHandler(new UnknownTypeHandler(configuration.getTypeHandlerRegistry)), Seq(OTHER))
  }

}