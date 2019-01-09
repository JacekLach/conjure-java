/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.conjure.java.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDelegatingDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;
import com.palantir.conjure.java.ConjureAnnotations;
import com.palantir.conjure.spec.AliasDefinition;
import com.palantir.conjure.spec.PrimitiveType;
import com.palantir.conjure.spec.Type;
import com.palantir.conjure.visitor.TypeDefinitionVisitor;
import com.palantir.conjure.visitor.TypeVisitor;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.Modifier;
import org.apache.commons.lang3.StringUtils;

public final class AliasGenerator {

    private AliasGenerator() {}

    public static JavaFile generateAliasType(
            TypeMapper typeMapper,
            AliasDefinition typeDef) {

        TypeName aliasTypeName = typeMapper.getClassName(typeDef.getAlias());

        String typePackage = typeDef.getTypeName().getPackage();
        ClassName thisClass = ClassName.get(typePackage, typeDef.getTypeName().getName());

        TypeSpec.Builder spec = TypeSpec.classBuilder(typeDef.getTypeName().getName())
                .addAnnotation(ConjureAnnotations.getConjureGeneratedAnnotation(AliasGenerator.class))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addField(aliasTypeName, "value", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(createConstructor(aliasTypeName))
                .addMethod(MethodSpec.methodBuilder("get")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(JsonValue.class)
                        .returns(aliasTypeName)
                        .addStatement("return value")
                        .build())
                .addMethod(MethodSpec.methodBuilder("toString")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(String.class)
                        .addCode(primitiveSafeToString(aliasTypeName))
                        .build())
                .addMethod(MethodSpec.methodBuilder("equals")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .addParameter(TypeName.OBJECT, "other")
                        .returns(TypeName.BOOLEAN)
                        .addCode(primitiveSafeEquality(thisClass, aliasTypeName))
                        .build())
                .addMethod(MethodSpec.methodBuilder("hashCode")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(TypeName.INT)
                        .addCode(primitiveSafeHashCode(aliasTypeName))
                        .build());

        addCustomDeserializer(spec, aliasTypeName, thisClass);

        Optional<CodeBlock> maybeValueOfFactoryMethod = valueOfFactoryMethod(
                typeDef.getAlias(), thisClass, aliasTypeName, typeMapper);
        if (maybeValueOfFactoryMethod.isPresent()) {
            spec.addMethod(MethodSpec.methodBuilder("valueOf")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(String.class, "value")
                    .returns(thisClass)
                    .addCode(maybeValueOfFactoryMethod.get())
                    .build());
        }

        spec.addMethod(MethodSpec.methodBuilder("of")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotation(JsonCreator.class)
                .addParameter(aliasTypeName, "value")
                .returns(thisClass)
                .addStatement("return new $T(value)", thisClass)
                .build());

        if (typeDef.getAlias().accept(TypeVisitor.IS_PRIMITIVE) && typeDef.getAlias().accept(
                TypeVisitor.PRIMITIVE).equals(PrimitiveType.DOUBLE)) {
            CodeBlock codeBlock = CodeBlock.builder()
                    .addStatement("return new $T((double) value)", thisClass)
                    .build();

            spec.addMethod(MethodSpec.methodBuilder("of")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addAnnotation(JsonCreator.class)
                    .addParameter(TypeName.INT, "value")
                    .returns(thisClass)
                    .addCode(codeBlock)
                    .build());

            CodeBlock doubleFromStringCodeBlock = CodeBlock.builder()
                    .beginControlFlow("switch (value)")
                    .add("case \"NaN\":\n")
                    .indent()
                    .addStatement("return $T.of($T.NaN)", thisClass, Double.class)
                    .unindent()
                    .add("case \"Infinity\":\n")
                    .indent()
                    .addStatement("return $T.of($T.POSITIVE_INFINITY)", thisClass, Double.class)
                    .unindent()
                    .add("case \"-Infinity\":\n")
                    .indent()
                    .addStatement("return $T.of($T.NEGATIVE_INFINITY)", thisClass, Double.class)
                    .unindent()
                    .add("default:\n")
                    .indent()
                    .addStatement(
                            "throw new $T(\"Cannot deserialize string into double: \" + value)",
                            IllegalArgumentException.class)
                    .unindent()
                    .endControlFlow()
                    .build();

            spec.addMethod(MethodSpec.methodBuilder("of")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addAnnotation(JsonCreator.class)
                    .addParameter(ClassName.get(String.class), "value")
                    .returns(thisClass)
                    .addCode(doubleFromStringCodeBlock)
                    .build());
        }

        typeDef.getDocs().ifPresent(docs -> spec.addJavadoc("$L", StringUtils.appendIfMissing(docs.get(), "\n")));

        return JavaFile.builder(typePackage, spec.build())
                .skipJavaLangImports(true)
                .indent("    ")
                .build();
    }

    // generate and annotate with a custom `@JsonDeserialize`r that correctly delegates null values
    private static void addCustomDeserializer(
            TypeSpec.Builder partial, TypeName aliasedTypeName, ClassName thisClass) {
        //
        TypeSpec converter = makeConverter(aliasedTypeName, thisClass);
        TypeSpec deserializer = makeDeserializer(aliasedTypeName, thisClass, converter);
        partial.addType(converter);
        partial.addType(deserializer);

        partial.addAnnotation(AnnotationSpec.builder(JsonDeserialize.class)
                .addMember("using", "$T.class", thisClass.nestedClass(deserializer.name))
                .build());
    }


    private static TypeSpec makeConverter(TypeName aliasedTypeName, ClassName thisClass) {
        /*
            public static final class AliasNameConverter
                    implements Converter<ContainedType, AliasName> {

                @Override
                public OptionalBearerTokenAliasExample convert(ContainedType value) {
                    return of(value);
                }

                @Override
                public JavaType getInputType(TypeFactory typeFactory) {
                    return typeFactory.constructType(new TypeReference<ContainedType>() { });
                }

                @Override
                public JavaType getOutputType(TypeFactory typeFactory) {
                    return typeFactory.constructType(AliasName.class);
                }
            }
         */
        // box the aliased type in case it's a primitive
        // CHECKSTYLE.OFF: ParameterAssignment - make sure we don't use the unboxed value
        aliasedTypeName = aliasedTypeName.box();
        // CHECKSTYLE.ON: ParameterAssignment
        return TypeSpec.classBuilder(thisClass.simpleName() + "Converter")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addSuperinterface(
                        ParameterizedTypeName.get(ClassName.get(Converter.class), aliasedTypeName, thisClass))
                .addMethod(
                        MethodSpec.methodBuilder("convert")
                                .addAnnotation(Override.class)
                                .addModifiers(Modifier.PUBLIC)
                                .addParameter(aliasedTypeName, "value")
                                .returns(thisClass)
                                .addCode("return of(value);")
                                .build()
                )
                .addMethod(
                        MethodSpec.methodBuilder("getInputType")
                                .addAnnotation(Override.class)
                                .addModifiers(Modifier.PUBLIC)
                                .addParameter(TypeFactory.class, "typeFactory")
                                .returns(JavaType.class)
                                .addCode("return typeFactory.constructType(new $T<$T>() { });",
                                        TypeReference.class, aliasedTypeName)
                                .build()
                )
                .addMethod(
                        MethodSpec.methodBuilder("getOutputType")
                                .addAnnotation(Override.class)
                                .addModifiers(Modifier.PUBLIC)
                                .addParameter(TypeFactory.class, "typeFactory")
                                .returns(JavaType.class)
                                .addCode("return typeFactory.constructType($T.class);", thisClass)
                                .build()
                ).build();
    }

    private static TypeSpec makeDeserializer(TypeName aliasedTypeName, ClassName thisClass, TypeSpec converter) {
        /*
            public static final class AliasNameDeserializer
                    extends StdDelegatingDeserializer<AliasName> {
                public AliasNameDeserializer() {
                    super(new AliasNameConverter());
                }

                public AliasNameDeserializer(
                        Converter<Object, OptionalBearerTokenAliasExample> converter,
                        JavaType delegateType,
                        JsonDeserializer<?> delegateDeserializer) {
                    super(converter, delegateType, delegateDeserializer);
                }

                @Override
                protected AliasNameDeserializer withDelegate(
                        Converter<Object, AliasName> converter,
                        JavaType delegateType,
                        JsonDeserializer<?> delegateDeserializer) {
                    return new AliasNameDeserializer(converter, delegateType, delegateDeserializer);
                }

                // delegating deserializer does the right thing except for null values, which short-circuit
                // since we may alias things that can deserialize from null, we need to ask the delegate how
                // it handles nulls.
                @Override
                public AliasName getNullValue(DeserializationContext context)
                        throws JsonMappingException {
                    return _converter.convert(_delegateDeserializer.getNullValue(context));
                }

            }
         */

        ClassName deserializerName = thisClass.nestedClass(thisClass.simpleName() + "Deserializer");
        ParameterizedTypeName specificConverter =
                ParameterizedTypeName.get(
                        ClassName.get(Converter.class), ClassName.get(Object.class), thisClass);

        MethodSpec defaultConstructor =
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("super(new $L())", converter.name)
                        .build();
        MethodSpec delegateConstructor =
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(specificConverter, "converter")
                        .addParameter(JavaType.class, "delegateType")
                        .addParameter(JsonDeserializer.class, "delegateDeserializer")
                        .addStatement("super(converter, delegateType, delegateDeserializer)")
                        .build();
        MethodSpec withDelegate =
                MethodSpec.methodBuilder("withDelegate")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(specificConverter, "converter")
                        .addParameter(JavaType.class, "delegateType")
                        .addParameter(
                                ParameterizedTypeName.get(
                                        ClassName.get(JsonDeserializer.class),
                                        WildcardTypeName.subtypeOf(Object.class)),
                                "delegateDeserializer")
                        .returns(deserializerName)
                        .addStatement(
                                "return new $T(converter, delegateType, delegateDeserializer)",
                                deserializerName)
                        .build();

        // the only 'real' change to the StdDelegatingDeserializer - we now delegate null values too!
        MethodSpec getNullValue = MethodSpec.methodBuilder("getNullValue")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addException(JsonMappingException.class)
                .addParameter(DeserializationContext.class, "context")
                .returns(thisClass)
                .addComment("delegating deserializer does the right thing except for null values, which short-circuit")
                .addComment("since we may alias things that can deserialize from null, got to ask the delegate how")
                .addComment("it handles nulls.")
                .addStatement("return _converter.convert(_delegateDeserializer.getNullValue(context))")
                .build();

        return TypeSpec.classBuilder(deserializerName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .superclass(ParameterizedTypeName.get(ClassName.get(StdDelegatingDeserializer.class), thisClass))
                .addMethod(defaultConstructor)
                .addMethod(delegateConstructor)
                .addMethod(withDelegate)
                .addMethod(getNullValue)
                .build();
    }

    private static Optional<CodeBlock> valueOfFactoryMethod(
            Type conjureType,
            ClassName thisClass,
            TypeName aliasTypeName,
            TypeMapper typeMapper) {

        // doesn't support valueOf factories for ANY and BINARY types
        if (conjureType.accept(TypeVisitor.IS_PRIMITIVE)
                && !conjureType.accept(TypeVisitor.IS_ANY)
                && !conjureType.accept(TypeVisitor.IS_BINARY)) {
            return Optional.of(valueOfFactoryMethodForPrimitive(
                    conjureType.accept(TypeVisitor.PRIMITIVE), thisClass, aliasTypeName));
        } else if (conjureType.accept(TypeVisitor.IS_INTERNAL_REFERENCE)) {
            // delegate to aliased type's valueOf factory method
            Optional<AliasDefinition> aliasTypeDef = typeMapper.getType(conjureType.accept(TypeVisitor.REFERENCE))
                    .filter(type -> type.accept(TypeDefinitionVisitor.IS_ALIAS))
                    .map(type -> type.accept(TypeDefinitionVisitor.ALIAS));

            return aliasTypeDef.map(type -> {
                ClassName className = ClassName.get(type.getTypeName().getPackage(), type.getTypeName().getName());
                return CodeBlock.builder()
                        .addStatement("return new $T($T.valueOf(value))", thisClass, className).build();
            });
        }

        return Optional.empty();
    }

    @SuppressWarnings("checkstyle:cyclomaticcomplexity")
    private static CodeBlock valueOfFactoryMethodForPrimitive(
            PrimitiveType primitiveType,
            ClassName thisClass,
            TypeName aliasTypeName) {
        switch (primitiveType.get()) {
            case STRING:
                return CodeBlock.builder().addStatement("return new $T(value)", thisClass).build();
            case DOUBLE:
                return CodeBlock.builder()
                        .addStatement("return new $T($T.parseDouble(value))", thisClass, aliasTypeName.box()).build();
            case INTEGER:
                return CodeBlock.builder()
                        .addStatement("return new $T($T.parseInt(value))", thisClass, aliasTypeName.box()).build();
            case BOOLEAN:
                return CodeBlock.builder()
                        .addStatement("return new $T($T.parseBoolean(value))", thisClass, aliasTypeName.box()).build();
            case SAFELONG:
            case RID:
            case BEARERTOKEN:
                return CodeBlock.builder()
                        .addStatement("return new $T($T.valueOf(value))", thisClass, aliasTypeName).build();
            case UUID:
                return CodeBlock.builder()
                        .addStatement("return new $T($T.fromString(value))", thisClass, aliasTypeName).build();
            case DATETIME:
                return CodeBlock.builder()
                        .addStatement("return new $T($T.parse(value))", thisClass, aliasTypeName).build();
            case BINARY:
            case ANY:
            case UNKNOWN:
        }
        throw new IllegalStateException(
                "Unsupported primitive type: " + primitiveType + "for `valueOf` method.");
    }

    private static MethodSpec createConstructor(TypeName aliasTypeName) {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(aliasTypeName, "value");
        if (!aliasTypeName.isPrimitive()) {
            builder.addStatement("$T.requireNonNull(value, \"value cannot be null\")", Objects.class);
        }
        return builder
                .addStatement("this.value = value")
                .build();
    }

    private static CodeBlock primitiveSafeEquality(ClassName thisClass, TypeName aliasTypeName) {
        if (aliasTypeName.isPrimitive()) {
            return CodeBlocks.statement(
                    "return this == other || (other instanceof $1T && this.value == (($1T) other).value)",
                    thisClass);
        }
        return CodeBlocks.statement(
                "return this == other || (other instanceof $1T && this.value.equals((($1T) other).value))",
                thisClass);
    }

    private static CodeBlock primitiveSafeToString(TypeName aliasTypeName) {
        if (aliasTypeName.isPrimitive()) {
            return CodeBlocks.statement("return $T.valueOf(value)", String.class);
        }
        return CodeBlocks.statement("return value.toString()");
    }

    private static CodeBlock primitiveSafeHashCode(TypeName aliasTypeName) {
        if (aliasTypeName.isPrimitive()) {
            return CodeBlocks.statement("return $T.hashCode(value)", aliasTypeName.box());
        }
        return CodeBlocks.statement("return value.hashCode()");
    }
}
