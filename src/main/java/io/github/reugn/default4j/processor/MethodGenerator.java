package io.github.reugn.default4j.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.github.reugn.default4j.annotation.DefaultFactory;
import io.github.reugn.default4j.annotation.DefaultValue;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

import static io.github.reugn.default4j.processor.CodeGenUtils.ParameterInfo;
import static io.github.reugn.default4j.processor.CodeGenUtils.addMethodParameters;
import static io.github.reugn.default4j.processor.CodeGenUtils.collectParameterInfos;
import static io.github.reugn.default4j.processor.CodeGenUtils.copyMethodAnnotations;
import static io.github.reugn.default4j.processor.CodeGenUtils.copyThrowsDeclarations;
import static io.github.reugn.default4j.processor.CodeGenUtils.findFirstDefaultIndex;
import static io.github.reugn.default4j.processor.CodeGenUtils.getDefaultExpression;

/**
 * Generates helper code for methods annotated with {@code @WithDefaults}.
 *
 * <p>This generator supports two modes of code generation:
 *
 * <p><b>Static Mode (default):</b>
 * <p>Generates static overloaded methods that accept an instance as the first parameter:
 * <pre>{@code
 * // Original method:
 * @WithDefaults
 * public void configure(String host, @DefaultValue("8080") int port) { ... }
 *
 * // Generated in ConfigDefaults:
 * public static void configure(Config instance, String host) {
 *     instance.configure(host, 8080);
 * }
 * public static void configure(Config instance, String host, int port) {
 *     instance.configure(host, port);
 * }
 * }</pre>
 *
 * <p><b>Named Mode ({@code named=true}):</b>
 * <p>Generates a fluent builder class for setting parameters by name:
 * <pre>{@code
 * // Original method:
 * @WithDefaults(named = true)
 * public void connect(String host, @DefaultValue("5432") int port) { ... }
 *
 * // Generated builder:
 * public static final class ConnectBuilder {
 *     private final Database instance;
 *     private String host;
 *     private int port = 5432;
 *
 *     public ConnectBuilder(Database instance) { this.instance = instance; }
 *     public ConnectBuilder host(String host) { this.host = host; return this; }
 *     public ConnectBuilder port(int port) { this.port = port; return this; }
 *     public void call() { instance.connect(host, port); }
 * }
 *
 * // Usage:
 * DatabaseDefaults.connect(db).host("localhost").call();
 * }</pre>
 *
 * <p><b>Key Responsibilities:</b>
 * <ul>
 *   <li>Generate static overloaded methods for varying parameter counts</li>
 *   <li>Generate builder classes for named parameter invocation</li>
 *   <li>Handle both {@code @DefaultValue} and {@code @DefaultFactory} annotations</li>
 *   <li>Preserve method annotations, return types, and exception declarations</li>
 *   <li>Validate consecutive defaults constraint</li>
 * </ul>
 *
 * @see BuilderGenerator
 * @see CodeGenUtils
 */
final class MethodGenerator {

    private MethodGenerator() {
    }

    // ==================== STATIC MODE ====================

    /**
     * Generates static overloaded methods for all valid parameter counts.
     *
     * <p>For a method with N parameters where the first default appears at index K,
     * this generates (N - K) overloaded methods:
     * <ul>
     *   <li>One method accepting K parameters (all required)</li>
     *   <li>One method accepting K+1 parameters</li>
     *   <li>... and so on up to N parameters (full signature)</li>
     * </ul>
     *
     * <p>Each overloaded method accepts the instance as its first parameter,
     * followed by the method's own parameters.
     *
     * <p>Example: For {@code void send(String to, @DefaultValue("Hi") String msg, @DefaultValue("1") int priority)}:
     * <pre>{@code
     * public static void send(Mailer instance, String to) { ... }
     * public static void send(Mailer instance, String to, String msg) { ... }
     * public static void send(Mailer instance, String to, String msg, int priority) { ... }
     * }</pre>
     *
     * <p>Validates:
     * <ul>
     *   <li>Consecutive defaults constraint</li>
     *   <li>No duplicate signatures with existing methods (generates static overloads)</li>
     * </ul>
     *
     * @param originalMethod the method element being processed
     * @param originalType   the class containing the method
     * @param errorReporter  for reporting validation errors
     * @return list of generated method specs, empty if validation fails
     */
    static List<MethodSpec> generateStaticOverloadedMethods(ExecutableElement originalMethod,
                                                            ClassName originalType,
                                                            ErrorReporter errorReporter) {
        List<MethodSpec> overloads = new ArrayList<>();
        List<ParameterInfo> paramInfos = collectParameterInfos(originalMethod);

        int firstDefaultIndex = findFirstDefaultIndex(paramInfos);

        if (firstDefaultIndex == -1) {
            overloads.add(generateStaticMethod(originalMethod, paramInfos, paramInfos.size(), originalType));
            return overloads;
        }

        if (!ValidationUtils.validateConsecutiveDefaults(originalMethod, paramInfos, firstDefaultIndex, errorReporter)) {
            return overloads;
        }

        // Note: Duplicate signature validation is not done here because the generated methods
        // are in a separate Defaults class, not the original class. Conflicts can only occur
        // within the generated class itself, which is controlled by the processor.

        for (int numParams = firstDefaultIndex; numParams < paramInfos.size(); numParams++) {
            overloads.add(generateStaticMethod(originalMethod, paramInfos, numParams, originalType));
        }

        return overloads;
    }

    /**
     * Generates a single static overloaded method with a specific parameter count.
     *
     * <p>The generated method:
     * <ul>
     *   <li>Has the same name as the original method</li>
     *   <li>Takes the instance as its first parameter</li>
     *   <li>Accepts {@code numProvidedParams} of the original method's parameters</li>
     *   <li>Uses default values for remaining parameters</li>
     *   <li>Preserves annotations and exception declarations</li>
     * </ul>
     *
     * @param originalMethod    the method being wrapped
     * @param paramInfos        parameter information including defaults
     * @param numProvidedParams number of parameters to expose (rest use defaults)
     * @param originalType      the class containing the method
     * @return the generated method specification
     */
    private static MethodSpec generateStaticMethod(ExecutableElement originalMethod,
                                                   List<ParameterInfo> paramInfos,
                                                   int numProvidedParams,
                                                   ClassName originalType) {
        String methodName = originalMethod.getSimpleName().toString();
        TypeMirror returnType = originalMethod.getReturnType();

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        copyMethodAnnotations(originalMethod, methodBuilder);

        if (returnType.getKind() != TypeKind.VOID) {
            methodBuilder.returns(TypeName.get(returnType));
        }

        methodBuilder.addParameter(originalType, CodeGenUtils.INSTANCE_PARAM);
        addMethodParameters(methodBuilder, paramInfos, numProvidedParams);
        buildDelegateCall(methodBuilder, originalMethod, paramInfos, numProvidedParams, originalType,
                CodeGenUtils.INSTANCE_PARAM);
        copyThrowsDeclarations(originalMethod, methodBuilder);

        return methodBuilder.build();
    }

    /**
     * Builds the method body that delegates to the original method.
     *
     * <p>Generates a statement like:
     * <pre>{@code
     * return instance.methodName(param1, param2, defaultValue3);
     * }</pre>
     *
     * <p>For parameters beyond {@code numProvidedParams}, the default expression
     * is used (from {@code @DefaultValue} or {@code @DefaultFactory}).
     *
     * @param methodBuilder     the method builder to add the statement to
     * @param originalMethod    the original method being delegated to
     * @param paramInfos        parameter information including defaults
     * @param numProvidedParams count of parameters that are passed through
     * @param enclosingClass    the class containing the original method
     * @param delegateName      the variable name of the instance to call
     */
    private static void buildDelegateCall(MethodSpec.Builder methodBuilder,
                                          ExecutableElement originalMethod,
                                          List<ParameterInfo> paramInfos,
                                          int numProvidedParams,
                                          ClassName enclosingClass,
                                          String delegateName) {
        String methodName = originalMethod.getSimpleName().toString();
        TypeMirror returnType = originalMethod.getReturnType();

        StringBuilder callBuilder = new StringBuilder();
        if (returnType.getKind() != TypeKind.VOID) {
            callBuilder.append("return ");
        }
        callBuilder.append(delegateName).append(".").append(methodName).append("(");

        List<String> args = new ArrayList<>();
        for (int i = 0; i < paramInfos.size(); i++) {
            ParameterInfo info = paramInfos.get(i);
            if (i < numProvidedParams) {
                args.add(info.element().getSimpleName().toString());
            } else {
                args.add(getDefaultExpression(info, enclosingClass));
            }
        }
        callBuilder.append(String.join(", ", args));
        callBuilder.append(")");

        methodBuilder.addStatement(callBuilder.toString());
    }

    // ==================== NAMED MODE ====================

    /**
     * Generates a builder class for named parameter method invocation.
     *
     * <p>The generated builder provides a fluent API for setting method parameters
     * by name, with defaults pre-populated. This enables calling methods with
     * many parameters in a readable, order-independent manner.
     *
     * <p>Builder structure:
     * <pre>{@code
     * public static final class MethodNameBuilder {
     *     private final OriginalType instance;  // The target instance
     *     private ParamType1 param1;            // Required: no default
     *     private ParamType2 param2 = default;  // Optional: has default
     *
     *     public MethodNameBuilder(OriginalType instance) { ... }
     *     public MethodNameBuilder param1(ParamType1 param1) { ... }
     *     public MethodNameBuilder param2(ParamType2 param2) { ... }
     *     public ReturnType call() { ... }      // Invokes the method
     * }
     * }</pre>
     *
     * <p>The {@code call()} method validates that all required parameters
     * (those without defaults) have been set before invoking the original method.
     *
     * @param method           the method element to generate a builder for
     * @param originalType     the class containing the method
     * @param builderClassName the name for the generated builder class
     * @param builderType      the fully qualified type of the builder
     * @return the generated builder class specification
     */
    static TypeSpec generateBuilderClass(ExecutableElement method,
                                         ClassName originalType,
                                         String builderClassName,
                                         ClassName builderType) {
        String methodName = method.getSimpleName().toString();
        List<? extends VariableElement> params = method.getParameters();

        // Convert params to FieldInfo list
        List<BuilderGenerator.FieldInfo> fieldInfos = toFieldInfos(params, originalType);

        // Build fields: instance field + parameter fields
        List<FieldSpec> fields = new ArrayList<>();
        fields.add(FieldSpec.builder(originalType, CodeGenUtils.INSTANCE_PARAM, Modifier.PRIVATE, Modifier.FINAL).build());
        fields.addAll(BuilderGenerator.createFields(fieldInfos));

        // Constructor with instance parameter
        MethodSpec constructor = BuilderGenerator.createInstanceConstructor(originalType);

        // Fluent setters
        List<MethodSpec> setters = BuilderGenerator.createSetters(fieldInfos, builderType);

        // Call method (different from build - invokes method on instance)
        MethodSpec callMethod = createCallMethod(method, fieldInfos);

        return TypeSpec.classBuilder(builderClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addFields(fields)
                .addMethod(constructor)
                .addMethods(setters)
                .addMethod(callMethod)
                .addJavadoc("Builder for {@link $T#$N} with named parameters.\n", originalType, methodName)
                .build();
    }

    /**
     * Converts method parameters to {@link BuilderGenerator.FieldInfo} objects.
     *
     * <p>Each parameter is analyzed for {@code @DefaultValue} and {@code @DefaultFactory}
     * annotations, and the appropriate default expression is computed.
     *
     * @param params       the method parameters to convert
     * @param originalType the containing class (for resolving factory references)
     * @return list of field info objects for builder generation
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
     * Creates the terminal {@code call()} method for the builder.
     *
     * <p>The call method:
     * <ol>
     *   <li>Validates all required parameters (those without defaults) are set</li>
     *   <li>Invokes the original method on the stored instance</li>
     *   <li>Returns the method's result (if not void)</li>
     * </ol>
     *
     * <p>Generated code example:
     * <pre>{@code
     * public ReturnType call() {
     *     if (requiredParam == null) {
     *         throw new IllegalStateException("requiredParam is required");
     *     }
     *     return instance.methodName(requiredParam, optionalParam);
     * }
     * }</pre>
     *
     * @param method     the original method being wrapped
     * @param fieldInfos the builder field information
     * @return the call method specification
     */
    private static MethodSpec createCallMethod(ExecutableElement method,
                                               List<BuilderGenerator.FieldInfo> fieldInfos) {
        String methodName = method.getSimpleName().toString();
        TypeMirror returnType = method.getReturnType();

        MethodSpec.Builder callBuilder = MethodSpec.methodBuilder("call")
                .addModifiers(Modifier.PUBLIC);

        if (returnType.getKind() != TypeKind.VOID) {
            callBuilder.returns(TypeName.get(returnType));
        }

        // Validate required parameters
        BuilderGenerator.addRequiredFieldValidation(callBuilder, fieldInfos, info -> !info.hasDefault());

        // Invoke method on instance
        String args = BuilderGenerator.buildArgumentList(fieldInfos);
        if (returnType.getKind() != TypeKind.VOID) {
            callBuilder.addStatement("return instance.$N($L)", methodName, args);
        } else {
            callBuilder.addStatement("instance.$N($L)", methodName, args);
        }

        copyThrowsDeclarations(method, callBuilder);

        return callBuilder.build();
    }
}
