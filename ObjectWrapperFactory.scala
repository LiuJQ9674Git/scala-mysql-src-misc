package org.mybatis.scala.config

import org.apache.ibatis.reflection.MetaObject
import org.apache.ibatis.reflection.property.PropertyTokenizer
import org.apache.ibatis.reflection.factory.{ObjectFactory => XObjectFactory}

abstract class CollectionObjectWrapper extends org.apache.ibatis.reflection.wrapper.ObjectWrapper {
  def get(prop : PropertyTokenizer) : AnyRef = null
  def set(prop : PropertyTokenizer, value : AnyRef) : Unit = {}
  def findProperty(name : String, useCamelCaseMapping : Boolean) : String = null
  def getGetterNames() : Array[String] = null
  def getSetterNames() : Array[String] = null
  def getSetterType(name : String) : Class[_] = null
  def getGetterType(name : String) : Class[_] = null
  def hasSetter(name : String) : Boolean = false
  def hasGetter(name : String) : Boolean = false
  def instantiatePropertyValue(name : String, prop : PropertyTokenizer, objectFactory : XObjectFactory) : MetaObject = null
  def isCollection() : Boolean = true
}

class ArrayBufferWrapper(buffer : scala.collection.mutable.ArrayBuffer[AnyRef]) extends CollectionObjectWrapper {
  import scala.jdk.CollectionConverters._
  def add(element : AnyRef) : Unit = buffer append element
  def addAll[E](elements : java.util.List[E]) : Unit = buffer.addAll(elements.asInstanceOf[java.util.Collection[AnyRef]].asScala)
}

class HashSetWrapper(set : scala.collection.mutable.HashSet[AnyRef]) extends CollectionObjectWrapper {
  import scala.jdk.CollectionConverters._
  def add(element : AnyRef) : Unit = set add element  
  def addAll[E](elements : java.util.List[E]) : Unit = set.addAll(elements.asInstanceOf[java.util.Collection[AnyRef]].asScala)
}

class DefaultObjectWrapperFactory extends ObjectWrapperFactory {
  def hasWrapperFor(obj : AnyRef) : Boolean = obj match {
    case o : scala.collection.mutable.ArrayBuffer[_] => true
    case o : scala.collection.mutable.HashSet[_] => true
    case _ => false
  }
  def getWrapperFor(metaObject : MetaObject, obj : AnyRef) : org.apache.ibatis.reflection.wrapper.ObjectWrapper = obj match {
    case o : scala.collection.mutable.ArrayBuffer[_] => new ArrayBufferWrapper(o.asInstanceOf[scala.collection.mutable.ArrayBuffer[AnyRef]])
    case o : scala.collection.mutable.HashSet[_] => new HashSetWrapper(o.asInstanceOf[scala.collection.mutable.HashSet[AnyRef]])
    case _ => 
      throw new IllegalArgumentException("Type not supported: " + obj.getClass.getSimpleName)
  }
}