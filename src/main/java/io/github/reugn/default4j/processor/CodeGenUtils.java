package io.github.reugn.default4j.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import io.github.reugn.default4j.annotation.DefaultFactory;
import io.github.reugn.default4j.annotation.DefaultValue;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared utilities for code generation in default4j.
 *
 * <p>This utility class provides common operations used by all generators, centralizing
 * logic for parameter analysis, annotation handling, and expression generation.
 *
 * <p><b>Responsibilities:</b>
 * <table border="1">
 *   <caption>CodeGenUtils method categories</caption>
 *   <tr><th>Category</th><th>Methods</th><th>Purpose</th></tr>
 *   <tr>
 *     <td>Parameter Collection</td>
 *     <td>{@link #collectParameterInfos}, {@link #collectRecordParameterInfos}</td>
 *     <td>Extract default annotations from methods, constructors, and records</td>
 *   </tr>
 *   <tr>
 *     <td>Default Expressions</td>
 *     <td>{@link #getDefaultExpression}, {@link #convertDefaultValue}</td>
 *     <td>Convert annotations to Java code expressions</td>
 *   </tr>
 *   <tr>
 *     <td>Annotation Handling</td>
 *     <td>{@link #copyMethodAnnotations}, {@link #addMethodParameters}</td>
 *     <td>Preserve user annotations while filtering default4j ones</td>
 *   </tr>
 *   <tr>
 *     <td>String Utilities</td>
 *     <td>{@link #escapeString}, {@link #capitalize}</td>
 *     <td>String manipulation for code generation</td>
 *   </tr>
 * </table>
 *
 * <p><b>Default Expression Examples:</b>
 * <p>Shows how annotations translate to generated code expressions.
 * <ul>
 *   <li>{@code @DefaultValue("42")} → 42</li>
 *   <li>{@code @DefaultValue("hello")} → "hello"</li>
 *   <li>{@code @DefaultValue("100L")} → 100L</li>
 *   <li>{@code @DefaultValue("3.14")} → 3.14 (double) or 3.14F (float)</li>
 *   <li>{@code @DefaultValue("null")} → null</li>
 *   <li>{@code @DefaultValue(field="CONSTANT")} → MyClass.CONSTANT</li>
 *   <li>{@code @DefaultFactory("create")} → MyClass.create()</li>
 *   <li>{@code @DefaultFactory("Util.now")} → Util.now()</li>
 * </ul>
 *
 * @see MethodGenerator
 * @see ConstructorGenerator
 * @see BuilderGenerator
 */
final class CodeGenUtils {

    /**
     * Suffix appended to class/method names when generating builder classes.
     */
    static final String BUILDER_SUFFIX = "Builder";

    /**
     * Parameter/field name for the instance on which methods are invoked.
     */
    static final String INSTANCE_PARAM = "instance";

    private CodeGenUtils() {
    }

    // ==================== PARAMETER COLLECTION ====================

    /**
     * Collects parameter information from a method or constructor.
     *
     * <p>Iterates through all parameters and extracts their default annotations,
     * wrapping each in a {@link ParameterInfo} record for convenient access.
     *
     * <p><b>Example:</b>
     * <p>For method {@code void greet(String name, @DefaultValue("Hello") String greeting)}:
     * <ul>
     *   <li>Parameter 0: {@code ParameterInfo(name, null, null)} — no default</li>
     *   <li>Parameter 1: {@code ParameterInfo(greeting, @DefaultValue("Hello"), null)}</li>
     * </ul>
     *
     * @param executable the method or constructor to analyze
     * @return list of {@link ParameterInfo}, one per parameter, in declaration order
     */
    static List<ParameterInfo> collectParameterInfos(ExecutableElement executable) {
        List<ParameterInfo> paramInfos = new ArrayList<>();
        for (VariableElement param : executable.getParameters()) {
            DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
            DefaultFactory defaultFactory = param.getAnnotation(DefaultFactory.class);
            paramInfos.add(new ParameterInfo(param, defaultValue, defaultFactory));
        }
        return paramInfos;
    }

    /**
     * Collects parameter info for a record, merging annotations from constructor and components.
     *
     * <p>Records can have default annotations on either:
     * <ul>
     *   <li>The canonical constructor parameter</li>
     *   <li>The record component declaration</li>
     * </ul>
     *
     * <p>This method checks both locations, with constructor parameter taking precedence.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * public record Config(
     *     String host,                          // No default
     *     @DefaultValue("8080") int port        // Default on component
     * ) {
     *     public Config(@DefaultValue("") String host, int port) {  // Override on param
     *         // ...
     *     }
     * }
     * // Result: host uses "" (from constructor), port uses "8080" (from component)
     * }</pre>
     *
     * @param constructor the record's canonical constructor
     * @param components  the record component elements (same order as constructor params)
     * @return list of {@link ParameterInfo} with merged annotations
     */
    static List<ParameterInfo> collectRecordParameterInfos(ExecutableElement constructor,
                                                           List<RecordComponentElement> components) {
        List<ParameterInfo> paramInfos = new ArrayList<>();
        List<? extends VariableElement> params = constructor.getParameters();

        for (int i = 0; i < params.size(); i++) {
            VariableElement param = params.get(i);
            RecordComponentElement component = components.get(i);

            // Check parameter first, then record component
            DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
            if (defaultValue == null) {
                defaultValue = component.getAnnotation(DefaultValue.class);
            }
            DefaultFactory defaultFactory = param.getAnnotation(DefaultFactory.class);
            if (defaultFactory == null) {
                defaultFactory = component.getAnnotation(DefaultFactory.class);
            }

            paramInfos.add(new ParameterInfo(param, defaultValue, defaultFactory));
        }
        return paramInfos;
    }

    // ==================== DEFAULT INDEX ====================

    /**
     * Finds the index of the first parameter with a default value.
     *
     * <p>This determines the boundary between required and optional parameters.
     * In non-named mode, overloaded methods are generated from this index onwards.
     *
     * <p><b>Example:</b>
     * <p>For {@code (String a, String b, @DefaultValue("x") String c, @DefaultValue("y") String d)}:
     * <ul>
     *   <li>Returns {@code 2} (index of parameter "c")</li>
     *   <li>Generated overloads: 2 params (a,b), 3 params (a,b,c), 4 params (a,b,c,d)</li>
     * </ul>
     *
     * @param paramInfos list of parameter information in declaration order
     * @return index of first defaulted parameter (0-based), or {@code -1} if no defaults
     */
    static int findFirstDefaultIndex(List<ParameterInfo> paramInfos) {
        for (int i = 0; i < paramInfos.size(); i++) {
            if (paramInfos.get(i).hasDefault()) {
                return i;
            }
        }
        return -1;
    }

    // ==================== ANNOTATION HANDLING ====================

    /**
     * Copies method annotations to a generated method, excluding default4j annotations.
     *
     * <p>Preserves user-defined annotations on the original method so they appear on
     * the generated helper methods. This is important for:
     * <ul>
     *   <li>Nullability annotations ({@code @Nullable}, {@code @NonNull})</li>
     *   <li>Documentation annotations ({@code @Deprecated})</li>
     *   <li>Framework annotations (e.g., Spring's {@code @Transactional})</li>
     * </ul>
     *
     * <p><b>Filtered Annotations:</b>
     * <p>The following default4j annotations are excluded:
     * {@code @WithDefaults}, {@code @DefaultValue}, {@code @DefaultFactory}
     *
     * @param originalMethod the source method to copy annotations from
     * @param methodBuilder  the JavaPoet {@link MethodSpec.Builder} to add annotations to
     */
    static void copyMethodAnnotations(ExecutableElement originalMethod, MethodSpec.Builder methodBuilder) {
        for (AnnotationMirror annotation : originalMethod.getAnnotationMirrors()) {
            String annotationName = annotation.getAnnotationType().toString();
            if (!isDefault4jAnnotation(annotationName)) {
                methodBuilder.addAnnotation(AnnotationSpec.get(annotation));
            }
        }
    }

    /**
     * Adds parameters to a generated method, copying their annotations.
     *
     * <p>Adds the first {@code numProvidedParams} parameters from the source method
     * to the generated method, preserving their annotations. Parameters beyond this
     * count are considered defaulted and not included.
     *
     * <p><b>Example:</b>
     * <p>For {@code greet(@NonNull String name, @DefaultValue("Hi") String greeting)}
     * with {@code numProvidedParams=1}:
     * <pre>{@code
     * // Generated: greet(@NonNull String name)
     * // 'greeting' parameter is omitted (uses default)
     * }</pre>
     *
     * @param methodBuilder     the JavaPoet {@link MethodSpec.Builder} to add parameters to
     * @param paramInfos        the source parameter information
     * @param numProvidedParams number of leading parameters to include (0 to all)
     */
    static void addMethodParameters(MethodSpec.Builder methodBuilder, List<ParameterInfo> paramInfos,
                                    int numProvidedParams) {
        for (int i = 0; i < numProvidedParams; i++) {
            ParameterInfo info = paramInfos.get(i);
            ParameterSpec.Builder paramBuilder = ParameterSpec.builder(
                    TypeName.get(info.element().asType()),
                    info.element().getSimpleName().toString()
            );

            for (AnnotationMirror annotation : info.element().getAnnotationMirrors()) {
                String annotationName = annotation.getAnnotationType().toString();
                if (!isDefault4jAnnotation(annotationName)) {
                    paramBuilder.addAnnotation(AnnotationSpec.get(annotation));
                }
            }

            methodBuilder.addParameter(paramBuilder.build());
        }
    }

    /**
     * Copies throws declarations from a method or constructor to a generated method.
     *
     * <p>Ensures generated wrapper methods declare the same checked exceptions
     * as the original, allowing callers to handle or propagate exceptions correctly.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Original:
     * void connect() throws IOException, TimeoutException
     *
     * // Generated:
     * public static void connect(Service instance) throws IOException, TimeoutException {
     *     instance.connect();
     * }
     * }</pre>
     *
     * @param originalMethod the source method or constructor
     * @param methodBuilder  the JavaPoet {@link MethodSpec.Builder} to add exceptions to
     */
    static void copyThrowsDeclarations(ExecutableElement originalMethod, MethodSpec.Builder methodBuilder) {
        for (TypeMirror thrown : originalMethod.getThrownTypes()) {
            methodBuilder.addException(TypeName.get(thrown));
        }
    }

    // ==================== DEFAULT EXPRESSIONS ====================

    /**
     * Gets the Java expression for a parameter's default value.
     *
     * <p>Transforms default annotations into compilable Java expressions suitable
     * for use in generated code.
     *
     * <p><b>Annotation Types:</b>
     * <table border="1">
     *   <caption>Default annotation to expression mapping</caption>
     *   <tr><th>Annotation</th><th>Result</th></tr>
     *   <tr>
     *     <td>{@code @DefaultValue("42")}</td>
     *     <td>Literal expression (e.g., {@code 42}, {@code "hello"}, {@code 3.14F})</td>
     *   </tr>
     *   <tr>
     *     <td>{@code @DefaultValue(field="CONSTANT")}</td>
     *     <td>Field reference (e.g., {@code MyClass.CONSTANT})</td>
     *   </tr>
     *   <tr>
     *     <td>{@code @DefaultFactory("create")}</td>
     *     <td>Method call (e.g., {@code MyClass.create()})</td>
     *   </tr>
     * </table>
     *
     * <p><b>Reference Resolution:</b>
     * <p>Simple names (without dots) are prefixed with the enclosing class name.
     * Qualified names are used as-is.
     *
     * @param info           the parameter info containing the default annotation
     * @param enclosingClass the class containing the annotated element (for simple name resolution)
     * @return valid Java expression string for use in generated code
     * @throws IllegalStateException if no default annotation is present
     */
    static String getDefaultExpression(ParameterInfo info, ClassName enclosingClass) {
        if (info.defaultValue() != null) {
            DefaultValue dv = info.defaultValue();

            // Check if it's a field reference
            if (!dv.field().isEmpty()) {
                return resolveReference(dv.field(), enclosingClass);
            }

            // It's a literal value
            return convertDefaultValue(dv.value(), info.element().asType());
        }

        if (info.defaultFactory() != null) {
            // Factory method call
            String ref = info.defaultFactory().value();
            return resolveReference(ref, enclosingClass) + "()";
        }

        throw new IllegalStateException("No default annotation found for parameter: " + info.element().getSimpleName());
    }

    /**
     * Resolves a reference string to a fully qualified expression.
     * <p>
     * Handles:
     * <ul>
     *   <li>{@code "name"} → {@code enclosingClass.name}</li>
     *   <li>{@code "Class.name"} → {@code Class.name}</li>
     *   <li>{@code "com.example.Class.name"} → {@code com.example.Class.name}</li>
     * </ul>
     */
    private static String resolveReference(String ref, ClassName enclosingClass) {
        if (ref.contains(".")) {
            // Already qualified (Class.member or fully.qualified.Class.member)
            return ref;
        }
        // Simple name - prefix with enclosing class
        return enclosingClass.canonicalName() + "." + ref;
    }

    // ==================== TYPE CONVERSION ====================

    /**
     * Converts a string default value to the appropriate Java literal expression.
     * <p>
     * Handles type-specific formatting:
     * <ul>
     *   <li>{@code int/Integer}: used as-is (e.g., "42" → {@code 42})</li>
     *   <li>{@code long/Long}: appends L suffix (e.g., "100" → {@code 100L})</li>
     *   <li>{@code double/Double}: ensures decimal point (e.g., "3" → {@code 3.0})</li>
     *   <li>{@code float/Float}: appends F suffix</li>
     *   <li>{@code byte/short}: adds cast (e.g., {@code (byte) 1})</li>
     *   <li>{@code char/Character}: wraps in single quotes</li>
     *   <li>{@code String}: wraps in double quotes with escaping</li>
     *   <li>{@code "null"}: returns literal {@code null}</li>
     * </ul>
     *
     * @param value the string value from {@code @DefaultValue}
     * @param type  the target parameter type
     * @return a valid Java expression for the default value
     * @throws IllegalStateException if value is null or empty for non-String types
     */
    static String convertDefaultValue(String value, TypeMirror type) {
        String typeStr = type.toString();

        if (value == null) {
            throw new IllegalStateException("@DefaultValue value cannot be null");
        }

        // Handle empty string - valid for String type
        if (value.isEmpty()) {
            if (typeStr.equals("java.lang.String")) {
                return "\"\"";
            }
            throw new IllegalStateException("@DefaultValue with empty value is only valid for String type");
        }

        if ("null".equals(value)) {
            return "null";
        }

        return switch (typeStr) {
            case "int", "java.lang.Integer" -> value;
            case "long", "java.lang.Long" -> value.endsWith("L") || value.endsWith("l") ? value : value + "L";
            case "double", "java.lang.Double" -> {
                if (value.endsWith("D") || value.endsWith("d")) {
                    yield value;
                }
                yield value.contains(".") ? value : value + ".0";
            }
            case "float", "java.lang.Float" -> value.endsWith("F") || value.endsWith("f") ? value : value + "F";
            case "boolean", "java.lang.Boolean" -> value;
            case "byte", "java.lang.Byte" -> "(byte) " + value;
            case "short", "java.lang.Short" -> "(short) " + value;
            case "char", "java.lang.Character" -> {
                if (value.length() == 1) {
                    yield "'" + escapeChar(value.charAt(0)) + "'";
                }
                yield value;
            }
            case "java.lang.String" -> "\"" + escapeString(value) + "\"";
            default -> value;
        };
    }

    // ==================== STRING UTILITIES ====================

    /**
     * Escapes special characters in a string for use in a Java string literal.
     * <p>
     * Handles: {@code \n}, {@code \r}, {@code \t}, {@code \\}, {@code \"}, {@code \'}
     *
     * @param s the string to escape
     * @return the escaped string (without surrounding quotes)
     */
    static String escapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(escapeChar(c));
        }
        return sb.toString();
    }

    /**
     * Escapes a single character for use in a Java literal.
     *
     * @param c the character to escape
     * @return the escaped representation (e.g., '\n' → "\\n")
     */
    static String escapeChar(char c) {
        return switch (c) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\\' -> "\\\\";
            case '"' -> "\\\"";
            case '\'' -> "\\'";
            default -> String.valueOf(c);
        };
    }

    /**
     * Capitalizes the first letter of a string.
     * <p>
     * Used for generating class names like "CreateBuilder" from method name "create".
     *
     * @param str the string to capitalize
     * @return the string with first letter uppercase, or unchanged if null/empty
     */
    static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Checks if an annotation name belongs to the default4j library.
     * <p>
     * Used to filter out default4j annotations when copying to generated code.
     *
     * @param annotationName the fully qualified annotation type name
     * @return true if it's a default4j annotation that should be excluded
     */
    private static boolean isDefault4jAnnotation(String annotationName) {
        return annotationName.contains("DefaultValue")
                || annotationName.contains("DefaultFactory")
                || annotationName.contains("WithDefaults");
    }

    // ==================== PARAMETER INFO ====================

    /**
     * Immutable record bundling a parameter element with its default annotations.
     *
     * <p>Created during parameter collection and used throughout code generation
     * to access both the parameter's type information and its default value configuration.
     *
     * <p><b>Usage Pattern:</b>
     * <pre>{@code
     * List<ParameterInfo> infos = collectParameterInfos(method);
     * for (ParameterInfo info : infos) {
     *     if (info.hasDefault()) {
     *         String expr = getDefaultExpression(info, enclosingClass);
     *     }
     * }
     * }</pre>
     *
     * @param element        the parameter's {@link VariableElement} from the AST
     * @param defaultValue   the {@code @DefaultValue} annotation, or {@code null} if absent
     * @param defaultFactory the {@code @DefaultFactory} annotation, or {@code null} if absent
     */
    record ParameterInfo(VariableElement element, DefaultValue defaultValue, DefaultFactory defaultFactory) {
        /**
         * Checks if this parameter has a usable default value.
         *
         * <p>A parameter is considered to have a default if:
         * <ul>
         *   <li>{@code @DefaultFactory} is present (any value), OR</li>
         *   <li>{@code @DefaultValue} is present with non-empty {@code value()}, OR</li>
         *   <li>{@code @DefaultValue} is present with non-empty {@code field()}</li>
         * </ul>
         *
         * <p>Note: {@code @DefaultValue("")} is valid for {@code String} type and returns
         * {@code false} here (no non-empty value or field). The empty string is handled
         * specially in {@link #convertDefaultValue}.
         *
         * @return {@code true} if a default expression can be generated for this parameter
         */
        boolean hasDefault() {
            if (defaultFactory != null) {
                return true;
            }
            if (defaultValue != null) {
                return !defaultValue.value().isEmpty() || !defaultValue.field().isEmpty();
            }
            return false;
        }
    }
}
