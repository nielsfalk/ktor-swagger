package de.nielsfalk.ktor.swagger

import com.winterbe.expekt.should
import de.nielsfalk.ktor.swagger.version.shared.Property
import de.nielsfalk.ktor.swagger.version.v2.Parameter
import io.ktor.client.call.TypeInfo
import io.ktor.client.call.typeInfo
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import de.nielsfalk.ktor.swagger.version.v3.Parameter as ParameterV3

class ModelExtractionTest {
    companion object {
        const val annotationDescription = "This field has a default"
    }

    private val variation = SwaggerSupport.swaggerVariation

    private val openApiVariation = SwaggerSupport.openApiVariation

    private fun createModelData(typeInfo: TypeInfo) =
        variation.createModelData(typeInfo)

    private inline fun <reified T> createAndExtractObjectModelData() =
        createModelData(typeInfo<T>()).first as ObjectModel

    enum class EnumClass {
        first, second, third
    }

    class EnumModel(val enumValue: EnumClass?)

    @Test
    fun `enum Property`() {

        val property = createAndExtractObjectModelData<EnumModel>()
            .properties["enumValue"] as Property

        property.type.should.equal("string")
        property.enum.should.contain.elements("first", "second", "third")
    }

    class InstantModel(val timestamp: Instant?)

    @Test
    fun `instant Property`() {
        val property = createAndExtractObjectModelData<InstantModel>()
            .properties["timestamp"] as Property

        property.type.should.equal("string")
        property.format.should.equal("date-time")
    }

    class LocalDateModel(val birthDate: LocalDate?)

    @Test
    fun `localDate Property`() {
        val property = createAndExtractObjectModelData<LocalDateModel>()
            .properties["birthDate"] as Property

        property.type.should.equal("string")
        property.format.should.equal("date")
    }

    class LongModel(val long: Long?)

    @Test
    fun `long Property`() {
        val property = createAndExtractObjectModelData<LongModel>()
            .properties["long"] as Property

        property.type.should.equal("integer")
        property.format.should.equal("int64")
    }

    class DoubleModel(val double: Double?)

    @Test
    fun `double Property`() {
        val property = createAndExtractObjectModelData<DoubleModel>()
            .properties["double"] as Property

        property.type.should.equal("number")
        property.format.should.equal("double")
    }

    class PropertyModel
    class ModelProperty(val something: PropertyModel?)

    @Test
    fun `reference model property`() {
        val modelWithDiscovered =
            createModelData(typeInfo<ModelProperty>())
        val property = (modelWithDiscovered.first as ObjectModel).properties["something"] as Property

        property.`$ref`.should.equal("#/definitions/PropertyModel")
        assertEqualTypeInfo(
            typeInfo<PropertyModel>(),
            modelWithDiscovered.second.first()
        )
    }

    class ModelStringArray(val something: List<String>)

    @Test
    fun `string array`() {

        val property = createAndExtractObjectModelData<ModelStringArray>()
            .properties["something"] as Property

        property.type.should.equal("array")
        property.items?.type.should.equal("string")
    }

    class SubModelElement(val somethingElse: String)

    class ModelWithGenericList<T>(val something: List<T>)

    @Test
    fun `reified generic list types`() {
        val modelWithDiscovered =
            createModelData(typeInfo<ModelWithGenericList<SubModelElement>>())
        val property = (modelWithDiscovered.first as ObjectModel).properties["something"]!!

        property.type.should.equal("array")
        property.items?.`$ref`.should.equal("#/definitions/SubModelElement")

        assertEqualTypeInfo(
            typeInfo<SubModelElement>(),
            modelWithDiscovered.second.first()
        )
    }

    @Test
    fun `top level reified generic list types`() {
        val modelWithDiscovered = createModelData(typeInfo<List<SubModelElement>>())
        val model = modelWithDiscovered.first as ArrayModel

        model.items.`$ref`.should.equal("#/definitions/SubModelElement")
        model.uniqueItems.should.equal(false)
        assertEqualTypeInfo(
            typeInfo<SubModelElement>(),
            modelWithDiscovered.second.first()
        )
    }

    class ModelWithElementToIgnore(val returnMe: String, @Ignore val ignoreMe: String)

    @Test
    fun `model with property to ignore`() {
        val model =
                createModelData(typeInfo<ModelWithElementToIgnore>())
        val returnedProperty = (model.first as ObjectModel).properties["returnMe"]!!

        returnedProperty.type.should.equal("string")
        (model.first as ObjectModel).properties["ignoreMe"].should.equal(null)
    }

    class ModelWithGenericSet<T>(val something: Set<T>)

    @Test
    fun `reified generic set types`() {
        val modelWithDiscovered =
            createModelData(typeInfo<ModelWithGenericSet<SubModelElement>>())
        val property = (modelWithDiscovered.first as ObjectModel).properties["something"]!!

        property.type.should.equal("array")
        property.items?.`$ref`.should.equal("#/definitions/SubModelElement")

        assertEqualTypeInfo(
            typeInfo<SubModelElement>(),
            modelWithDiscovered.second.first()
        )
    }

    @Test
    fun `top level reified generic set types`() {
        val modelWithDiscovered = createModelData(typeInfo<Set<SubModelElement>>())
        val model = modelWithDiscovered.first as ArrayModel

        model.items.`$ref`.should.equal("#/definitions/SubModelElement")
        model.uniqueItems.should.equal(true)
        assertEqualTypeInfo(
            typeInfo<SubModelElement>(),
            modelWithDiscovered.second.first()
        )
    }

    @Test
    fun `nested reified generic set types`() {
        val modelWithDiscovered =
            createModelData(typeInfo<ModelWithGenericSet<ModelWithGenericSet<String>>>())

        val property = (modelWithDiscovered.first as ObjectModel).properties["something"]!!

        property.type.should.equal("array")
        property.items?.`$ref`.should.equal("#/definitions/ModelWithGenericSetOfString")

        assertEqualTypeInfo(
            typeInfo<ModelWithGenericSet<String>>(),
            modelWithDiscovered.second.first()
        )
    }

    class ModelNestedGenericList<T>(val somethingNested: List<List<T>>)

    @Test
    fun `reified generic nested in list type`() {
        val modelWithDiscovered =
            createModelData(typeInfo<ModelNestedGenericList<SubModelElement>>())

        val property = (modelWithDiscovered.first as ObjectModel).properties["somethingNested"]!!
        property.type.should.equal("array")
        property.items?.type.should.equal("array")
        property.items?.items?.`$ref`.should.equal("#/definitions/SubModelElement")

        assertEqualTypeInfo(
            typeInfo<SubModelElement>(),
            modelWithDiscovered.second.first()
        )
    }

    @Test
    fun `name of triply nested generic type`() {
        val tripleNestedTypeInfo =
            typeInfo<ModelNestedGenericList<ModelNestedGenericList<ModelNestedGenericList<SubModelElement>>>>()
        tripleNestedTypeInfo.modelName()
            .should.equal("ModelNestedGenericListOfModelNestedGenericListOfModelNestedGenericListOfSubModelElement")
    }

    @Test
    fun `triply nested generic list type`() {
        val tripleNestedTypeInfo =
            typeInfo<ModelNestedGenericList<ModelNestedGenericList<ModelNestedGenericList<SubModelElement>>>>()
        val modelWithDiscovered = createModelData(tripleNestedTypeInfo)

        val expectedType = typeInfo<ModelNestedGenericList<ModelNestedGenericList<SubModelElement>>>()
        assertEqualTypeInfo(expectedType, modelWithDiscovered.second.first())

        val property = (modelWithDiscovered.first as ObjectModel).properties["somethingNested"]!!
        property.type.should.equal("array")
        property.items?.type.should.equal("array")
        property.items?.items?.`$ref`.should.equal("#/definitions/ModelNestedGenericListOfModelNestedGenericListOfSubModelElement")
    }

    class ModelNestedList(val somethingNested: List<List<SubModelElement>>)

    @Test
    fun `generic nested in list type`() {
        val modelWithDiscovered =
            createModelData(typeInfo<ModelNestedList>())

        val property = (modelWithDiscovered.first as ObjectModel).properties["somethingNested"]!!
        property.type.should.equal("array")
        property.items?.type.should.equal("array")
        property.items?.items?.`$ref`.should.equal("#/definitions/SubModelElement")

        assertEqualTypeInfo(
            typeInfo<SubModelElement>(),
            modelWithDiscovered.second.first()
        )
    }

    class GenericSubModel<S>(val value: S)
    class ModelWithNestedGeneric<T>(val subModelElement: GenericSubModel<T>)

    @Test
    fun `generic type extraction`() {
        val typeInfo = typeInfo<ModelWithNestedGeneric<String>>()

        val property = ModelWithNestedGeneric::class.memberProperties.first()

        val typeInfoForProperty = typeInfo<GenericSubModel<String>>()

        assertEqualTypeInfo(
            typeInfoForProperty,
            property.returnTypeInfo(typeInfo.reifiedType)
        )
    }

    @Test
    fun `generic type information extraction at top level`() {
        val typeInfo = typeInfo<GenericSubModel<String>>()

        val property = GenericSubModel::class.memberProperties.first()

        val typeInfoForProperty = typeInfo<String>()
        assertEqualTypeInfo(
            typeInfoForProperty,
            property.returnTypeInfo(typeInfo.reifiedType)
        )
    }

    @Test
    fun `when value in GenericSubModel is collection type, value is correct type`() {
        val typeInfo = typeInfo<GenericSubModel<List<String>>>()

        val modelWithDiscovered = createModelData(typeInfo)

        val property = (modelWithDiscovered.first as ObjectModel).properties["value"]!!
        property.type.should.equal("array")
        property.items?.type.should.equal("string")

        modelWithDiscovered.second.should.be.empty
    }

    @Test
    fun `extract generic sub-model element`() {
        val modelWithDiscovered =
            createModelData(typeInfo<ModelWithNestedGeneric<String>>())

        assertEqualTypeInfo(
            typeInfo<GenericSubModel<String>>(),
            modelWithDiscovered.second.first()
        )

        val property = (modelWithDiscovered.first as ObjectModel).properties["subModelElement"]!!
        property.`$ref`.should.equal("#/definitions/GenericSubModelOfString")
    }

    class GenericSubModelTwoGenerics<S1, S2>(
        val value1: S1,
        val value2: S2
    )

    class ModelWithTwoNestedGeneric<T1, T2>(val subModelElement: GenericSubModelTwoGenerics<T1, T2>)

    @Test
    fun `extract generic sub-model with two generics`() {
        val modelWithDiscovered =
            createModelData(typeInfo<ModelWithTwoNestedGeneric<String, Int>>())
        assertEqualTypeInfo(
            typeInfo<GenericSubModelTwoGenerics<String, Int>>(),
            modelWithDiscovered.second.first()
        )
        val property = (modelWithDiscovered.first as ObjectModel).properties["subModelElement"]!!
        property.`$ref`.should.equal("#/definitions/GenericSubModelTwoGenericsOfStringAndInt")
    }

    class Parameters(
        val optional: String?,
        val mandatory: String,
        @DefaultValue("true")
        @Description(annotationDescription)
        val default: Boolean = true
    )

    @Test
    fun `optional parameters`() {
        val map = variation { Parameters::class.memberProperties.map { it.toParameter("").first } }

        map.find { it.name == "optional" }!!.run {
            this as Parameter
            required.should.equal(false)
            type.should.equal("string")
        }

        map.find { it.name == "mandatory" }!!.run {
            this as Parameter
            required.should.equal(true)
            type.should.equal("string")
        }

        map.find { it.name == "default" }!!.run {
            this as Parameter
            required.should.equal(false)
            type.should.equal("boolean")
            description.should.equal(annotationDescription)
        }
    }

    @Test
    fun `openapi optional parameters`() {
        val map = openApiVariation { Parameters::class.memberProperties.map { it.toParameter("").first } }

        map.find { it.name == "optional" }!!.run {
            this as ParameterV3
            required.should.equal(false)
            schema.type.should.equal("string")
        }

        map.find { it.name == "mandatory" }!!.run {
            this as ParameterV3
            required.should.equal(true)
            schema.type.should.equal("string")
        }

        map.find { it.name == "default" }!!.run {
            this as ParameterV3
            required.should.equal(false)
            schema.type.should.equal("boolean")
            description.should.equal(annotationDescription)
        }
    }

    class TwoGenerics<J, K>(val jValue: J, val kValue: K)

    @Test
    fun `two generics passed to object`() {
        val typeInfo = typeInfo<TwoGenerics<Int, String>>()

        val properties = TwoGenerics::class.memberProperties
        val jValueType = properties.find { it.name == "jValue" }!!.returnTypeInfo(typeInfo.reifiedType)
        val kValueType = properties.find { it.name == "kValue" }!!.returnTypeInfo(typeInfo.reifiedType)

        assertEqualTypeInfo(typeInfo<Int>(), jValueType)
        assertEqualTypeInfo(typeInfo<String>(), kValueType)
    }
}

fun assertEqualTypeInfo(expected: TypeInfo, actual: TypeInfo) {
    assertEquals(expected.type, actual.type)
    assertEquals(expected.reifiedType, actual.reifiedType)
}
