/** A factory of [[org.mybatis.scala.config.Configuration]] instances. */
object Configuration {

  /**
   * Creates a Configuration built from a reader.
   * @param reader Reader over a myBatis main configuration XML
   */
  def apply(reader: Reader): Configuration = {
    val builder = new XMLConfigBuilder(reader)
    new Configuration(builder.parse)
  }

  /**
   * Creates a Configuration built from a reader.
   * @param reader Reader over a myBatis main configuration XML
   * @param env Environment name
   */
  def apply(reader: Reader, env: String): Configuration = {
    val builder = new XMLConfigBuilder(reader, env)
    new Configuration(builder.parse)
  }

  /**
   * Creates a Configuration built from a reader.
   * @param reader Reader over a myBatis main configuration XML
   * @param env Environment name
   * @param properties Properties
   */
  def apply(reader: Reader, env: String, properties: Properties): Configuration = {
    val builder = new XMLConfigBuilder(reader, env, properties)
    new Configuration(builder.parse)
  }

  /**
   * Creates a Configuration built from a classpath resource.
   * @param path Classpath resource with main configuration XML
   */
  def apply(path: String): Configuration = {
    apply(Resources.getResourceAsReader(path))
  }

  /**
   * Creates a Configuration built from a classpath resource.
   * @param path Classpath resource with main configuration XML
   * @param env Environment name
   */
  def apply(path: String, env: String): Configuration = {
    apply(Resources.getResourceAsReader(path), env)
  }

  /**
   * Creates a Configuration built from a classpath resource.
   * @param path Classpath resource with main configuration XML
   * @param env Environment name
   * @param properties Properties
   */
  def apply(path: String, env: String, properties: Properties): Configuration = {
    apply(Resources.getResourceAsReader(path), env, properties)
  }

  /**
   * Creates a Configuration built from an environment
   * @param env Environment
   */
  def apply(env: Environment): Configuration = {
    new Configuration(new MBConfig(env.unwrap))
  }

  /**
   * Creates a Configuration built from a custom builder
   * @param builder Builder => Unit
   */
  def apply(builder: Builder): Configuration = builder.build()

  /** Configuration builder */
  class Builder {

    import org.mybatis.scala.mapping.JdbcType
    import org.apache.ibatis.plugin.Interceptor
    import scala.collection.mutable.ArrayBuffer
    import org.mybatis.scala.session.ExecutorType
    import scala.jdk.CollectionConverters._
    import org.apache.ibatis.mapping.Environment

    /** Reference to self. Support for operational notation. */
    protected val >> = this

    /** Mutable hidden state, discarded after construction */
    private val pre = new ArrayBuffer[ConfigElem[MBConfig]]()

    /** Mutable hidden state, discarded after construction */
    private val pos = new ArrayBuffer[ConfigElem[Configuration]]()

    /** Configuration Element */
    private abstract class ConfigElem[T] {
      val index: Int
      def set(config: T): Unit
    }

    /** Ordered deferred setter */
    private def set[A](i: Int, e: ArrayBuffer[ConfigElem[A]])(f: A => Unit): Unit = {
      e += new ConfigElem[A] {
        val index = i
        def set(c: A) = f(c)
      }
    }

    /** Builds the configuration object */
    private[Configuration] def build(): Configuration = {
      val preConfig = new MBConfig()
      pre.sortWith((a, b) => a.index < b.index).foreach(_.set(preConfig))
      val config = new Configuration(preConfig)
      pos.sortWith((a, b) => a.index < b.index).foreach(_.set(config))
      config
    }

    // Pre ====================================================================
    
    def properties(props: (String, String)*) =
      set(0, pre) { _.getVariables.asScala ++= Map(props: _*) }

    def properties(props: Properties) =
      set(1, pre) { _.getVariables.asScala ++= props.asScala }

    def properties(resource: String) =
      set(2, pre) { _.getVariables.asScala ++= Resources.getResourceAsProperties(resource).asScala }

    def propertiesFromUrl(url: String) =
      set(3, pre) { _.getVariables.asScala ++= Resources.getUrlAsProperties(url).asScala }

    def plugin(plugin: Interceptor) =
      set(4, pre) { _.addInterceptor(plugin) }

    def objectFactory(factory: ObjectFactory) =
      set(5, pre) { _.setObjectFactory(factory) }

    def objectWrapperFactory(factory: ObjectWrapperFactory) =
      set(6, pre) { _.setObjectWrapperFactory(factory) }

    // Settings ===============

    def autoMappingBehavior(behavior: AutoMappingBehavior) =
      set(8, pre) { _.setAutoMappingBehavior(behavior.unwrap) }

    def cacheSupport(enabled: Boolean) =
      set(9, pre) { _.setCacheEnabled(enabled) }

    def lazyLoadingSupport(enabled: Boolean) =
      set(10, pre) { _.setLazyLoadingEnabled(enabled) }

    def aggressiveLazyLoading(enabled: Boolean) = 
      set(11, pre) { _.setAggressiveLazyLoading(enabled) }
    
    def multipleResultSetsSupport(enabled: Boolean) = 
      set(12, pre) { _.setMultipleResultSetsEnabled(enabled) }
    
    def useColumnLabel(enabled: Boolean) = 
      set(13, pre) { _.setUseColumnLabel(enabled) }
    
    def useGeneratedKeys(enabled: Boolean) = 
      set(14, pre) { _.setUseGeneratedKeys(enabled) }
    
    def defaultExecutorType(executorType: ExecutorType) = 
      set(15, pre) { _.setDefaultExecutorType(executorType.unwrap) }
    
    //删减结构类似代码
    
    def typeHandler(handler: TypeHandler[_]) = 
      set(26, pre) { _.getTypeHandlerRegistry.register(handler) }

    // Pos ===========================================================

    def namespace(name: String)(f: (ConfigurationSpace => Unit)) =
      set(0, pos) { c => f(new ConfigurationSpace(c.configuration, name)) }

    def statements(s: Statement*) = 
      set(1, pos) { _ ++= s }
    
    def mappers(mappers: { def bind: Seq[Statement] }*) = 
      set(1, pos) { c => mappers.foreach(c ++= _) }
    
    def cacheRef(that: ConfigurationSpace) = 
      set(2, pos) { _.cacheRef(that) }
    
    def cache(
      impl: T[_ <: Cache] = DefaultCache,
      eviction: T[_ <: Cache] = Eviction.LRU,
      flushInterval: Long = -1,
      size: Int = -1,
      readWrite: Boolean = true,
      blocking : Boolean = false,
      props: Properties = null) = 
        set(2, pos) { _.cache(impl, eviction, flushInterval, size, readWrite, blocking, props) }

  }

}