package io.github.reugn.default4j.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.github.reugn.default4j.annotation.DefaultFactory;
import io.github.reugn.default4j.annotation.DefaultValue;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

import static io.github.reugn.default4j.processor.CodeGenUtils.ParameterInfo;
import static io.github.reugn.default4j.processor.CodeGenUtils.collectParameterInfos;
import static io.github.reugn.default4j.processor.CodeGenUtils.collectRecordParameterInfos;
import static io.github.reugn.default4j.processor.CodeGenUtils.copyThrowsDeclarations;
import static io.github.reugn.default4j.processor.CodeGenUtils.findFirstDefaultIndex;
import static io.github.reugn.default4j.processor.CodeGenUtils.getDefaultExpression;

/**
 * Generates factory methods and builders for constructors annotated with {@code @WithDefaults}.
 *
 * <p>Handles code generation for constructor defaults, producing either overloaded factory
 * methods or a fluent builder class depending on the {@code named} setting.
 *
 * <p><b>Generation Modes:</b>
 * <table border="1">
 *   <caption>Generation modes for constructor defaults</caption>
 *   <tr><th>Mode</th><th>Setting</th><th>Output</th></tr>
 *   <tr>
 *     <td><b>Non-named</b></td>
 *     <td>{@code @WithDefaults} or {@code @WithDefaults(named=false)}</td>
 *     <td>Overloaded static factory methods</td>
 *   </tr>
 *   <tr>
 *     <td><b>Named</b></td>
 *     <td>{@code @WithDefaults(named=true)}</td>
 *     <td>Builder inner class with fluent API</td>
 *   </tr>
 * </table>
 *
 * <p><b>Non-Named Mode Example:</b>
 * <pre>{@code
 * // Source
 * public Person(String name, @DefaultValue("Unknown") String city) { }
 *
 * // Generated in PersonDefaults.java
 * public static Person create(String name, String city) {
 *     return new Person(name, city);
 * }
 * public static Person create(String name) {
 *     return new Person(name, "Unknown");
 * }
 * }</pre>
 *
 * <p><b>Named Mode Example:</b>
 * <pre>{@code
 * // Generated builder usage
 * Person p = PersonDefaults.create()
 *     .name("John")
 *     .city("NYC")
 *     .build();
 * }</pre>
 *
 * <p><b>Record Support:</b>
 * <p>Java records are fully supported with specialized methods:
 * <ul>
 *   <li>{@link #generateRecordFactoryMethods} — reads defaults from record components</li>
 *   <li>{@link #generateRecordBuilderClass} — creates builder for record construction</li>
 * </ul>
 *
 * <p><b>Exception Handling:</b>
 * <p>Generated factory methods copy the {@code throws} declarations from the original
 * constructor, ensuring callers can handle checked exceptions.
 *
 * @see MethodGenerator
 * @see BuilderGenerator
 * @see CodeGenUtils#collectRecordParameterInfos
 */
final class ConstructorGenerator {

    private ConstructorGenerator() {
    }

    // ==================== STATIC MODE (Factory Methods) ====================

    /**
     * Generates overloaded static factory methods for a constructor.
     *
     * <p>Creates multiple factory methods with the same name but different parameter
     * counts, progressively omitting trailing parameters that have defaults.
     *
     * <p><b>Generation Logic:</b>
     * <p>For a constructor with N parameters where the first default is at index D:
     * <ul>
     *   <li>Generates (N - D + 1) factory methods</li>
     *   <li>Parameter counts range from D to N (inclusive)</li>
     *   <li>Omitted parameters use their default expressions</li>
     * </ul>
     *
     * <p><b>Example:</b>
     * Constructor: {@code Person(String a, @Default String b, @Default String c)}
     * <ul>
     *   <li>{@code create(String a, String b, String c)} — all params</li>
     *   <li>{@code create(String a, String b)} — c uses default</li>
     *   <li>{@code create(String a)} — b and c use defaults</li>
     * </ul>
     *
     * @param constructor   the constructor element to generate factories for
     * @param originalType  the {@link ClassName} of the class being constructed
     * @param methodName    the name for all factory methods (e.g., "create", "of")
     * @param errorReporter callback for reporting validation errors
     * @return list of generated {@link MethodSpec}s; empty if validation fails
     */
    static List<MethodSpec> generateFactoryMethods(ExecutableElement constructor,
                                                   ClassName originalType,
                                                   String methodName,
                                                   ErrorReporter errorReporter) {
        List<ParameterInfo> paramInfos = collectParameterInfos(constructor);
        return generateFactoryMethodsFromParams(constructor, paramInfos, originalType, methodName, errorReporter);
    }

    /**
     * Generates overloaded static factory methods for a record's canonical constructor.
     *
     * <p>Similar to {@link #generateFactoryMethods}, but merges annotations from both
     * the constructor parameters and record component declarations. This allows users
     * to place {@code @DefaultValue} on either location.
     *
     * <p><b>Annotation Precedence:</b>
     * <p>Constructor parameter annotations take precedence over component annotations:
     * <pre>{@code
     * public record Config(
     *     @DefaultValue("A") String host,   // Uses "A" from component
     *     int port
     * ) {
     *     public Config(@DefaultValue("B") String host, int port) { }
     *     // host uses "B" from constructor (overrides)
     * }
     * }</pre>
     *
     * @param constructor   the record's canonical constructor
     * @param components    the record component elements (for fallback annotation lookup)
     * @param originalType  the {@link ClassName} of the record being constructed
     * @param methodName    the name for all factory methods
     * @param errorReporter callback for reporting validation errors
     * @return list of generated {@link MethodSpec}s; empty if validation fails
     */
    static List<MethodSpec> generateRecordFactoryMethods(ExecutableElement constructor,
                                                         List<RecordComponentElement> components,
                                                         ClassName originalType,
                                                         String methodName,
                                                         ErrorReporter errorReporter) {
        List<ParameterInfo> paramInfos = collectRecordParameterInfos(constructor, components);
        return generateFactoryMethodsFromParams(constructor, paramInfos, originalType, methodName, errorReporter);
    }

    /**
     * Core implementation for generating overloaded factory methods.
     *
     * <p>Shared by both {@link #generateFactoryMethods} and {@link #generateRecordFactoryMethods}.
     * Validates consecutive defaults before generating methods.
     *
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li>Find the first defaulted parameter index</li>
     *   <li>If no defaults, generate a single pass-through factory</li>
     *   <li>Validate that all params after the first default also have defaults</li>
     *   <li>Generate one factory for each parameter count from firstDefault to size</li>
     * </ol>
     */
    private static List<MethodSpec> generateFactoryMethodsFromParams(ExecutableElement constructor,
                                                                     List<ParameterInfo> paramInfos,
                                                                     ClassName originalType,
                                                                     String methodName,
                                                                     ErrorReporter errorReporter) {
        List<MethodSpec> methods = new ArrayList<>();
        int firstDefaultIndex = findFirstDefaultIndex(paramInfos);

        if (firstDefaultIndex == -1) {
            methods.add(createFactoryMethod(constructor, paramInfos, paramInfos.size(), originalType, methodName));
            return methods;
        }

        if (!ValidationUtils.validateConsecutiveDefaults(constructor, paramInfos, firstDefaultIndex, errorReporter)) {
            return methods;
        }

        for (int numParams = firstDefaultIndex; numParams < paramInfos.size(); numParams++) {
            methods.add(createFactoryMethod(constructor, paramInfos, numParams, originalType, methodName));
        }

        return methods;
    }

    /**
     * Creates a single factory method with the specified parameter count.
     *
     * <p>Parameters up to {@code numProvidedParams} are included as method parameters.
     * Remaining parameters use their default expressions in the constructor call.
     *
     * <p><b>Exception Propagation:</b>
     * <p>Copies the constructor's {@code throws} declarations to the factory method,
     * ensuring checked exceptions are properly declared.
     */
    private static MethodSpec createFactoryMethod(ExecutableElement constructor,
                                                  List<ParameterInfo> paramInfos,
                                                  int numProvidedParams,
                                                  ClassName originalType,
                                                  String methodName) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(originalType);

        addFactoryParameters(builder, paramInfos, numProvidedParams);
        addConstructorInvocation(builder, paramInfos, numProvidedParams, originalType);

        // Copy checked exceptions from constructor
        copyThrowsDeclarations(constructor, builder);

        return builder.build();
    }

    /**
     * Adds parameter declarations to a factory method, copying non-default4j annotations.
     */
    private static void addFactoryParameters(MethodSpec.Builder builder,
                                             List<ParameterInfo> paramInfos,
                                             int numProvidedParams) {
        for (int i = 0; i < numProvidedParams; i++) {
            ParameterInfo info = paramInfos.get(i);
            ParameterSpec.Builder paramBuilder = ParameterSpec.builder(
                    TypeName.get(info.element().asType()),
                    info.element().getSimpleName().toString()
            );

            // Copy annotations except default4j annotations
            for (AnnotationMirror annotation : info.element().getAnnotationMirrors()) {
                String annotationName = annotation.getAnnotationType().toString();
                if (!annotationName.contains("DefaultValue") && !annotationName.contains("DefaultFactory")) {
                    paramBuilder.addAnnotation(AnnotationSpec.get(annotation));
                }
            }

            builder.addParameter(paramBuilder.build());
        }
    }

    /**
     * Adds the constructor invocation statement to a factory method.
     */
    private static void addConstructorInvocation(MethodSpec.Builder builder,
                                                 List<ParameterInfo> paramInfos,
                                                 int numProvidedParams,
                                                 ClassName originalType) {
        String args = buildConstructorArgs(paramInfos, numProvidedParams, originalType);
        builder.addStatement("return new $T($L)", originalType, args);
    }

    /**
     * Builds the argument list for constructor invocation.
     * Provided parameters use their names; omitted parameters use their default expressions.
     */
    private static String buildConstructorArgs(List<ParameterInfo> paramInfos, int numProvidedParams,
                                               ClassName originalType) {
        List<String> args = new ArrayList<>();
        for (int i = 0; i < paramInfos.size(); i++) {
            ParameterInfo info = paramInfos.get(i);
            if (i < numProvidedParams) {
                args.add(info.element().getSimpleName().toString());
            } else {
                args.add(getDefaultExpression(info, originalType));
            }
        }
        return String.join(", ", args);
    }

    // ==================== NAMED MODE (Builder) ====================

    /**
     * Generates a builder class for named parameter construction.
     *
     * <p>Creates a fluent builder allowing parameters to be set in any order,
     * with optional parameters pre-initialized to their default values.
     *
     * <p><b>Generated Structure:</b>
     * <pre>{@code
     * public static final class PersonBuilder {
     *     private String name;                    // Required
     *     private String city = "Unknown";        // Optional (default)
     *
     *     private PersonBuilder() {}
     *
     *     public PersonBuilder name(String name) {
     *         this.name = name;
     *         return this;
     *     }
     *     public PersonBuilder city(String city) {
     *         this.city = city;
     *         return this;
     *     }
     *     public Person build() {
     *         if (this.name == null) {
     *             throw new IllegalStateException("Required parameter 'name' was not set");
     *         }
     *         return new Person(name, city);
     *     }
     * }
     * }</pre>
     *
     * <p><b>Builder Naming:</b>
     * <p>The builder is named after the class being built (e.g., {@code PersonBuilder}),
     * not the factory method name.
     *
     * @param constructor      the constructor to generate a builder for
     * @param originalType     the {@link ClassName} of the class being constructed
     * @param builderClassName the simple name for the builder (e.g., "PersonBuilder")
     * @param builderType      the {@link ClassName} for the builder (for nested class resolution)
     * @return the generated builder {@link TypeSpec}
     */
    static TypeSpec generateBuilderClass(ExecutableElement constructor,
                                         ClassName originalType,
                                         String builderClassName,
                                         ClassName builderType) {
        List<BuilderGenerator.FieldInfo> fieldInfos = toFieldInfos(
                constructor.getParameters(), originalType);

        return BuilderGenerator.createConstructorBuilder(
                fieldInfos, originalType, builderClassName, builderType);
    }

    /**
     * Generates a builder class for a record's canonical constructor.
     *
     * <p>Similar to {@link #generateBuilderClass}, but merges annotations from
     * both constructor parameters and record components.
     *
     * <p><b>Annotation Sources:</b>
     * <pre>{@code
     * @WithDefaults(named = true)
     * public record Config(
     *     @DefaultValue("localhost") String host,  // From component
     *     int port                                  // Required
     * ) {}
     * }</pre>
     *
     * @param constructor      the record's canonical constructor
     * @param components       the record component elements (for annotation lookup)
     * @param originalType     the {@link ClassName} of the record being constructed
     * @param builderClassName the simple name for the builder (e.g., "ConfigBuilder")
     * @param builderType      the {@link ClassName} for the builder
     * @return the generated builder {@link TypeSpec}
     */
    static TypeSpec generateRecordBuilderClass(ExecutableElement constructor,
                                               List<RecordComponentElement> components,
                                               ClassName originalType,
                                               String builderClassName,
                                               ClassName builderType) {
        List<BuilderGenerator.FieldInfo> fieldInfos = toRecordFieldInfos(
                constructor.getParameters(), components, originalType);

        return BuilderGenerator.createConstructorBuilder(
                fieldInfos, originalType, builderClassName, builderType);
    }

    /**
     * Converts constructor parameters to builder field information.
     *
     * <p>Extracts default annotations from each parameter and creates corresponding
     * {@link BuilderGenerator.FieldInfo} records for builder generation.
     *
     * @param params       the constructor parameters
     * @param originalType the enclosing class (for resolving simple name references)
     * @return list of field info in parameter order
     */
    private static List<BuilderGenerator.FieldInfo> toFieldInfos(
            List<? extends VariableElement> params, ClassName originalType) {
        List<BuilderGenerator.FieldInfo> fieldInfos = new ArrayList<>();

        for (VariableElement param : params) {
            TypeName paramType = TypeName.get(param.asType());
            String paramName = param.getSimpleName().toString();

            DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
            DefaultFactory defaultFactory = param.getAnnotation(DefaultFactory.class);
            ParameterInfo info = new ParameterInfo(param, defaultValue, defaultFactory);

            String defaultExpr = info.hasDefault() ? getDefaultExpression(info, originalType) : null;
            fieldInfos.add(new BuilderGenerator.FieldInfo(paramName, paramType, defaultExpr));
        }

        return fieldInfos;
    }

    /**
     * Converts record constructor parameters to builder field information.
     *
     * <p>Merges annotations from constructor parameters and record components,
     * with constructor parameter annotations taking precedence.
     *
     * @param params       the constructor parameters
     * @param components   the record components (same order as params)
     * @param originalType the record class (for resolving simple name references)
     * @return list of field info in parameter order
     */
    private static List<BuilderGenerator.FieldInfo> toRecordFieldInfos(
            List<? extends VariableElement> params,
            List<RecordComponentElement> components,
            ClassName originalType) {
        List<BuilderGenerator.FieldInfo> fieldInfos = new ArrayList<>();

        for (int i = 0; i < params.size(); i++) {
            VariableElement param = params.get(i);
            RecordComponentElement component = components.get(i);
            TypeName paramType = TypeName.get(param.asType());
            String paramName = param.getSimpleName().toString();

            // Check both parameter and record component for defaults
            DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
            if (defaultValue == null) {
                defaultValue = component.getAnnotation(DefaultValue.class);
            }
            DefaultFactory defaultFactory = param.getAnnotation(DefaultFactory.class);
            if (defaultFactory == null) {
                defaultFactory = component.getAnnotation(DefaultFactory.class);
            }

            ParameterInfo info = new ParameterInfo(param, defaultValue, defaultFactory);
            String defaultExpr = info.hasDefault() ? getDefaultExpression(info, originalType) : null;
            fieldInfos.add(new BuilderGenerator.FieldInfo(paramName, paramType, defaultExpr));
        }

        return fieldInfos;
    }
}
