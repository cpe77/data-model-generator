package com.datawizards.dmg

import com.datawizards.dmg.dialects.Dialect
import com.datawizards.dmg.metadata._
import org.apache.log4j.Logger

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

object DataModelGenerator {

  private val log = Logger.getLogger(getClass.getName)

  /**
    * Generate data model for provided class
    *
    * @param dialect DB dialect e.g. H2, Hive, Redshift, Avro schema, Elasticsearch maping
    * @tparam T type for which generate data model
    */
  def generate[T: ClassTag: TypeTag](dialect: Dialect): String = {
    val ct = implicitly[ClassTag[T]].runtimeClass
    log.info(s"Generating model for class: [${ct.getName}], dialect: [$dialect]")
    generateDataModel(dialect, getClassMetaData[T](dialect))
  }

  private def getClassMetaData[T: ClassTag: TypeTag](dialect: Dialect): ClassTypeMetaData =
    changeName(dialect, MetaDataExtractor.extractClassMetaData[T]())

  private def changeName(dialect: Dialect, c: ClassTypeMetaData): ClassTypeMetaData =
    c.copy(
      typeName = getClassName(dialect, c),
      fields = c.fields.map(f => changeName(dialect, f, c))
    )

  private def changeName(dialect: Dialect, classFieldMetaData: ClassFieldMetaData, classTypeMetaData: ClassTypeMetaData): ClassFieldMetaData =
    classFieldMetaData.copy(
      fieldName = getFieldName(dialect, classFieldMetaData, classTypeMetaData),
      fieldType = changeName(dialect, classFieldMetaData.fieldType)
    )

  private def changeName(dialect: Dialect, typeMetaData: TypeMetaData): TypeMetaData = typeMetaData match {
    case p:PrimitiveTypeMetaData => p
    case c:CollectionTypeMetaData => c
    case m:MapTypeMetaData => m
    case c:ClassTypeMetaData => changeName(dialect, c)
  }

  private val Table = "com.datawizards.dmg.annotations.table"
  private val Column = "com.datawizards.dmg.annotations.column"
  private val Underscore = "com.datawizards.dmg.annotations.underscore"

  private def getClassName(dialect: Dialect, classMetaData: ClassTypeMetaData): String = {
    val tableAnnotations = classMetaData.annotations.filter(_.name == Table)
    if(tableAnnotations.nonEmpty) {
      val dialectSpecificTableAnnotation = tableAnnotations.find(_.attributes.exists(aa => aa.name == "dialect" && aa.value.contains(dialect.toString.replace("Dialect",""))))
      if(dialectSpecificTableAnnotation.isDefined)
        dialectSpecificTableAnnotation.get.attributes.filter(_.name == "name").head.value
      else {
        val defaultTableAnnotation = tableAnnotations.find(!_.attributes.exists(aa => aa.name == "dialect"))
        if(defaultTableAnnotation.isDefined)
          defaultTableAnnotation.get.attributes.filter(_.name == "name").head.value
        else
          convertToUnderscoreIfRequired(classMetaData.typeName, dialect, classMetaData)
      }
    }
    else
      convertToUnderscoreIfRequired(classMetaData.typeName, dialect, classMetaData)
  }

  private def getFieldName(dialect: Dialect, classFieldMetaData: ClassFieldMetaData, classTypeMetaData: ClassTypeMetaData): String = {
    val columnAnnotations = classFieldMetaData.annotations.filter(_.name == Column)
    if(columnAnnotations.nonEmpty) {
      val dialectSpecificColumnAnnotation = columnAnnotations.find(_.attributes.exists(aa => aa.name == "dialect" && aa.value.contains(dialect.toString.replace("Dialect",""))))
      if(dialectSpecificColumnAnnotation.isDefined)
        dialectSpecificColumnAnnotation.get.attributes.filter(_.name == "name").head.value
      else {
        val defaultColumnAnnotation = columnAnnotations.find(!_.attributes.exists(aa => aa.name == "dialect"))
        if(defaultColumnAnnotation.isDefined)
          defaultColumnAnnotation.get.attributes.filter(_.name == "name").head.value
        else
          convertToUnderscoreIfRequired(classFieldMetaData.fieldName, dialect, classTypeMetaData)
      }
    }
    else
      convertToUnderscoreIfRequired(classFieldMetaData.fieldName, dialect, classTypeMetaData)
  }

  private def convertToUnderscoreIfRequired(name: String, dialect: Dialect, classTypeMetaData: ClassTypeMetaData): String = {
    val underscoreAnnotations = classTypeMetaData.annotations.filter(_.name == Underscore)
    if(underscoreAnnotations.nonEmpty) {
      val dialectSpecificUnderscoreAnnotation = underscoreAnnotations.find(_.attributes.exists(aa => aa.name == "dialect" && aa.value.contains(dialect.toString.replace("Dialect",""))))
      if(dialectSpecificUnderscoreAnnotation.isDefined)
        name.replaceAll("(.)(\\p{Upper})","$1_$2").toLowerCase
      else
        name
    }
    else
      name
  }


  private def generateDataModel(dialect: Dialect, classTypeMetaData: ClassTypeMetaData): String = {
    dialect.generateDataModel(classTypeMetaData, generateFieldsExpressions(dialect, classTypeMetaData))
  }

  private def generateFieldsExpressions(dialect: Dialect, classTypeMetaData: ClassTypeMetaData): Iterable[String] = {
    classTypeMetaData
      .fields
      .withFilter(f => dialect.generateColumn(f))
      .map(f => dialect.generateClassFieldExpression(f))
  }

}
