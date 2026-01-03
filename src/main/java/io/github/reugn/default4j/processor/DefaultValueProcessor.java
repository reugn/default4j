package io.github.reugn.default4j.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import io.github.reugn.default4j.annotation.DefaultFactory;
import io.github.reugn.default4j.annotation.DefaultValue;
import io.github.reugn.default4j.annotation.IncludeDefaults;
import io.github.reugn.default4j.annotation.WithDefaults;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.reugn.default4j.processor.CodeGenUtils.capitalize;

/**
 * Main annotation processor for default4j — generates helper classes for default parameter values.
 *
 * <p>This is the entry point for the default4j annotation processor, registered via
 * {@link com.google.auto.service.AutoService} for automatic discovery by the Java compiler.
 *
 * <p><b>Supported Annotations:</b>
 * <table border="1">
 *   <caption>Annotations processed by this processor</caption>
 *   <tr><th>Annotation</th><th>Target</th><th>Purpose</th></tr>
 *   <tr>
 *     <td>{@link WithDefaults}</td>
 *     <td>Method, Constructor, Class, Record</td>
 *     <td>Enables default value generation for parameters</td>
 *   </tr>
 *   <tr>
 *     <td>{@link IncludeDefaults}</td>
 *     <td>Class</td>
 *     <td>Defines defaults for external types you cannot modify</td>
 *   </tr>
 * </table>
 *
 * <p><b>Generated Output:</b>
 * <p>For each class with {@code @WithDefaults} annotations, generates a utility class:
 * <pre>
 * {@code // Source
 * public class Greeter {
 *     {@literal @}WithDefaults
 *     public void greet(String name, @DefaultValue("Hello") String greeting) { }
 * }
 *
 * // Generated: GreeterDefaults.java
 * public final class GreeterDefaults {
 *     public static void greet(Greeter instance, String name) { ... }
 *     public static void greet(Greeter instance, String name, String greeting) { ... }
 * }}
 * </pre>
 *
 * <p><b>Processing Pipeline:</b>
 * <ol>
 *   <li><b>Collection</b> — Gather all {@code @WithDefaults} elements, group by enclosing class</li>
 *   <li><b>Validation</b> — Check annotation consistency, literal parseability, reference validity</li>
 *   <li><b>Generation</b> — Create {@code {ClassName}Defaults} class with helpers</li>
 *   <li><b>Include Processing</b> — Handle {@code @IncludeDefaults} for external types</li>
 * </ol>
 *
 * <p><b>Generation Modes:</b>
 * <ul>
 *   <li><b>Non-named (default)</b>: Generates overloaded methods/factories with trailing defaults</li>
 *   <li><b>Named</b>: Generates fluent builder classes for flexible parameter setting</li>
 * </ul>
 *
 * <p><b>Record Support:</b>
 * <p>For Java records, {@link DefaultValue} and {@link DefaultFactory} can be placed
 * on record component declarations:
 * <pre>{@code
 * @WithDefaults
 * public record Config(String host, @DefaultValue("8080") int port) {}
 * }</pre>
 *
 * <p><b>Delegation:</b>
 * <p>Code generation is delegated to specialized generators:
 * <ul>
 *   <li>{@link MethodGenerator} — instance method helpers</li>
 *   <li>{@link ConstructorGenerator} — factory methods and builders</li>
 *   <li>{@link IncludeGenerator} — external type defaults</li>
 *   <li>{@link BuilderGenerator} — shared builder logic</li>
 * </ul>
 *
 * @see MethodGenerator
 * @see ConstructorGenerator
 * @see IncludeGenerator
 * @see ValidationUtils
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "io.github.reugn.default4j.annotation.WithDefaults",
        "io.github.reugn.default4j.annotation.IncludeDefaults"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class DefaultValueProcessor extends AbstractProcessor {

    private ErrorReporter errorReporter;
    private IncludeGenerator includeGenerator;
    private Types typeUtils;
    private javax.lang.model.util.Elements elementUtils;

    /**
     * Creates a new DefaultValueProcessor instance.
     *
     * <p>This no-arg constructor is required for annotation processor discovery via
     * {@link java.util.ServiceLoader}. The processor is not usable until
     * {@link #init(ProcessingEnvironment)} is called by the compiler.
     */
    public DefaultValueProcessor() {
        // Required for ServiceLoader-based processor discovery
    }

    /**
     * Initializes the processor with the processing environment.
     *
     * <p>Called by the Java compiler before any processing rounds. Sets up:
     * <ul>
     *   <li>{@link ErrorReporter} — for reporting compilation errors to the user</li>
     *   <li>{@link IncludeGenerator} — for handling {@code @IncludeDefaults} annotations</li>
     *   <li>{@link Types} — for type assignability checks during validation</li>
     *   <li>{@link javax.lang.model.util.Elements} — for resolving external class references</li>
     * </ul>
     *
     * @param processingEnv the environment providing access to compiler facilities
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.errorReporter = (element, message) ->
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
        this.includeGenerator = new IncludeGenerator(processingEnv, errorReporter);
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
    }

    /**
     * Processes {@code @WithDefaults} and {@code @IncludeDefaults} annotations.
     *
     * <p>Called by the compiler for each processing round. A round may contain:
     * <ul>
     *   <li>Source files from the compilation</li>
     *   <li>Source files generated by previous processor rounds</li>
     * </ul>
     *
     * <p><b>Processing Steps:</b></p>
     * <ol>
     *   <li><b>Collect</b> — Gather all {@code @WithDefaults} elements, grouped by enclosing class</li>
     *   <li><b>Generate</b> — Create {@code {ClassName}Defaults} for each collected class</li>
     *   <li><b>Include</b> — Process {@code @IncludeDefaults} via {@link IncludeGenerator}</li>
     * </ol>
     *
     * <p><b>Error Handling:</b></p>
     * Errors during validation or generation are reported via the {@link Messager}
     * and processing continues to report as many errors as possible in a single compilation.
     *
     * @param annotations the annotation types being processed in this round
     * @param roundEnv    the environment for this processing round
     * @return {@code true} to claim the annotations, preventing other processors from handling them
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Process @WithDefaults
        Map<TypeElement, ClassInfo> classInfoMap = collectAnnotatedElements(roundEnv);

        for (Map.Entry<TypeElement, ClassInfo> entry : classInfoMap.entrySet()) {
            try {
                generateDefaultsClass(entry.getValue());
            } catch (IOException e) {
                errorReporter.error(entry.getKey(), "Failed to generate defaults class: " + e.getMessage());
            }
        }

        // Process @IncludeDefaults
        for (Element element : roundEnv.getElementsAnnotatedWith(IncludeDefaults.class)) {
            if (element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE) {
                try {
                    includeGenerator.processIncludeDefaults((TypeElement) element);
                } catch (IOException e) {
                    errorReporter.error(element, "Failed to generate included defaults: " + e.getMessage());
                }
            }
        }

        return true;
    }

    // ==================== COLLECTION ====================

    /**
     * Collects all {@code @WithDefaults}-annotated elements and groups them by enclosing class.
     *
     * <p>This is the first phase of processing, gathering all elements that need code generation
     * and organizing them by their enclosing class (since we generate one helper class per source class).
     *
     * <p><b>Supported Element Kinds:</b>
     * <table border="1">
     *   <caption>Supported element kinds and their processing behavior</caption>
     *   <tr><th>Kind</th><th>Behavior</th></tr>
     *   <tr>
     *     <td>{@code METHOD}</td>
     *     <td>Added directly with its own {@code @WithDefaults} settings</td>
     *   </tr>
     *   <tr>
     *     <td>{@code CONSTRUCTOR}</td>
     *     <td>Added directly with its own {@code @WithDefaults} settings</td>
     *   </tr>
     *   <tr>
     *     <td>{@code CLASS}</td>
     *     <td>Scans enclosed methods/constructors with {@code @DefaultValue}/{@code @DefaultFactory}
     *         parameters, using the class-level annotation settings</td>
     *   </tr>
     *   <tr>
     *     <td>{@code RECORD}</td>
     *     <td>Processes the canonical constructor using class-level settings;
     *         reads defaults from record component annotations</td>
     *   </tr>
     * </table>
     *
     * <p><b>Annotation Precedence:</b>
     * <p>Method/constructor-level {@code @WithDefaults} takes precedence over class-level:
     * <pre>{@code
     * @WithDefaults(named = true)  // Class-level: use builders
     * public class Service {
     *     @WithDefaults(named = false)  // Override: use overloads for this method
     *     public void process(@DefaultValue("x") String s) { }
     * }
     * }</pre>
     *
     * <p><b>Validation:</b>
     * <p>Each element is validated before collection:
     * <ul>
     *   <li>Parameter annotations checked via {@link ValidationUtils#validateParameterAnnotations}</li>
     *   <li>Visibility checked via {@link ValidationUtils#validateElementVisibility}</li>
     *   <li>Record components validated via {@link ValidationUtils#validateRecordComponents}</li>
     * </ul>
     *
     * @param roundEnv the round environment providing access to annotated elements
     * @return map from each enclosing class to its collected methods and constructors;
     * uses {@link LinkedHashMap} to preserve processing order
     */
    private Map<TypeElement, ClassInfo> collectAnnotatedElements(RoundEnvironment roundEnv) {
        Map<TypeElement, ClassInfo> classInfoMap = new LinkedHashMap<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(WithDefaults.class)) {
            switch (element.getKind()) {
                case METHOD -> {
                    ExecutableElement method = (ExecutableElement) element;
                    TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();
                    // Validate annotations
                    ValidationUtils.validateParameterAnnotations(method, enclosingClass, errorReporter,
                            typeUtils, elementUtils);
                    // Validate visibility (error for private)
                    ValidationUtils.validateElementVisibility(method, errorReporter);
                    classInfoMap.computeIfAbsent(enclosingClass, ClassInfo::new).addMethod(method);
                }
                case CONSTRUCTOR -> {
                    ExecutableElement constructor = (ExecutableElement) element;
                    TypeElement enclosingClass = (TypeElement) constructor.getEnclosingElement();
                    // Validate annotations
                    ValidationUtils.validateParameterAnnotations(constructor, enclosingClass, errorReporter,
                            typeUtils, elementUtils);
                    // Validate visibility (error for private)
                    ValidationUtils.validateElementVisibility(constructor, errorReporter);
                    classInfoMap.computeIfAbsent(enclosingClass, ClassInfo::new).addConstructor(constructor);
                }
                case CLASS -> {
                    TypeElement classElement = (TypeElement) element;
                    WithDefaults classAnnotation = classElement.getAnnotation(WithDefaults.class);
                    ClassInfo info = classInfoMap.computeIfAbsent(classElement, ClassInfo::new);

                    for (Element enclosed : classElement.getEnclosedElements()) {
                        if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                            ExecutableElement constructor = (ExecutableElement) enclosed;
                            // Skip if constructor has its own @WithDefaults (processed separately with its own settings)
                            if (constructor.getAnnotation(WithDefaults.class) != null) {
                                continue;
                            }
                            if (hasDefaultValueParams(constructor)) {
                                // Validate annotations
                                ValidationUtils.validateParameterAnnotations(constructor, classElement, errorReporter,
                                        typeUtils, elementUtils);
                                info.addConstructorFromClass(constructor, classAnnotation);
                            }
                        } else if (enclosed.getKind() == ElementKind.METHOD) {
                            ExecutableElement method = (ExecutableElement) enclosed;
                            // Skip if method has its own @WithDefaults (processed separately with its own settings)
                            if (method.getAnnotation(WithDefaults.class) != null) {
                                continue;
                            }
                            if (hasDefaultValueParams(method)) {
                                // Validate annotations
                                ValidationUtils.validateParameterAnnotations(method, classElement, errorReporter,
                                        typeUtils, elementUtils);
                                info.addMethodFromClass(method, classAnnotation);
                            }
                        }
                    }
                }
                case RECORD -> {
                    TypeElement recordElement = (TypeElement) element;
                    WithDefaults classAnnotation = recordElement.getAnnotation(WithDefaults.class);
                    ClassInfo info = classInfoMap.computeIfAbsent(recordElement, ClassInfo::new);
                    info.isRecord = true;

                    // Validate record component annotations
                    ValidationUtils.validateRecordComponents(recordElement,
                            recordElement.getEnclosedElements(), errorReporter, typeUtils, elementUtils);

                    // For records, find the canonical constructor
                    ExecutableElement canonicalConstructor = findCanonicalConstructor(recordElement);
                    if (canonicalConstructor != null) {
                        // Check if record components have @DefaultValue
                        if (hasDefaultValueRecordComponents(recordElement) || hasDefaultValueParams(canonicalConstructor)) {
                            info.addConstructorFromClass(canonicalConstructor, classAnnotation);
                            info.recordComponents = getRecordComponents(recordElement);
                        }
                    }
                }
                default -> errorReporter.error(element,
                        "@WithDefaults can only be applied to methods, constructors, classes, or records");
            }
        }

        return classInfoMap;
    }

    /**
     * Checks if any parameter of a method or constructor has a default annotation.
     *
     * <p>Used when processing class-level {@code @WithDefaults} to identify which
     * methods/constructors should be included in generation.
     *
     * @param executable the method or constructor to check
     * @return {@code true} if at least one parameter has {@code @DefaultValue} or {@code @DefaultFactory}
     */
    private boolean hasDefaultValueParams(ExecutableElement executable) {
        for (VariableElement param : executable.getParameters()) {
            if (param.getAnnotation(DefaultValue.class) != null ||
                    param.getAnnotation(DefaultFactory.class) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if any record component has a default annotation.
     *
     * <p>For records, defaults can be specified on the component declaration rather than
     * (or in addition to) the canonical constructor parameter. This method checks the
     * component declarations.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * public record Config(
     *     String host,
     *     @DefaultValue("8080") int port  // This would return true
     * ) {}
     * }</pre>
     *
     * @param recordElement the record type to check
     * @return {@code true} if at least one component has {@code @DefaultValue} or {@code @DefaultFactory}
     */
    private boolean hasDefaultValueRecordComponents(TypeElement recordElement) {
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.RECORD_COMPONENT) {
                if (enclosed.getAnnotation(DefaultValue.class) != null ||
                        enclosed.getAnnotation(DefaultFactory.class) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds the canonical constructor of a record.
     *
     * <p>Every record has a canonical constructor whose signature matches the record
     * component declarations. This constructor may be implicitly generated or explicitly
     * declared.
     *
     * <p><b>Identification:</b>
     * <p>The canonical constructor is identified by:
     * <ol>
     *   <li>Same number of parameters as record components</li>
     *   <li>Parameter types match component types in declaration order</li>
     * </ol>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * public record Point(int x, int y) { }
     * // Canonical constructor: Point(int x, int y)
     *
     * public record Config(String host, int port) {
     *     public Config {  // Compact canonical constructor
     *         host = host.toLowerCase();
     *     }
     * }
     * }</pre>
     *
     * @param recordElement the record type to search
     * @return the canonical constructor element, or {@code null} if not found
     * (should not happen for valid records)
     */
    private ExecutableElement findCanonicalConstructor(TypeElement recordElement) {
        List<RecordComponentElement> components = getRecordComponents(recordElement);
        int componentCount = components.size();

        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement constructor = (ExecutableElement) enclosed;
                List<? extends VariableElement> params = constructor.getParameters();

                // Canonical constructor has same number of params as components
                if (params.size() == componentCount) {
                    boolean matches = true;
                    for (int i = 0; i < componentCount; i++) {
                        if (!params.get(i).asType().equals(components.get(i).asType())) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {
                        return constructor;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extracts record component elements from a record type.
     *
     * <p>Record components are extracted by scanning enclosed elements and filtering
     * for {@code RECORD_COMPONENT} kind. The returned list maintains declaration order,
     * which is essential for matching with constructor parameters.
     *
     * <p><b>Note:</b>
     * Java 16+ provides {@link TypeElement#getRecordComponents()}, but we use manual
     * scanning for broader compatibility with annotation processing APIs.
     *
     * @param recordElement the record type to extract components from
     * @return list of record components in declaration order; empty list if none found
     */
    private List<RecordComponentElement> getRecordComponents(TypeElement recordElement) {
        List<RecordComponentElement> components = new ArrayList<>();
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.RECORD_COMPONENT) {
                components.add((RecordComponentElement) enclosed);
            }
        }
        return components;
    }

    // ==================== GENERATION ====================

    /**
     * Generates the {@code {ClassName}Defaults} utility class for a collected class.
     *
     * <p>This is the main code generation method, creating a complete utility class
     * that provides default parameter handling for the source class.
     *
     * <p><b>Generated Class Structure:</b>
     * <pre>
     * {@code Generated("io.github.reugn.default4j.processor.DefaultValueProcessor")
     * public final class GreeterDefaults {
     *     private GreeterDefaults() { throw new UnsupportedOperationException("Utility class"); }
     *
     *     // Method helpers (overloads or builder entry points)
     *     public static void greet(Greeter instance, String name) { ... }
     *
     *     // Constructor helpers (factory methods or builder entry points)
     *     public static Greeter create() { ... }
     *
     *     // Inner builder classes (for named mode)
     *     public static final class GreeterBuilder { ... }
     * }}
     * </pre>
     *
     * <p><b>Generation Pipeline:</b>
     * <ol>
     *   <li>Validate builder name conflicts</li>
     *   <li>Generate private constructor (utility class pattern)</li>
     *   <li>Generate method helpers via {@link #generateMethodHelpers}</li>
     *   <li>Generate constructor helpers via {@link #generateConstructorHelpers}</li>
     *   <li>Build and write the class to the filer</li>
     * </ol>
     *
     * @param info the collected class information containing methods and constructors
     * @throws IOException if writing the generated source file fails
     */
    private void generateDefaultsClass(ClassInfo info) throws IOException {
        TypeElement originalClass = info.classElement;
        String packageName = getPackageName(originalClass);
        String originalClassName = originalClass.getSimpleName().toString();
        String generatedClassName = originalClassName + "Defaults";

        ClassName originalType = ClassName.get(packageName, originalClassName);
        ClassName generatedType = ClassName.get(packageName, generatedClassName);

        List<MethodSpec> methods = new ArrayList<>();
        List<TypeSpec> innerClasses = new ArrayList<>();

        // Check for builder name conflicts before generating
        if (!validateBuilderNameConflicts(info)) {
            return; // Error already reported
        }

        // Private constructor
        methods.add(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addStatement("throw new $T($S)", UnsupportedOperationException.class, "Utility class")
                .build());

        // Generate method helpers
        generateMethodHelpers(info, originalType, generatedType, methods, innerClasses);

        // Generate constructor helpers
        generateConstructorHelpers(info, originalType, generatedType, methods, innerClasses);

        // Build the class
        TypeSpec generatedClass = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethods(methods)
                .addTypes(innerClasses)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("javax.annotation.processing", "Generated"))
                        .addMember("value", "$S", DefaultValueProcessor.class.getCanonicalName())
                        .build())
                .addJavadoc("Default parameter helpers for {@link $T}.\n", originalType)
                .addJavadoc("<p>Generated by default4j annotation processor.\n")
                .build();

        JavaFile.builder(packageName, generatedClass)
                .addFileComment("Generated by default4j annotation processor. Do not modify.")
                .build()
                .writeTo(processingEnv.getFiler());
    }

    /**
     * Validates that there are no builder name conflicts between constructors and methods.
     *
     * <p>Builder classes are named based on their source element:
     * <ul>
     *   <li><b>Constructors</b>: {@code {ClassName}Builder} (e.g., {@code PersonBuilder})</li>
     *   <li><b>Methods</b>: {@code {MethodName}Builder} (e.g., {@code ProcessBuilder})</li>
     * </ul>
     *
     * <p><b>Conflict Scenario:</b>
     * <p>A conflict occurs when a method name, when capitalized, matches the class name:
     * <pre>{@code
     * public class Person {
     *     @WithDefaults(named = true)
     *     public Person(...) { }  // Would generate PersonBuilder
     *
     *     @WithDefaults(named = true)
     *     public void person(...) { }  // Would also generate PersonBuilder ← CONFLICT
     * }
     * }</pre>
     *
     * <p><b>Resolution:</b>
     * <p>Users can resolve conflicts by:
     * <ul>
     *   <li>Renaming the method</li>
     *   <li>Using {@code named=false} for one of them</li>
     * </ul>
     *
     * @param info the collected class information
     * @return {@code true} if no conflicts exist;
     * {@code false} if a conflict was detected (error already reported)
     */
    private boolean validateBuilderNameConflicts(ClassInfo info) {
        String className = info.classElement.getSimpleName().toString();
        String constructorBuilderName = className + CodeGenUtils.BUILDER_SUFFIX;

        // Check if any constructor uses named mode
        boolean hasNamedConstructor = info.constructors.stream()
                .anyMatch(ConstructorInfo::named);

        if (!hasNamedConstructor) {
            return true; // No conflict possible without named constructor
        }

        // Check if any named method would generate the same builder name
        for (MethodInfo methodInfo : info.methods) {
            if (methodInfo.named()) {
                String methodName = methodInfo.method().getSimpleName().toString();
                String methodBuilderName = capitalize(methodName) + CodeGenUtils.BUILDER_SUFFIX;

                if (constructorBuilderName.equals(methodBuilderName)) {
                    errorReporter.error(methodInfo.method(),
                            "Builder name conflict: method '" + methodName + "' would generate '" +
                                    methodBuilderName + "' which conflicts with the constructor builder. " +
                                    "Consider renaming the method or using named=false for one of them.");
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Generates helper code for methods annotated with {@code @WithDefaults}.
     *
     * <p>Iterates through all collected methods and generates appropriate helper code
     * based on the method's {@code named} setting.
     *
     * <p><b>Non-named Mode (default):</b>
     * <p>Generates static overloaded methods that forward to the instance method:
     * <pre>{@code
     * // Source: void greet(String name, @DefaultValue("Hello") String greeting)
     * public static void greet(Greeter instance, String name) {
     *     instance.greet(name, "Hello");
     * }
     * public static void greet(Greeter instance, String name, String greeting) {
     *     instance.greet(name, greeting);
     * }
     * }</pre>
     *
     * <p><b>Named Mode:</b>
     * <p>Generates a builder class and factory method:
     * <pre>{@code
     * public static GreetBuilder greet(Greeter instance) {
     *     return new GreetBuilder(instance);
     * }
     *
     * public static final class GreetBuilder {
     *     private String name;
     *     private String greeting = "Hello";
     *     // ... builder methods and build()
     * }
     * }</pre>
     *
     * @param info          the collected class information
     * @param originalType  the {@link ClassName} of the source class
     * @param generatedType the {@link ClassName} of the generated Defaults class
     * @param methods       accumulator list for generated method specs
     * @param innerClasses  accumulator list for generated builder type specs
     */
    private void generateMethodHelpers(ClassInfo info, ClassName originalType, ClassName generatedType,
                                       List<MethodSpec> methods, List<TypeSpec> innerClasses) {
        for (MethodInfo methodInfo : info.methods) {
            ExecutableElement method = methodInfo.method();

            if (methodInfo.named()) {
                String methodName = method.getSimpleName().toString();
                String builderClassName = capitalize(methodName) + CodeGenUtils.BUILDER_SUFFIX;
                ClassName builderType = generatedType.nestedClass(builderClassName);

                innerClasses.add(MethodGenerator.generateBuilderClass(method, originalType, builderClassName, builderType));

                methods.add(MethodSpec.methodBuilder(methodName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(builderType)
                        .addParameter(originalType, CodeGenUtils.INSTANCE_PARAM)
                        .addStatement("return new $T(instance)", builderType)
                        .addJavadoc("Creates a builder for {@link $T#$N} with named parameters.\n", originalType, methodName)
                        .build());
            } else {
                methods.addAll(MethodGenerator.generateStaticOverloadedMethods(method, originalType, errorReporter));
            }
        }
    }

    /**
     * Generates helper code for constructors annotated with {@code @WithDefaults}.
     *
     * <p>Iterates through all collected constructors and generates factory methods
     * or builders based on the constructor's settings.
     *
     * <p><b>Non-named Mode (default):</b>
     * <p>Generates overloaded static factory methods:
     * <pre>{@code
     * // Source: Person(String name, @DefaultValue("30") int age)
     * public static Person create(String name) {
     *     return new Person(name, 30);
     * }
     * public static Person create(String name, int age) {
     *     return new Person(name, age);
     * }
     * }</pre>
     *
     * <p><b>Named Mode:</b>
     * <p>Generates a builder class and factory entry point:
     * <pre>{@code
     * public static PersonBuilder create() {
     *     return new PersonBuilder();
     * }
     *
     * public static final class PersonBuilder {
     *     private String name;
     *     private int age = 30;
     *     // ... builder methods and build()
     * }
     * }</pre>
     *
     * <p><b>Record Handling:</b>
     * <p>For records, defaults are read from record component annotations rather than
     * constructor parameter annotations. Uses specialized methods in {@link ConstructorGenerator}:
     * <ul>
     *   <li>{@link ConstructorGenerator#generateRecordFactoryMethods}</li>
     *   <li>{@link ConstructorGenerator#generateRecordBuilderClass}</li>
     * </ul>
     *
     * @param info          the collected class information
     * @param originalType  the {@link ClassName} of the source class
     * @param generatedType the {@link ClassName} of the generated Defaults class
     * @param methods       accumulator list for generated factory method specs
     * @param innerClasses  accumulator list for generated builder type specs
     */
    private void generateConstructorHelpers(ClassInfo info, ClassName originalType, ClassName generatedType,
                                            List<MethodSpec> methods, List<TypeSpec> innerClasses) {
        for (ConstructorInfo ctorInfo : info.constructors) {
            ExecutableElement constructor = ctorInfo.constructor();
            String factoryMethodName = ctorInfo.methodName();

            if (ctorInfo.named()) {
                // Builder named after the class being built, not the factory method
                String builderClassName = info.classElement.getSimpleName().toString() + CodeGenUtils.BUILDER_SUFFIX;
                ClassName builderType = generatedType.nestedClass(builderClassName);

                if (info.isRecord && info.recordComponents != null) {
                    innerClasses.add(ConstructorGenerator.generateRecordBuilderClass(
                            constructor, info.recordComponents, originalType, builderClassName, builderType));
                } else {
                    innerClasses.add(ConstructorGenerator.generateBuilderClass(
                            constructor, originalType, builderClassName, builderType));
                }

                methods.add(MethodSpec.methodBuilder(factoryMethodName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(builderType)
                        .addStatement("return new $T()", builderType)
                        .addJavadoc("Creates a builder for {@link $T} with named parameters.\n", originalType)
                        .build());
            } else {
                if (info.isRecord && info.recordComponents != null) {
                    methods.addAll(ConstructorGenerator.generateRecordFactoryMethods(
                            constructor, info.recordComponents, originalType, factoryMethodName, errorReporter));
                } else {
                    methods.addAll(ConstructorGenerator.generateFactoryMethods(
                            constructor, originalType, factoryMethodName, errorReporter));
                }
            }
        }
    }

    /**
     * Gets the package name for a type element.
     *
     * <p>The generated Defaults class is placed in the same package as the source class,
     * ensuring it has access to package-private members.
     *
     * @param type the type element to get the package for
     * @return the fully-qualified package name (e.g., "com.example.service")
     */
    private String getPackageName(TypeElement type) {
        return processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Immutable record holding constructor information and its generation settings.
     *
     * <p>Created during collection phase and consumed during generation phase.
     *
     * @param constructor the constructor element from the source class
     * @param methodName  the factory method name to generate (from {@code @WithDefaults.methodName()},
     *                    defaults to "create")
     * @param named       {@code true} to generate a builder, {@code false} for overloaded factories
     */
    private record ConstructorInfo(ExecutableElement constructor, String methodName, boolean named) {
    }

    /**
     * Immutable record holding method information and its generation settings.
     *
     * <p>Created during collection phase and consumed during generation phase.
     *
     * @param method the method element from the source class
     * @param named  {@code true} to generate a builder, {@code false} for overloaded methods
     */
    private record MethodInfo(ExecutableElement method, boolean named) {
    }

    /**
     * Mutable accumulator for information about a class being processed.
     *
     * <p>Collects methods and constructors from various annotation sources during
     * the collection phase:
     * <ul>
     *   <li>Directly annotated methods/constructors</li>
     *   <li>Methods/constructors with default parameters under class-level {@code @WithDefaults}</li>
     * </ul>
     *
     * <p>A single {@code ClassInfo} results in a single generated {@code {ClassName}Defaults} class.
     */
    private static class ClassInfo {
        /**
         * The source class being processed.
         */
        final TypeElement classElement;

        /**
         * Methods to generate helpers for (with their individual settings).
         */
        final List<MethodInfo> methods = new ArrayList<>();

        /**
         * Constructors to generate factories/builders for (with their individual settings).
         */
        final List<ConstructorInfo> constructors = new ArrayList<>();

        /**
         * {@code true} if this is a Java record type.
         */
        boolean isRecord = false;

        /**
         * Record components in declaration order; {@code null} for non-records.
         */
        List<RecordComponentElement> recordComponents;

        /**
         * Creates a new ClassInfo for the given source class.
         *
         * @param classElement the source class being processed
         */
        ClassInfo(TypeElement classElement) {
            this.classElement = classElement;
        }

        /**
         * Adds a method that has its own {@code @WithDefaults} annotation.
         *
         * <p>The method's own annotation settings are used for generation.
         *
         * @param method the directly-annotated method
         */
        void addMethod(ExecutableElement method) {
            WithDefaults annotation = method.getAnnotation(WithDefaults.class);
            methods.add(new MethodInfo(method, annotation.named()));
        }

        /**
         * Adds a method discovered via class-level {@code @WithDefaults}.
         *
         * <p>The class-level annotation settings are used for generation since
         * the method itself doesn't have {@code @WithDefaults}.
         *
         * @param method          the method with default parameters
         * @param classAnnotation the class-level {@code @WithDefaults} annotation
         */
        void addMethodFromClass(ExecutableElement method, WithDefaults classAnnotation) {
            methods.add(new MethodInfo(method, classAnnotation.named()));
        }

        /**
         * Adds a constructor that has its own {@code @WithDefaults} annotation.
         *
         * <p>The constructor's own annotation settings are used for generation.
         *
         * @param constructor the directly-annotated constructor
         */
        void addConstructor(ExecutableElement constructor) {
            WithDefaults annotation = constructor.getAnnotation(WithDefaults.class);
            constructors.add(new ConstructorInfo(constructor, annotation.methodName(), annotation.named()));
        }

        /**
         * Adds a constructor discovered via class-level {@code @WithDefaults}.
         *
         * <p>The class-level annotation settings are used for generation since
         * the constructor itself doesn't have {@code @WithDefaults}.
         *
         * @param constructor     the constructor with default parameters
         * @param classAnnotation the class-level {@code @WithDefaults} annotation
         */
        void addConstructorFromClass(ExecutableElement constructor, WithDefaults classAnnotation) {
            constructors.add(new ConstructorInfo(constructor, classAnnotation.methodName(), classAnnotation.named()));
        }
    }
}
