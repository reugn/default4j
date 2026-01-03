package io.github.reugn.default4j.processor;

import io.github.reugn.default4j.annotation.DefaultFactory;
import io.github.reugn.default4j.annotation.DefaultValue;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Compile-time validation utilities for default4j annotations.
 *
 * <p>Provides early detection of configuration errors during annotation processing,
 * giving developers immediate feedback with actionable error messages rather than
 * cryptic runtime errors or generated code that fails to compile.
 *
 * <p><b>Architecture:</b>
 * <p>This class is the main entry point for validations, orchestrating calls to specialized validators:
 * <ul>
 *   <li>{@link LiteralValidator} — validates that literal values are parseable for their target types</li>
 *   <li>{@link ReferenceValidator} — validates field and factory method references with typo suggestions</li>
 * </ul>
 *
 * <p><b>Validation Categories:</b>
 *
 * <p><b>Annotation Validation:</b>
 * <ul>
 *   <li>Mutual exclusivity: {@code @DefaultValue(value=..., field=...)} cannot specify both</li>
 *   <li>Mutual exclusivity: same parameter cannot have both {@code @DefaultValue} and {@code @DefaultFactory}</li>
 *   <li>Empty value: {@code @DefaultValue("")} is only valid for {@code String} type</li>
 * </ul>
 *
 * <p><b>Literal Validation:</b>
 * <ul>
 *   <li>Numeric literals must be parseable (e.g., {@code "abc"} is not a valid {@code int})</li>
 *   <li>Boolean literals must be {@code "true"} or {@code "false"}</li>
 *   <li>Character literals must be exactly one character</li>
 *   <li>{@code "null"} is not allowed for primitive types</li>
 * </ul>
 *
 * <p><b>Reference Validation:</b>
 * <ul>
 *   <li>Field references must point to existing static fields with compatible types</li>
 *   <li>Factory methods must exist, be static, have no parameters, and return a compatible type</li>
 *   <li>Typo suggestions are provided when references cannot be resolved</li>
 * </ul>
 *
 * <p><b>Structural Validation:</b>
 * <ul>
 *   <li>Consecutive defaults: in non-named mode, all parameters after the first default must have defaults</li>
 *   <li>Visibility: private methods/constructors cannot have generated helpers</li>
 *   <li>Instantiability: {@code @IncludeDefaults} types must be concrete (not interfaces or abstract)</li>
 *   <li>Signature conflicts: generated overloads must not conflict with existing methods</li>
 * </ul>
 *
 * <p><b>Example Error Messages:</b>
 * <pre>
 * error: @DefaultValue cannot specify both 'value' and 'field'. Use one or the other.
 * error: 'abc' is not a valid int. Expected a numeric value.
 * error: Field 'DEFUALT_NAME' not found in Service. Did you mean 'DEFAULT_NAME'?
 * error: Method 'process' is private. Generated helpers cannot access private elements.
 * </pre>
 *
 * @see LiteralValidator
 * @see ReferenceValidator
 * @see io.github.reugn.default4j.annotation.DefaultValue
 * @see io.github.reugn.default4j.annotation.DefaultFactory
 */
final class ValidationUtils {

    private ValidationUtils() {
    }

    // ==================== PARAMETER VALIDATION ====================

    /**
     * Validates {@code @DefaultValue} and {@code @DefaultFactory} annotations on method/constructor parameters.
     *
     * <p>Iterates through all parameters of the given executable, validating each annotated parameter.
     * Continues validating all parameters even after finding errors to report all issues at once.
     *
     * <p><b>Pre-checks:</b>
     * <ul>
     *   <li>No parameter may have both {@code @DefaultValue} and {@code @DefaultFactory}</li>
     * </ul>
     *
     * <p><b>{@code @DefaultValue} Validations:</b>
     * <ul>
     *   <li>Cannot specify both {@code value} and {@code field} attributes</li>
     *   <li>Empty value ({@code @DefaultValue("")}) is only valid for {@code String} type</li>
     *   <li>Literal values must be parseable for the parameter's type</li>
     *   <li>Field references must point to existing static fields with compatible types</li>
     * </ul>
     *
     * <p><b>{@code @DefaultFactory} Validations:</b>
     * <ul>
     *   <li>Method reference must be non-empty</li>
     *   <li>Referenced method must exist, be static, and have no parameters</li>
     *   <li>Method must not return {@code void}</li>
     *   <li>Return type must be assignable to the parameter type</li>
     * </ul>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * @WithDefaults
     * public void process(
     *     @DefaultValue("100") int timeout,           // Validated: parseable int literal
     *     @DefaultValue(field = "DEFAULT_HOST") String host,  // Validated: field exists and is static
     *     @DefaultFactory("generateId") String id    // Validated: method exists and returns String
     * ) { }
     * }</pre>
     *
     * @param executable     the method or constructor whose parameters are being validated
     * @param enclosingClass the class containing the executable (used for resolving references)
     * @param errorReporter  callback for reporting compilation errors
     * @param typeUtils      type utilities for assignability checks
     * @param elementUtils   element utilities for resolving external class references
     * @return {@code true} if all parameters pass validation;
     * {@code false} if any errors were reported (processing continues to find all errors)
     */
    static boolean validateParameterAnnotations(ExecutableElement executable,
                                                TypeElement enclosingClass,
                                                ErrorReporter errorReporter,
                                                Types typeUtils,
                                                Elements elementUtils) {
        boolean valid = true;

        for (VariableElement param : executable.getParameters()) {
            DefaultValue defaultValue = param.getAnnotation(DefaultValue.class);
            DefaultFactory defaultFactory = param.getAnnotation(DefaultFactory.class);

            // Check for duplicate annotations
            if (defaultValue != null && defaultFactory != null) {
                errorReporter.error(param,
                        "Parameter cannot have both @DefaultValue and @DefaultFactory. Use one or the other.");
                valid = false;
                continue;
            }

            // Validate @DefaultValue
            if (defaultValue != null) {
                if (!validateDefaultValue(defaultValue, param, enclosingClass, errorReporter, typeUtils, elementUtils)) {
                    valid = false;
                }
            }

            // Validate @DefaultFactory
            if (defaultFactory != null) {
                if (!ReferenceValidator.validateFactoryMethod(defaultFactory.value(), param.asType(),
                        enclosingClass, errorReporter, param, typeUtils, elementUtils)) {
                    valid = false;
                }
            }
        }

        return valid;
    }

    /**
     * Validates a single {@code @DefaultValue} annotation on a parameter.
     *
     * <p>Performs all validations specific to {@code @DefaultValue}:
     * <ol>
     *   <li>Mutual exclusivity of {@code value} and {@code field} attributes</li>
     *   <li>Empty value validation (only allowed for {@code String} type)</li>
     *   <li>Literal parseability (delegated to {@link LiteralValidator})</li>
     *   <li>Field reference validation (delegated to {@link ReferenceValidator})</li>
     * </ol>
     *
     * @param defaultValue   the annotation instance to validate
     * @param param          the parameter element bearing the annotation
     * @param enclosingClass the class containing the parameter's method/constructor
     * @param errorReporter  callback for reporting errors
     * @param typeUtils      type utilities for assignability checks
     * @param elementUtils   element utilities for resolving external references
     * @return {@code true} if valid, {@code false} if an error was reported
     */
    private static boolean validateDefaultValue(DefaultValue defaultValue, VariableElement param,
                                                TypeElement enclosingClass, ErrorReporter errorReporter,
                                                Types typeUtils, Elements elementUtils) {
        boolean hasField = !defaultValue.field().isEmpty();
        boolean hasValue = !defaultValue.value().isEmpty();

        // Mutual exclusivity
        if (hasValue && hasField) {
            errorReporter.error(param,
                    "@DefaultValue cannot specify both 'value' and 'field'. Use one or the other.");
            return false;
        }

        // Empty value is only valid for String
        if (!hasValue && !hasField) {
            String typeStr = param.asType().toString();
            if (!typeStr.equals("java.lang.String")) {
                errorReporter.error(param,
                        "@DefaultValue with empty value is only valid for String type, not " +
                                LiteralValidator.getSimpleTypeName(typeStr) + ".");
                return false;
            }
        }

        // Validate literal parseability
        if (!hasField && hasValue) {
            if (!LiteralValidator.validateParseable(defaultValue.value(), param.asType(), param, errorReporter)) {
                return false;
            }
        }

        // Validate field reference
        if (hasField) {
            if (!ReferenceValidator.validateFieldReference(defaultValue.field(), enclosingClass, param,
                    param.asType(), errorReporter, typeUtils, elementUtils)) {
                return false;
            }
        }

        return true;
    }

    // ==================== RECORD COMPONENT VALIDATION ====================

    /**
     * Validates {@code @DefaultValue} and {@code @DefaultFactory} annotations on record components.
     *
     * <p>Records in Java have their annotations placed on component declarations rather than
     * constructor parameters. This method provides equivalent validation for record components
     * as {@link #validateParameterAnnotations} does for regular method/constructor parameters.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * @WithDefaults
     * public record Config(
     *     String host,
     *     @DefaultValue("8080") int port,           // Validated
     *     @DefaultFactory("generateSecret") String secret  // Validated
     * ) { }
     * }</pre>
     *
     * <p>Filters the components list to only process elements of kind {@code RECORD_COMPONENT},
     * ignoring fields, methods, and other enclosed elements.
     *
     * @param recordElement the record type element being validated
     * @param components    all enclosed elements of the record (will be filtered to RECORD_COMPONENT)
     * @param errorReporter callback for reporting compilation errors
     * @param typeUtils     type utilities for assignability checks
     * @param elementUtils  element utilities for resolving external class references
     * @return {@code true} if all components pass validation;
     * {@code false} if any errors were reported
     * @see #validateParameterAnnotations
     */
    static boolean validateRecordComponents(TypeElement recordElement,
                                            List<? extends Element> components,
                                            ErrorReporter errorReporter,
                                            Types typeUtils,
                                            Elements elementUtils) {
        boolean valid = true;

        for (Element component : components) {
            if (component.getKind() == ElementKind.RECORD_COMPONENT) {
                DefaultValue defaultValue = component.getAnnotation(DefaultValue.class);
                DefaultFactory defaultFactory = component.getAnnotation(DefaultFactory.class);

                // Check for duplicate annotations
                if (defaultValue != null && defaultFactory != null) {
                    errorReporter.error(component,
                            "Record component cannot have both @DefaultValue and @DefaultFactory. Use one or the other.");
                    valid = false;
                    continue;
                }

                if (defaultValue != null) {
                    if (!validateRecordDefaultValue(defaultValue, component, recordElement, errorReporter, typeUtils, elementUtils)) {
                        valid = false;
                    }
                }

                if (defaultFactory != null) {
                    if (!ReferenceValidator.validateFactoryMethod(defaultFactory.value(), component.asType(),
                            recordElement, errorReporter, component, typeUtils, elementUtils)) {
                        valid = false;
                    }
                }
            }
        }

        return valid;
    }

    /**
     * Validates a single {@code @DefaultValue} annotation on a record component.
     *
     * <p>Equivalent to {@link #validateDefaultValue} but for record components,
     * which use {@link Element} rather than {@link VariableElement}.
     *
     * @param defaultValue  the annotation instance to validate
     * @param component     the record component element bearing the annotation
     * @param recordElement the record type containing the component
     * @param errorReporter callback for reporting errors
     * @param typeUtils     type utilities for assignability checks
     * @param elementUtils  element utilities for resolving external references
     * @return {@code true} if valid, {@code false} if an error was reported
     */
    private static boolean validateRecordDefaultValue(DefaultValue defaultValue, Element component,
                                                      TypeElement recordElement, ErrorReporter errorReporter,
                                                      Types typeUtils, Elements elementUtils) {
        boolean hasField = !defaultValue.field().isEmpty();
        boolean hasValue = !defaultValue.value().isEmpty();

        if (hasValue && hasField) {
            errorReporter.error(component,
                    "@DefaultValue cannot specify both 'value' and 'field'. Use one or the other.");
            return false;
        }

        if (!hasValue && !hasField) {
            String typeStr = component.asType().toString();
            if (!typeStr.equals("java.lang.String")) {
                errorReporter.error(component,
                        "@DefaultValue with empty value is only valid for String type, not " +
                                LiteralValidator.getSimpleTypeName(typeStr) + ".");
                return false;
            }
        }

        if (!hasField && hasValue) {
            if (!LiteralValidator.validateParseable(defaultValue.value(), component.asType(), component, errorReporter)) {
                return false;
            }
        }

        if (hasField) {
            if (!ReferenceValidator.validateFieldReference(defaultValue.field(), recordElement, component,
                    component.asType(), errorReporter, typeUtils, elementUtils)) {
                return false;
            }
        }

        return true;
    }

    // ==================== INCLUDED TYPE VALIDATION ====================

    /**
     * Validates that an included type can be instantiated.
     *
     * <p>{@code @IncludeDefaults} generates factory methods that create instances of the
     * included type using its constructor. This requires the type to be a concrete class
     * or record that can be directly instantiated.
     *
     * <p><b>Rejected Types:</b>
     * <ul>
     *   <li><b>Interfaces</b> — cannot be instantiated directly</li>
     *   <li><b>Abstract classes</b> — cannot be instantiated directly</li>
     * </ul>
     *
     * <p><b>Example Error:</b>
     * <pre>{@code
     * // This will produce an error:
     * @IncludeDefaults(List.class)
     * public class MyHolder { }}
     * </pre>
     * <pre>
     * error: Cannot include interface 'List' in @IncludeDefaults.
     *        Only concrete classes and records are supported.
     * </pre>
     *
     * @param includedType    the type specified in {@code @IncludeDefaults}
     * @param definingElement the element bearing the {@code @IncludeDefaults} annotation
     *                        (used for error reporting location)
     * @param errorReporter   callback for reporting compilation errors
     * @return {@code true} if the type is instantiable;
     * {@code false} if it's an interface or abstract class
     */
    static boolean validateIncludedType(TypeElement includedType, Element definingElement,
                                        ErrorReporter errorReporter) {
        if (includedType.getKind() == ElementKind.INTERFACE) {
            errorReporter.error(definingElement,
                    "Cannot include interface '" + includedType.getSimpleName() +
                            "' in @IncludeDefaults. Only concrete classes and records are supported.");
            return false;
        }

        if (includedType.getModifiers().contains(Modifier.ABSTRACT)) {
            errorReporter.error(definingElement,
                    "Cannot include abstract class '" + includedType.getSimpleName() +
                            "' in @IncludeDefaults. Only concrete classes and records are supported.");
            return false;
        }

        return true;
    }

    // ==================== CONSECUTIVE DEFAULTS VALIDATION ====================

    /**
     * Validates that default parameters are consecutive (no required params after optional ones).
     *
     * <p>In non-named mode (builder pattern disabled), default4j generates overloaded methods
     * by progressively omitting trailing parameters. This approach only works when all
     * parameters after the first default also have defaults — otherwise, there's no way
     * to call the method without the middle parameter.
     *
     * <p><b>Valid Pattern:</b>
     * <pre>{@code
     * void process(String name, @DefaultValue("100") int timeout, @DefaultValue("8080") int port)
     * // Generates: process(name), process(name, timeout), process(name, timeout, port)
     * }</pre>
     *
     * <p><b>Invalid Pattern:</b>
     * <pre>{@code
     * void process(String name, @DefaultValue("100") int timeout, int port)
     * // ERROR: 'port' has no default but appears after 'timeout' which has a default
     * }</pre>
     *
     * <p>For non-consecutive defaults, users should use {@code named=true} mode, which
     * generates a builder allowing any combination of parameters.
     *
     * @param executable        the method or constructor being validated
     * @param paramInfos        list of parameter information including default status
     * @param firstDefaultIndex index of the first parameter with a default value (0-based)
     * @param errorReporter     callback for reporting compilation errors
     * @return {@code true} if all parameters after firstDefaultIndex have defaults;
     * {@code false} if a gap is found
     */
    static boolean validateConsecutiveDefaults(ExecutableElement executable,
                                               List<CodeGenUtils.ParameterInfo> paramInfos,
                                               int firstDefaultIndex,
                                               ErrorReporter errorReporter) {
        for (int i = firstDefaultIndex; i < paramInfos.size(); i++) {
            if (!paramInfos.get(i).hasDefault()) {
                errorReporter.error(executable.getParameters().get(i),
                        "Parameter without @DefaultValue/@DefaultFactory found after parameter with default. " +
                                "All parameters after the first default must also have defaults.");
                return false;
            }
        }
        return true;
    }

    /**
     * Validates consecutive defaults for external classes included via {@code @IncludeDefaults}.
     *
     * <p>Similar to {@link #validateConsecutiveDefaults} but works with external types where
     * we only have component names and a predicate to check for defaults, rather than direct
     * access to annotations.
     *
     * <p>If no components have defaults, validation passes (nothing to generate).
     * If defaults exist but are non-consecutive, reports an error listing the specific
     * parameters that are missing defaults.
     *
     * <p><b>Example Error:</b>
     * <pre>
     * error: Non-consecutive defaults for Config: parameters [port] have no default
     *        but appear after parameters with defaults. Either add defaults for these
     *        parameters, or use named=true mode.
     * </pre>
     *
     * @param componentNames   ordered list of constructor parameter or record component names
     * @param hasDefault       predicate returning {@code true} if the component at index i has a default
     * @param definingClass    the class bearing the {@code @IncludeDefaults} annotation
     *                         (used for error reporting location)
     * @param includedTypeName the simple name of the included class (for error messages)
     * @param errorReporter    callback for reporting compilation errors
     * @return {@code true} if defaults are consecutive or no defaults exist;
     * {@code false} if non-consecutive defaults are detected
     */
    static boolean validateConsecutiveDefaultsForInclude(List<String> componentNames,
                                                         IntPredicate hasDefault,
                                                         TypeElement definingClass,
                                                         String includedTypeName,
                                                         ErrorReporter errorReporter) {
        int firstDefaultIndex = -1;
        for (int i = 0; i < componentNames.size(); i++) {
            if (hasDefault.test(i)) {
                firstDefaultIndex = i;
                break;
            }
        }

        if (firstDefaultIndex == -1) {
            return true;
        }

        List<String> missingDefaults = new ArrayList<>();
        for (int i = firstDefaultIndex; i < componentNames.size(); i++) {
            if (!hasDefault.test(i)) {
                missingDefaults.add(componentNames.get(i));
            }
        }

        if (!missingDefaults.isEmpty()) {
            errorReporter.error(definingClass,
                    "Non-consecutive defaults for " + includedTypeName + ": " +
                            "parameters " + missingDefaults + " have no default but appear after parameters with defaults. " +
                            "Either add defaults for these parameters, or use named=true mode.");
            return false;
        }

        return true;
    }

    // ==================== VISIBILITY VALIDATION ====================

    /**
     * Validates that a method or constructor is not private.
     *
     * <p>Generated helper classes (e.g., {@code ServiceDefaults}) are separate compilation
     * units that cannot access private members of other classes. Attempting to generate
     * helpers for private methods/constructors would result in "cannot access" compilation
     * errors in the generated code.
     *
     * <p><b>Supported Visibility:</b>
     * <ul>
     *   <li>{@code public} — accessible from generated helper ✓</li>
     *   <li>{@code protected} — accessible from same-package helper ✓</li>
     *   <li>package-private (no modifier) — accessible from same-package helper ✓</li>
     *   <li>{@code private} — NOT accessible from generated helper ✗</li>
     * </ul>
     *
     * <p><b>Example Error:</b>
     * <pre>
     * error: Method 'process' is private. Generated helpers cannot access private
     *        elements. Use package-private, protected, or public visibility.
     * </pre>
     *
     * @param executable    the method or constructor being validated
     * @param errorReporter callback for reporting compilation errors
     * @return {@code true} if the element is not private;
     * {@code false} if it is private (error reported)
     */
    static boolean validateElementVisibility(ExecutableElement executable, ErrorReporter errorReporter) {
        if (executable.getModifiers().contains(Modifier.PRIVATE)) {
            String elementType = executable.getKind() == ElementKind.CONSTRUCTOR ? "Constructor" : "Method";
            errorReporter.error(executable,
                    elementType + " '" + executable.getSimpleName() + "' is private. " +
                            "Generated helpers cannot access private elements. " +
                            "Use package-private, protected, or public visibility.");
            return false;
        }
        return true;
    }
}
