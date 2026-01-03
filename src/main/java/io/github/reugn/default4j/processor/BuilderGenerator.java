package io.github.reugn.default4j.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared utilities for generating builder classes in named parameter mode.
 *
 * <p>Provides reusable components for constructing the builder pattern, consolidating
 * logic used by multiple generators:
 * <ul>
 *   <li>{@link MethodGenerator} — builders for instance method calls</li>
 *   <li>{@link ConstructorGenerator} — builders for object construction</li>
 *   <li>{@link IncludeGenerator} — builders for external type construction</li>
 * </ul>
 *
 * <p><b>Builder Structure:</b>
 * <p>Generated builders follow a consistent pattern:
 * <pre>{@code
 * public static final class PersonBuilder {
 *     // 1. Private fields (with optional default initializers)
 *     private String name;
 *     private int age = 30;
 *
 *     // 2. Private constructor
 *     private PersonBuilder() {}
 *
 *     // 3. Fluent setter methods
 *     public PersonBuilder name(String name) {
 *         this.name = name;
 *         return this;
 *     }
 *
 *     // 4. Terminal build method with validation
 *     public Person build() {
 *         if (this.name == null) {
 *             throw new IllegalStateException("Required parameter 'name' was not set");
 *         }
 *         return new Person(name, age);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Validation:</b>
 * <p>Required fields (those without defaults) that are non-primitive are validated
 * in the {@code build()} method to ensure they've been set before construction.
 *
 * @see MethodGenerator
 * @see ConstructorGenerator
 * @see IncludeGenerator
 */
final class BuilderGenerator {

    private BuilderGenerator() {
    }

    /**
     * Creates private field declarations for the builder class.
     *
     * <p>Each field is declared as {@code private} and optionally initialized
     * with its default expression.
     *
     * <p><b>Example Output:</b>
     * <pre>{@code
     * private String name;                    // Required (no default)
     * private int port = 8080;                // Optional with literal
     * private String host = Defaults.HOST;    // Optional with field reference
     * }</pre>
     *
     * @param fieldInfos list of field information from parameter analysis
     * @return list of JavaPoet {@link FieldSpec} objects for the builder
     */
    static List<FieldSpec> createFields(List<FieldInfo> fieldInfos) {
        List<FieldSpec> fields = new ArrayList<>();

        for (FieldInfo info : fieldInfos) {
            FieldSpec.Builder fieldBuilder = FieldSpec.builder(info.type, info.name, Modifier.PRIVATE);
            if (info.hasDefault()) {
                fieldBuilder.initializer(info.defaultExpression);
            }
            fields.add(fieldBuilder.build());
        }

        return fields;
    }

    /**
     * Creates fluent setter methods for all builder fields.
     *
     * <p>Each setter follows the fluent pattern: accepts a value, assigns it to the
     * field, and returns {@code this} for method chaining.
     *
     * <p><b>Example Output:</b>
     * <pre>{@code
     * public PersonBuilder name(String name) {
     *     this.name = name;
     *     return this;
     * }
     * }</pre>
     *
     * @param fieldInfos  list of field information (one setter per field)
     * @param builderType the builder's {@link ClassName} for the return type
     * @return list of JavaPoet {@link MethodSpec} objects for the setters
     */
    static List<MethodSpec> createSetters(List<FieldInfo> fieldInfos, ClassName builderType) {
        List<MethodSpec> setters = new ArrayList<>();

        for (FieldInfo info : fieldInfos) {
            setters.add(MethodSpec.methodBuilder(info.name)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderType)
                    .addParameter(info.type, info.name)
                    .addStatement("this.$N = $N", info.name, info.name)
                    .addStatement("return this")
                    .build());
        }

        return setters;
    }

    /**
     * Creates a private no-arg constructor for the builder.
     *
     * <p>Used for constructor builders where the builder is instantiated via
     * a static factory method (e.g., {@code PersonDefaults.create()}).
     *
     * @return JavaPoet {@link MethodSpec} for a private parameterless constructor
     */
    static MethodSpec createPrivateConstructor() {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build();
    }

    /**
     * Creates a package-private constructor that stores an instance reference.
     *
     * <p>Used for method builders where the builder needs a reference to the
     * target object to call the instance method on.
     *
     * <p><b>Example Output:</b>
     * <pre>{@code
     * GreetBuilder(Greeter instance) {
     *     this.instance = instance;
     * }
     * }</pre>
     *
     * @param instanceType the type of the instance to store (e.g., {@code Greeter})
     * @return JavaPoet {@link MethodSpec} for the constructor
     */
    static MethodSpec createInstanceConstructor(ClassName instanceType) {
        return MethodSpec.constructorBuilder()
                .addParameter(instanceType, CodeGenUtils.INSTANCE_PARAM)
                .addStatement("this.instance = instance")
                .build();
    }

    /**
     * Adds null-check validation for required fields to the build/call method.
     *
     * <p>Generates runtime validation that throws {@link IllegalStateException}
     * if a required (non-primitive, non-defaulted) field is {@code null}.
     *
     * <p><b>Example Output:</b>
     * <pre>{@code
     * if (this.name == null) {
     *     throw new IllegalStateException("Required parameter 'name' was not set");
     * }
     * }</pre>
     *
     * <p><b>Skipped Fields:</b>
     * <ul>
     *   <li>Primitive fields — cannot be null, always have a zero-value default</li>
     *   <li>Fields with defaults — already initialized, not required to be set</li>
     * </ul>
     *
     * @param builder    the {@link MethodSpec.Builder} to add validation statements to
     * @param fieldInfos list of field information
     * @param isRequired predicate returning {@code true} for fields that require validation
     */
    static void addRequiredFieldValidation(MethodSpec.Builder builder,
                                           List<FieldInfo> fieldInfos,
                                           Predicate<FieldInfo> isRequired) {
        for (FieldInfo info : fieldInfos) {
            if (isRequired.test(info) && !info.type.isPrimitive()) {
                builder.beginControlFlow("if (this.$N == null)", info.name)
                        .addStatement("throw new $T($S)", IllegalStateException.class,
                                "Required parameter '" + info.name + "' was not set")
                        .endControlFlow();
            }
        }
    }

    /**
     * Builds the argument list for the constructor or method invocation.
     *
     * <p>Concatenates all field names into a comma-separated string suitable
     * for use in a method or constructor call.
     *
     * <p><b>Example:</b>
     * <p>For fields {@code [name, age, city]}, returns {@code "name, age, city"}.
     *
     * @param fieldInfos list of field information in parameter order
     * @return comma-separated argument string, or empty string if no fields
     */
    static String buildArgumentList(List<FieldInfo> fieldInfos) {
        return fieldInfos.stream()
                .map(FieldInfo::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    /**
     * Creates a complete builder class for constructor invocation.
     *
     * <p>Assembles all builder components into a complete inner class:
     * <ul>
     *   <li>Private fields with optional default initializers</li>
     *   <li>Private no-arg constructor</li>
     *   <li>Fluent setter methods</li>
     *   <li>{@code build()} method with validation and constructor call</li>
     * </ul>
     *
     * <p><b>Example Output:</b>
     * <pre>{@code
     * public static final class PersonBuilder {
     *     private String name;
     *     private int age = 30;
     *     private PersonBuilder() {}
     *     public PersonBuilder name(String name) { this.name = name; return this; }
     *     public PersonBuilder age(int age) { this.age = age; return this; }
     *     public Person build() {
     *         if (this.name == null) { throw new IllegalStateException(...); }
     *         return new Person(name, age);
     *     }
     * }
     * }</pre>
     *
     * @param fieldInfos       list of field information from parameter analysis
     * @param targetType       the {@link ClassName} of the type being constructed
     * @param builderClassName the simple name for the builder (e.g., "PersonBuilder")
     * @param builderType      the {@link ClassName} for the builder (for nested class resolution)
     * @return complete JavaPoet {@link TypeSpec} for the builder class
     */
    static TypeSpec createConstructorBuilder(List<FieldInfo> fieldInfos,
                                             ClassName targetType,
                                             String builderClassName,
                                             ClassName builderType) {
        List<FieldSpec> fields = createFields(fieldInfos);
        MethodSpec constructor = createPrivateConstructor();
        List<MethodSpec> setters = createSetters(fieldInfos, builderType);

        MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(targetType);

        addRequiredFieldValidation(buildMethod, fieldInfos, info -> !info.hasDefault());
        buildMethod.addStatement("return new $T($L)", targetType, buildArgumentList(fieldInfos));

        return TypeSpec.classBuilder(builderClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addFields(fields)
                .addMethod(constructor)
                .addMethods(setters)
                .addMethod(buildMethod.build())
                .addJavadoc("Builder for {@link $T} with named parameters.\n", targetType)
                .build();
    }

    /**
     * Creates a static factory method that returns a new builder instance.
     *
     * <p>This is the entry point for using the builder pattern. Users call this
     * method to obtain a builder, then chain setters, and finally call {@code build()}.
     *
     * <p><b>Example Output:</b>
     * <pre>{@code
     * public static PersonBuilder create() {
     *     return new PersonBuilder();
     * }
     * }</pre>
     *
     * <p><b>Usage:</b>
     * <pre>{@code
     * Person p = PersonDefaults.create()
     *     .name("John")
     *     .age(25)
     *     .build();
     * }</pre>
     *
     * @param methodName  the factory method name (e.g., "create", "of", "builder")
     * @param builderType the {@link ClassName} of the builder to instantiate
     * @return JavaPoet {@link MethodSpec} for the static factory method
     */
    static MethodSpec createBuilderFactoryMethod(String methodName, ClassName builderType) {
        return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderType)
                .addStatement("return new $T()", builderType)
                .build();
    }

    /**
     * Immutable record holding information about a builder field.
     *
     * <p>Each field corresponds to a parameter in the target method/constructor.
     * Fields may have default initializers if the parameter has a default value.
     *
     * @param name              the field name (matches parameter name, e.g., "host")
     * @param type              the JavaPoet {@link TypeName} for the field
     * @param defaultExpression the Java expression for initialization, or {@code null} if required
     *                          (e.g., {@code "8080"}, {@code "Defaults.HOST"})
     */
    record FieldInfo(String name, TypeName type, String defaultExpression) {
        /**
         * Checks if this field has a default value.
         *
         * <p>Fields with defaults are optional in the builder pattern — users don't
         * need to call the setter. Fields without defaults are required and validated
         * in the {@code build()} method.
         *
         * @return {@code true} if a default expression is present
         */
        boolean hasDefault() {
            return defaultExpression != null;
        }
    }
}
