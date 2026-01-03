package io.github.reugn.default4j.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.github.reugn.default4j.annotation.IncludeDefaults;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates default helpers for external classes/records via {@code @IncludeDefaults}.
 *
 * <p>This generator handles the case where you cannot modify the original class to add
 * {@code @WithDefaults} annotations (e.g., third-party library classes). Instead, you
 * create a placeholder class with {@code @IncludeDefaults} that defines the defaults
 * using a naming convention.
 *
 * <p><b>Use Case:</b>
 * <pre>{@code
 * // External record you cannot modify (e.g., from a library)
 * public record Config(String host, int port, boolean ssl) {}
 *
 * // Your placeholder class that defines defaults
 * @IncludeDefaults(Config.class)
 * public class ConfigDefaults {
 *     public static final String DEFAULT_HOST = "localhost";
 *     public static final int DEFAULT_PORT = 8080;
 *     public static boolean defaultSsl() { return false; }
 * }
 *
 * // Usage with generated factory methods
 * Config c1 = ConfigDefaults.create();              // ("localhost", 8080, false)
 * Config c2 = ConfigDefaults.create("myhost");      // ("myhost", 8080, false)
 * Config c3 = ConfigDefaults.create("myhost", 443); // ("myhost", 443, false)
 * }</pre>
 *
 * <p><b>Default Value Discovery:</b>
 * <p>Defaults are discovered from the placeholder class using naming conventions:
 *
 * <p><b>Field Convention:</b>
 * <ul>
 *   <li>Pattern: {@code DEFAULT_{componentName}} (case-insensitive, underscores ignored)</li>
 *   <li>Example: {@code DEFAULT_HOST_NAME} matches component {@code hostName}</li>
 *   <li>Must be: {@code static} (can be any visibility)</li>
 * </ul>
 *
 * <p><b>Method Convention:</b>
 * <ul>
 *   <li>Pattern: {@code default{ComponentName}()} (camelCase after "default")</li>
 *   <li>Example: {@code defaultHostName()} matches component {@code hostName}</li>
 *   <li>Must be: {@code static}, no parameters, non-void return</li>
 * </ul>
 *
 * <p><b>Generation Modes:</b>
 * <table border="1">
 *   <caption>Generation modes</caption>
 *   <tr><th>Mode</th><th>Setting</th><th>Generated Code</th></tr>
 *   <tr>
 *     <td><b>Non-named</b></td>
 *     <td>{@code @IncludeDefaults(Config.class)}</td>
 *     <td>Overloaded factory methods: {@code create()}, {@code create(host)}, etc.</td>
 *   </tr>
 *   <tr>
 *     <td><b>Named</b></td>
 *     <td>{@code @IncludeDefaults(value=Config.class, named=true)}</td>
 *     <td>Builder: {@code create().host("x").port(80).build()}</td>
 *   </tr>
 * </table>
 *
 * <p><b>Validation:</b>
 * <p>The generator performs several validations:
 * <ul>
 *   <li>Included type must be a concrete class or record (not interface/abstract)</li>
 *   <li>Included class must have a public constructor</li>
 *   <li>In non-named mode, defaults must be consecutive (no gaps)</li>
 *   <li>Warns if no defaults are found (likely configuration error)</li>
 * </ul>
 *
 * <p><b>Generated Output:</b>
 * <p>For {@code @IncludeDefaults(Config.class)}, generates:
 * <pre>
 * {@code public final class ConfigDefaults {
 *     private ConfigDefaults() {}
 *
 *     public static Config create() { return new Config(...defaults...); }
 *     public static Config create(String host) { return new Config(host, ...defaults...); }
 *     // ... more overloads
 * }}
 * </pre>
 *
 * @see io.github.reugn.default4j.annotation.IncludeDefaults
 * @see BuilderGenerator
 */
final class IncludeGenerator {

    private final ProcessingEnvironment processingEnv;
    private final Elements elementUtils;
    private final Types typeUtils;
    private final ErrorReporter errorReporter;

    /**
     * Creates a new include generator.
     *
     * @param processingEnv the annotation processing environment providing
     *                      access to {@link Elements} and {@link Types} utilities
     * @param errorReporter callback for reporting compilation errors to the user
     */
    IncludeGenerator(ProcessingEnvironment processingEnv, ErrorReporter errorReporter) {
        this.processingEnv = processingEnv;
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.errorReporter = errorReporter;
    }

    // ==================== MAIN ENTRY POINT ====================

    /**
     * Processes an {@code @IncludeDefaults} annotation and generates helper classes.
     *
     * <p>This is the main entry point called by {@link DefaultValueProcessor} for each
     * class annotated with {@code @IncludeDefaults}.
     *
     * <p><b>Processing Pipeline:</b>
     * <p>For each type listed in the annotation's {@code value()} array:
     * <ol>
     *   <li><b>Collect defaults</b> — Scan placeholder class for {@code DEFAULT_*} fields
     *       and {@code default*()} methods</li>
     *   <li><b>Validate type</b> — Ensure it's not an interface/abstract class</li>
     *   <li><b>Validate constructor</b> — Ensure public constructor exists (for classes)</li>
     *   <li><b>Match defaults</b> — Associate discovered defaults with constructor parameters</li>
     *   <li><b>Validate consecutiveness</b> — Check for gaps in non-named mode</li>
     *   <li><b>Generate code</b> — Create {@code {TypeName}Defaults} helper class</li>
     * </ol>
     *
     * <p><b>Multiple Includes:</b>
     * <p>A single {@code @IncludeDefaults} can include multiple types:
     * <pre>
     * {@code @IncludeDefaults({Config.class, Settings.class})
     * public class MyDefaults { ... }}
     * </pre>
     * This generates both {@code ConfigDefaults} and {@code SettingsDefaults}.
     *
     * @param annotatedElement the placeholder class bearing {@code @IncludeDefaults}
     * @throws IOException if writing the generated source file fails
     */
    void processIncludeDefaults(TypeElement annotatedElement) throws IOException {
        IncludeDefaults annotation = annotatedElement.getAnnotation(IncludeDefaults.class);
        if (annotation == null) return;

        // Get the classes to include (via mirror to handle Class<?> at compile time)
        List<TypeMirror> includedTypes = getIncludedTypes(annotatedElement);

        // Collect default values from the annotated class
        Map<String, DefaultSource> defaults = collectDefaults(annotatedElement);

        String packageName = elementUtils.getPackageOf(annotatedElement).getQualifiedName().toString();
        boolean named = annotation.named();
        String methodName = annotation.methodName();

        for (TypeMirror typeMirror : includedTypes) {
            TypeElement includedType = (TypeElement) typeUtils.asElement(typeMirror);
            if (includedType != null) {
                generateForIncludedType(includedType, annotatedElement, defaults, packageName, named, methodName);
            }
        }
    }

    // ==================== DEFAULT COLLECTION ====================

    /**
     * Extracts the included types from {@code @IncludeDefaults.value()}.
     *
     * <p>Annotation processing happens at compile time, before classes are loaded.
     * Direct access to {@code annotation.value()} would attempt to load the referenced
     * classes, causing {@link MirroredTypesException}. This method intentionally
     * catches that exception to extract the type mirrors.
     *
     * <p><b>Why This Pattern?:</b>
     * <pre>{@code
     * // This would fail at compile time:
     * Class<?>[] classes = annotation.value();  // Throws MirroredTypesException
     *
     * // Instead, we catch the exception to get TypeMirror references:
     * try { annotation.value(); }
     * catch (MirroredTypesException e) { return e.getTypeMirrors(); }
     * }</pre>
     *
     * @param annotatedElement the element bearing the {@code @IncludeDefaults} annotation
     * @return list of type mirrors representing the included classes
     * @see <a href="https://docs.oracle.com/javase/8/docs/api/javax/lang/model/type/MirroredTypesException.html">
     * MirroredTypesException Javadoc</a>
     */
    private List<TypeMirror> getIncludedTypes(TypeElement annotatedElement) {
        List<TypeMirror> types = new ArrayList<>();
        try {
            IncludeDefaults annotation = annotatedElement.getAnnotation(IncludeDefaults.class);
            // This will throw MirroredTypesException - value intentionally ignored
            @SuppressWarnings("unused")
            Class<?>[] ignored = annotation.value();
        } catch (MirroredTypesException e) {
            types.addAll(e.getTypeMirrors());
        }
        return types;
    }

    /**
     * Collects default value sources from the placeholder class.
     *
     * <p>Scans all enclosed elements of the placeholder class looking for fields
     * and methods that match the naming conventions for defaults.
     *
     * <p><b>Field Pattern:</b>
     * <ul>
     *   <li>Name starts with {@code DEFAULT_} (case-insensitive)</li>
     *   <li>Must be {@code static}</li>
     *   <li>Example: {@code DEFAULT_HOST} → generates expression {@code "MyClass.DEFAULT_HOST"}</li>
     * </ul>
     *
     * <p><b>Method Pattern:</b>
     * <ul>
     *   <li>Name starts with {@code default} followed by component name</li>
     *   <li>Must be {@code static} with no parameters</li>
     *   <li>Example: {@code defaultHost()} → generates expression {@code "MyClass.defaultHost()"}</li>
     * </ul>
     *
     * <p><b>Name Normalization:</b>
     * <p>Component names are normalized by removing underscores and converting to lowercase.
     * This allows flexible matching:
     * <ul>
     *   <li>{@code DEFAULT_HOST_NAME} matches component {@code hostName}</li>
     *   <li>{@code DEFAULT_HOSTNAME} matches component {@code hostName}</li>
     *   <li>{@code defaultHostName()} matches component {@code hostName}</li>
     * </ul>
     *
     * @param definingClass the placeholder class with {@code @IncludeDefaults}
     * @return map from normalized component name to default source (expression + type)
     */
    private Map<String, DefaultSource> collectDefaults(TypeElement definingClass) {
        Map<String, DefaultSource> defaults = new HashMap<>();

        for (Element enclosed : definingClass.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                if (field.getModifiers().contains(Modifier.STATIC)) {
                    String fieldName = field.getSimpleName().toString();
                    // Match DEFAULT_xxx or DEFAULT_XXX pattern
                    if (fieldName.toUpperCase(Locale.ROOT).startsWith("DEFAULT_")) {
                        String componentName = normalizeComponentName(
                                fieldName.substring("DEFAULT_".length())
                        );
                        String className = definingClass.getQualifiedName().toString();
                        defaults.put(componentName, new DefaultSource(
                                className + "." + fieldName,
                                DefaultSourceType.FIELD,
                                fieldName
                        ));
                    }
                }
            } else if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                if (method.getModifiers().contains(Modifier.STATIC)
                        && method.getParameters().isEmpty()) {
                    String methodName = method.getSimpleName().toString();
                    // Match defaultXxx pattern
                    if (methodName.startsWith("default") && methodName.length() > 7) {
                        String componentName = normalizeComponentName(
                                methodName.substring("default".length())
                        );
                        String className = definingClass.getQualifiedName().toString();
                        defaults.put(componentName, new DefaultSource(
                                className + "." + methodName + "()",
                                DefaultSourceType.METHOD,
                                methodName + "()"
                        ));
                    }
                }
            }
        }

        return defaults;
    }

    /**
     * Normalizes a component name for case-insensitive, underscore-insensitive matching.
     *
     * <p>Removes all underscores and converts to lowercase, enabling flexible matching
     * between different naming conventions.
     *
     * <p><b>Examples:</b>
     * <ul>
     *   <li>{@code "HOST_NAME"} → {@code "hostname"}</li>
     *   <li>{@code "HostName"} → {@code "hostname"}</li>
     *   <li>{@code "hostName"} → {@code "hostname"}</li>
     *   <li>{@code "HOSTNAME"} → {@code "hostname"}</li>
     * </ul>
     *
     * @param name the component name to normalize (from field suffix or method suffix)
     * @return lowercase name with underscores removed
     */
    private String normalizeComponentName(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    // ==================== CODE GENERATION ====================

    /**
     * Generates the defaults helper class for a single included type.
     *
     * <p>This method handles the complete generation pipeline for one included type,
     * from validation through code generation and file writing.
     *
     * <p><b>Validation Steps:</b>
     * <ol>
     *   <li>Type must not be an interface or abstract class</li>
     *   <li>Class (non-record) must have a public constructor</li>
     *   <li>In non-named mode, defaults must be consecutive</li>
     * </ol>
     *
     * <p><b>Generation Steps:</b>
     * <ol>
     *   <li>Extract constructor/record components</li>
     *   <li>Match collected defaults to components by normalized name</li>
     *   <li>Generate either overloaded factories or builder (based on mode)</li>
     *   <li>Write {@code {TypeName}Defaults.java} to the filer</li>
     * </ol>
     *
     * <p><b>Generated Class Structure:</b>
     * <pre>
     * {@code public final class ConfigDefaults {
     *     private ConfigDefaults() {}  // Utility class
     *     // ... factory methods or builder ...
     * }}
     * </pre>
     *
     * @param includedType      the external type to generate helpers for
     * @param definingClass     the placeholder class bearing {@code @IncludeDefaults}
     * @param defaults          map of normalized component names to default sources
     * @param packageName       package for the generated class (same as placeholder)
     * @param named             {@code true} for builder pattern, {@code false} for overloads
     * @param factoryMethodName the name for factory methods (e.g., "create", "of")
     * @throws IOException if writing the generated source file fails
     */
    private void generateForIncludedType(TypeElement includedType,
                                         TypeElement definingClass,
                                         Map<String, DefaultSource> defaults,
                                         String packageName,
                                         boolean named,
                                         String factoryMethodName) throws IOException {
        String simpleClassName = includedType.getSimpleName().toString();
        String generatedClassName = simpleClassName + "Defaults";
        ClassName includedTypeName = ClassName.get(includedType);

        // Validation 9: Check if included type is interface or abstract
        if (!ValidationUtils.validateIncludedType(includedType, definingClass, errorReporter)) {
            return;
        }

        // Validation 4: Check if included class has a public constructor (non-records)
        if (includedType.getKind() != ElementKind.RECORD) {
            if (!hasPublicConstructor(includedType)) {
                errorReporter.error(definingClass,
                        includedType.getSimpleName() + " has no public constructor. " +
                                "@IncludeDefaults requires a public constructor to generate factory methods.");
                return;
            }
        }

        // Get components/parameters (uses best-matching constructor for classes)
        List<ComponentInfo> components = getComponents(includedType, defaults);
        if (components.isEmpty()) {
            return;
        }

        // Match defaults to components and track which defaults are used
        int matchedDefaults = 0;
        java.util.Set<String> matchedDefaultNames = new java.util.HashSet<>();
        for (ComponentInfo comp : components) {
            String normalizedName = normalizeComponentName(comp.name);
            DefaultSource source = defaults.get(normalizedName);
            if (source != null) {
                comp.defaultExpression = source.expression;
                matchedDefaults++;
                matchedDefaultNames.add(normalizedName);
            }
        }

        // Validation 3: Warn if no defaults are defined
        if (matchedDefaults == 0) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "No defaults found for " + includedType.getSimpleName() + ". " +
                            "Define DEFAULT_{component} fields or default{Component}() methods in " +
                            definingClass.getSimpleName() + ".",
                    definingClass);
        }

        // Warn about unused defaults (defined but don't match any constructor parameter)
        for (Map.Entry<String, DefaultSource> entry : defaults.entrySet()) {
            if (!matchedDefaultNames.contains(entry.getKey())) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        "Default '" + entry.getValue().originalName() + "' does not match any parameter in " +
                                includedType.getSimpleName() + "'s selected constructor. " +
                                "Check parameter names or constructor signature.",
                        definingClass);
            }
        }

        // Validate consecutive defaults for non-named mode
        if (!named) {
            List<String> componentNames = components.stream().map(c -> c.name).toList();
            boolean valid = ValidationUtils.validateConsecutiveDefaultsForInclude(
                    componentNames,
                    i -> components.get(i).defaultExpression != null,
                    definingClass,
                    includedType.getSimpleName().toString(),
                    errorReporter);
            if (!valid) {
                return; // Error already reported
            }
        }

        // Build the Defaults class
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Generated default helpers for {@link $T}.\n", includedTypeName)
                .addJavadoc("<p>Generated by default4j from {@link $T}.\n",
                        ClassName.get(definingClass));

        // Private constructor
        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build());

        if (named) {
            generateNamedBuilder(classBuilder, components, includedTypeName, factoryMethodName);
        } else {
            generateOverloadedFactories(classBuilder, components, includedTypeName, factoryMethodName);
        }

        TypeSpec generatedClass = classBuilder.build();
        JavaFile javaFile = JavaFile.builder(packageName, generatedClass)
                .indent("    ")
                .build();

        javaFile.writeTo(processingEnv.getFiler());
    }

    /**
     * Extracts constructor parameters or record components from a type.
     *
     * <p>Different extraction strategies are used based on the type kind:
     *
     * <p><b>Records:</b>
     * <p>Uses {@link TypeElement#getRecordComponents()} to get the canonical
     * constructor parameters in declaration order.
     *
     * <p><b>Classes:</b>
     * <p>Finds the public constructor that best matches the defined defaults using
     * {@link #findBestMatchingConstructor}. This ensures that when a class has
     * multiple constructors, the one most aligned with user-defined defaults is chosen.
     *
     * <p><b>Returned Info:</b>
     * <p>Each {@link ComponentInfo} contains:
     * <ul>
     *   <li>{@code name} — parameter/component name</li>
     *   <li>{@code type} — JavaPoet {@link TypeName} for code generation</li>
     *   <li>{@code defaultExpression} — initially {@code null}, populated later by matching</li>
     * </ul>
     *
     * @param type     the type to extract components from
     * @param defaults map of normalized component names to default sources
     * @return ordered list of component info; empty if no suitable constructor found
     */
    private List<ComponentInfo> getComponents(TypeElement type, Map<String, DefaultSource> defaults) {
        List<ComponentInfo> components = new ArrayList<>();

        if (type.getKind() == ElementKind.RECORD) {
            // For records, use record components
            for (RecordComponentElement comp : type.getRecordComponents()) {
                components.add(new ComponentInfo(
                        comp.getSimpleName().toString(),
                        TypeName.get(comp.asType()),
                        null
                ));
            }
        } else {
            // For classes, find the best matching public constructor
            ExecutableElement bestConstructor = findBestMatchingConstructor(type, defaults);
            if (bestConstructor != null) {
                for (VariableElement param : bestConstructor.getParameters()) {
                    components.add(new ComponentInfo(
                            param.getSimpleName().toString(),
                            TypeName.get(param.asType()),
                            null
                    ));
                }
            }
        }

        return components;
    }

    /**
     * Finds the public constructor that best matches the defined defaults.
     *
     * <p>When a class has multiple public constructors, this method scores each one
     * based on how many of its parameters have matching defaults defined. This ensures
     * that user intent is respected — if they defined {@code DEFAULT_HOSTNAME} and
     * {@code DEFAULT_PORT}, the constructor with both {@code hostname} and {@code port}
     * parameters is preferred over one with only {@code port}.
     *
     * <p><b>Scoring Algorithm:</b>
     * <ol>
     *   <li>For each public constructor, count parameters with matching defaults</li>
     *   <li>Select the constructor with the highest match count</li>
     *   <li>On tie, prefer the constructor with more parameters (more complete API)</li>
     * </ol>
     *
     * <p><b>Example:</b>
     * <p>Given class with constructors:
     * <ul>
     *   <li>{@code Foo(int port)} — 1 parameter</li>
     *   <li>{@code Foo(String host, int port)} — 2 parameters</li>
     * </ul>
     * And defaults: {@code DEFAULT_HOST}, {@code DEFAULT_PORT}
     * <ul>
     *   <li>First constructor scores 1 (port matches)</li>
     *   <li>Second constructor scores 2 (host and port match)</li>
     * </ul>
     * Result: Second constructor is selected.
     *
     * @param type     the class to find a constructor for
     * @param defaults map of normalized component names to default sources
     * @return the best matching public constructor, or {@code null} if none found
     */
    private ExecutableElement findBestMatchingConstructor(TypeElement type, Map<String, DefaultSource> defaults) {
        ExecutableElement best = null;
        int bestScore = -1;
        int bestParamCount = -1;

        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement constructor = (ExecutableElement) enclosed;
                if (constructor.getModifiers().contains(Modifier.PUBLIC)) {
                    // Score: count parameters with matching defaults
                    int score = 0;
                    for (VariableElement param : constructor.getParameters()) {
                        String normalized = normalizeComponentName(param.getSimpleName().toString());
                        if (defaults.containsKey(normalized)) {
                            score++;
                        }
                    }
                    int paramCount = constructor.getParameters().size();

                    // Prefer higher score, then more parameters on tie
                    if (score > bestScore || (score == bestScore && paramCount > bestParamCount)) {
                        best = constructor;
                        bestScore = score;
                        bestParamCount = paramCount;
                    }
                }
            }
        }

        return best;
    }

    /**
     * Checks if a class has at least one public constructor.
     *
     * <p>Generated factory methods call the constructor directly, so at least one
     * public constructor must exist for the generated code to compile.
     *
     * <p>Note: This check is skipped for records, which always have an implicit
     * public canonical constructor.
     *
     * @param type the class to check
     * @return {@code true} if at least one public constructor exists
     */
    private boolean hasPublicConstructor(TypeElement type) {
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement constructor = (ExecutableElement) enclosed;
                if (constructor.getModifiers().contains(Modifier.PUBLIC)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Generates overloaded static factory methods (non-named mode).
     *
     * <p>Creates multiple factory methods with the same name but different parameter
     * counts, allowing callers to omit trailing parameters that have defaults.
     *
     * <p><b>Example Generated Methods:</b>
     * <p>For {@code Config(String host, int port, boolean ssl)} with port and ssl having defaults:
     * <pre>{@code
     * public static Config create(String host, int port, boolean ssl) {
     *     return new Config(host, port, ssl);
     * }
     * public static Config create(String host, int port) {
     *     return new Config(host, port, ConfigDefaults.DEFAULT_SSL);
     * }
     * public static Config create(String host) {
     *     return new Config(host, ConfigDefaults.DEFAULT_PORT, ConfigDefaults.DEFAULT_SSL);
     * }
     * }</pre>
     *
     * <p><b>Overload Generation:</b>
     * <p>Generates overloads from {@code components.size()} parameters down to
     * {@code firstDefaultIndex} parameters. Each omitted parameter uses its
     * default expression in the constructor call.
     *
     * @param classBuilder the {@link TypeSpec.Builder} to add factory methods to
     * @param components   ordered list of constructor/record components with their defaults
     * @param targetType   the {@link ClassName} of the type being constructed
     * @param methodName   the name for all factory methods (e.g., "create")
     */
    private void generateOverloadedFactories(TypeSpec.Builder classBuilder,
                                             List<ComponentInfo> components,
                                             ClassName targetType,
                                             String methodName) {
        // Find first component with default
        int firstDefaultIndex = -1;
        for (int i = 0; i < components.size(); i++) {
            if (components.get(i).defaultExpression != null) {
                firstDefaultIndex = i;
                break;
            }
        }

        if (firstDefaultIndex == -1) {
            // No defaults - just generate pass-through
            firstDefaultIndex = components.size();
        }

        // Generate overloads from firstDefaultIndex down to required params only
        for (int numProvided = components.size(); numProvided >= firstDefaultIndex; numProvided--) {
            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(targetType);

            // Add parameters up to numProvided
            List<String> args = new ArrayList<>();
            for (int i = 0; i < components.size(); i++) {
                ComponentInfo comp = components.get(i);
                if (i < numProvided) {
                    methodBuilder.addParameter(ParameterSpec.builder(comp.type, comp.name).build());
                    args.add(comp.name);
                } else {
                    if (comp.defaultExpression != null) {
                        args.add(comp.defaultExpression);
                    } else {
                        // Required param after optional - shouldn't happen with consecutive defaults
                        args.add("null /* missing default for " + comp.name + " */");
                    }
                }
            }

            methodBuilder.addStatement("return new $T($L)", targetType, String.join(", ", args));
            classBuilder.addMethod(methodBuilder.build());
        }
    }

    /**
     * Generates a builder class for named parameter construction.
     *
     * <p>Delegates to {@link BuilderGenerator} to create an inner builder class
     * with fluent setter methods for each component. Components with defaults
     * are pre-initialized in the builder's field declarations.
     *
     * <p><b>Example Generated Code:</b>
     * <pre>{@code
     * public static ConfigBuilder create() {
     *     return new ConfigBuilder();
     * }
     *
     * public static final class ConfigBuilder {
     *     private String host;
     *     private int port = ConfigDefaults.DEFAULT_PORT;
     *     private boolean ssl = ConfigDefaults.defaultSsl();
     *
     *     public ConfigBuilder host(String host) { this.host = host; return this; }
     *     public ConfigBuilder port(int port) { this.port = port; return this; }
     *     public ConfigBuilder ssl(boolean ssl) { this.ssl = ssl; return this; }
     *
     *     public Config build() { return new Config(host, port, ssl); }
     * }
     * }</pre>
     *
     * <p><b>Builder Naming:</b>
     * <p>The builder is named after the target type (e.g., {@code ConfigBuilder}),
     * not the factory method name. The factory method name only affects the
     * entry point method (e.g., {@code create()}, {@code of()}).
     *
     * @param classBuilder the {@link TypeSpec.Builder} to add the builder class to
     * @param components   ordered list of constructor/record components with their defaults
     * @param targetType   the {@link ClassName} of the type being constructed
     * @param methodName   the name for the factory method that returns the builder
     */
    private void generateNamedBuilder(TypeSpec.Builder classBuilder,
                                      List<ComponentInfo> components,
                                      ClassName targetType,
                                      String methodName) {
        // Builder named after the target type, not the factory method
        String builderClassName = targetType.simpleName() + CodeGenUtils.BUILDER_SUFFIX;
        ClassName builderType = ClassName.get("", builderClassName);

        // Convert to FieldInfo for shared builder generation
        List<BuilderGenerator.FieldInfo> fieldInfos = components.stream()
                .map(c -> new BuilderGenerator.FieldInfo(c.name, c.type, c.defaultExpression))
                .toList();

        // Use shared builder generator
        TypeSpec builderClass = BuilderGenerator.createConstructorBuilder(
                fieldInfos, targetType, builderClassName, builderType);
        classBuilder.addType(builderClass);

        // Factory method that returns builder
        classBuilder.addMethod(BuilderGenerator.createBuilderFactoryMethod(methodName, builderType));
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Indicates the source type of a discovered default value.
     *
     * <p>Used to distinguish between field-based and method-based defaults,
     * which have different expression formats in the generated code.
     */
    private enum DefaultSourceType {
        /**
         * A static field following the {@code DEFAULT_{name}} pattern.
         * <p>
         * Generated expression: {@code "ClassName.DEFAULT_NAME"}
         */
        FIELD,

        /**
         * A static no-arg method following the {@code default{Name}()} pattern.
         * <p>
         * Generated expression: {@code "ClassName.defaultName()"}
         */
        METHOD
    }

    /**
     * Holds information about a constructor parameter or record component.
     *
     * <p>Created during component extraction with {@code defaultExpression = null}.
     * The default expression is populated later when matching against collected
     * defaults from the placeholder class.
     *
     * <p><b>Lifecycle:</b>
     * <ol>
     *   <li>Created by {@link #getComponents} with {@code defaultExpression = null}</li>
     *   <li>Matched by {@link #generateForIncludedType} — sets {@code defaultExpression}
     *       if a matching default source is found</li>
     *   <li>Used by {@link #generateOverloadedFactories} or {@link #generateNamedBuilder}
     *       to generate code</li>
     * </ol>
     */
    private static class ComponentInfo {
        /**
         * The parameter/component name (e.g., "host", "port").
         */
        final String name;

        /**
         * The JavaPoet type for code generation.
         */
        final TypeName type;

        /**
         * The default value expression, or {@code null} if this is a required parameter.
         * <p>
         * Examples:
         * <ul>
         *   <li>{@code "ConfigDefaults.DEFAULT_HOST"} — field reference</li>
         *   <li>{@code "ConfigDefaults.defaultPort()"} — method call</li>
         * </ul>
         */
        String defaultExpression;

        ComponentInfo(String name, TypeName type, String defaultExpression) {
            this.name = name;
            this.type = type;
            this.defaultExpression = defaultExpression;
        }
    }

    /**
     * Represents a discovered default value source from the placeholder class.
     *
     * <p>Captured during the default collection phase and used during code generation
     * to produce the correct expression for invoking the default.
     *
     * @param expression   the fully-qualified Java expression to use in generated code
     *                     (e.g., {@code "com.example.ConfigDefaults.DEFAULT_HOST"} or
     *                     {@code "com.example.ConfigDefaults.defaultHost()"})
     * @param type         indicates whether this default came from a field or method,
     *                     which may be useful for diagnostics or optimization
     * @param originalName the original field or method name as declared in source code
     *                     (e.g., {@code "DEFAULT_PORT_NUM"} or {@code "defaultPortNum"}),
     *                     used for user-friendly error messages
     */
    private record DefaultSource(String expression, DefaultSourceType type, String originalName) {
    }
}
