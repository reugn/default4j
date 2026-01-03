package io.github.reugn.default4j.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates field and factory method references used in {@code @DefaultValue(field=...)}
 * and {@code @DefaultFactory} annotations.
 *
 * <p><b>Supported Reference Formats:</b>
 * <ul>
 *   <li><b>Same-class references:</b> Simple names like {@code "MY_CONSTANT"} or {@code "createDefault"}</li>
 *   <li><b>External class references:</b> Qualified names like {@code "Constants.VALUE"} or
 *       {@code "com.example.Defaults.create"}</li>
 * </ul>
 *
 * <p><b>Field Reference Validation ({@code @DefaultValue(field=...)}):</b>
 * <ul>
 *   <li>Reference must be non-empty</li>
 *   <li>Field must exist in the target class</li>
 *   <li>Field must have the {@code static} modifier</li>
 *   <li>Field type must be assignable to the parameter type</li>
 * </ul>
 *
 * <p><b>Factory Method Validation ({@code @DefaultFactory}):</b>
 * <ul>
 *   <li>Reference must be non-empty</li>
 *   <li>Method must exist in the target class</li>
 *   <li>Method must have the {@code static} modifier</li>
 *   <li>Method must have no parameters</li>
 *   <li>Method must not return {@code void}</li>
 *   <li>Return type must be assignable to the parameter type</li>
 * </ul>
 *
 * <p><b>Typo Detection:</b>
 * <p>When a reference cannot be resolved, this validator uses Levenshtein distance to find
 * similar names and provide helpful suggestions in error messages. For example:
 * <pre>
 * Field 'DEFUALT_NAME' not found in Service. Did you mean 'DEFAULT_NAME'?
 * </pre>
 *
 * <p><b>External Class Resolution:</b>
 * External classes are resolved by attempting:
 * <ol>
 *   <li>Fully qualified name lookup (e.g., {@code com.example.Constants})</li>
 *   <li>Same-package lookup (e.g., {@code Constants} in the same package)</li>
 * </ol>
 * If the class cannot be resolved, a helpful error is reported suggesting the use of
 * fully qualified class names (required for classes outside the current package).
 *
 * <p><b>Architecture:</b>
 * This class uses shared infrastructure for both field and method validation:
 * <ul>
 *   <li>{@link ParsedReference} - holds parsed class/member components</li>
 *   <li>{@link #parseReference} - parses references with annotation-specific messages</li>
 *   <li>{@link #resolveClass} - resolves external class names</li>
 *   <li>{@link #reportNotFound} - generates helpful error messages with suggestions</li>
 * </ul>
 *
 * @see ValidationUtils
 * @see io.github.reugn.default4j.annotation.DefaultValue
 * @see io.github.reugn.default4j.annotation.DefaultFactory
 */
final class ReferenceValidator {

    private ReferenceValidator() {
    }

    // ==================== FIELD VALIDATION ====================

    /**
     * Validates a field reference for {@code @DefaultValue(field=...)}.
     *
     * <p>Validates that the referenced field exists, is static, and has a type compatible
     * with the annotated parameter. Supports both same-class references (e.g., {@code "MY_CONSTANT"})
     * and external class references (e.g., {@code "Constants.VALUE"}).
     *
     * <p><b>Validation Rules:</b>
     * <ol>
     *   <li>Reference must be non-empty</li>
     *   <li>If qualified (contains a dot), the field name after the last dot must be non-empty</li>
     *   <li>For external references, the class must be resolvable</li>
     *   <li>Field must exist in the resolved class</li>
     *   <li>Field must be {@code static}</li>
     *   <li>Field type must be assignable to the parameter type</li>
     * </ol>
     *
     * <p><b>Error Messages:</b>
     * When a field is not found, the error message includes:
     * <ul>
     *   <li>A typo suggestion if a similar field name exists (using Levenshtein distance)</li>
     *   <li>A list of available static fields if no similar name is found</li>
     *   <li>A generic hint if no static fields exist in the class</li>
     * </ul>
     *
     * <p><b>Example Usage:</b>
     * <pre>{@code
     * // Same-class reference
     * @DefaultValue(field = "DEFAULT_TIMEOUT")
     * int timeout
     *
     * // Same-package class reference
     * @DefaultValue(field = "AppDefaults.TIMEOUT")
     * int timeout
     *
     * // External class reference (requires fully qualified name)
     * @DefaultValue(field = "java.lang.Integer.MAX_VALUE")
     * int maxValue
     * }</pre>
     *
     * @param fieldRef         the field reference; simple name (e.g., {@code "MY_CONSTANT"})
     *                         or qualified name (e.g., {@code "OtherClass.CONSTANT"})
     * @param enclosingClass   the class containing the annotated parameter
     * @param annotatedElement the parameter element to report errors against
     * @param expectedType     the parameter type that the field must be assignable to
     * @param errorReporter    callback for reporting compilation errors
     * @param typeUtils        type utilities for assignability checks
     * @param elementUtils     element utilities for resolving external classes;
     *                         if {@code null}, external class validation is skipped
     * @return {@code true} if the reference is valid;
     * {@code false} if a validation error was reported
     */
    static boolean validateFieldReference(String fieldRef, TypeElement enclosingClass,
                                          Element annotatedElement, TypeMirror expectedType,
                                          ErrorReporter errorReporter, Types typeUtils,
                                          Elements elementUtils) {
        ParsedReference parsed = parseReference(fieldRef, "@DefaultValue(field=...)",
                "field", annotatedElement, errorReporter);
        if (parsed == null) {
            return false;
        }

        TypeElement targetClass;
        if (parsed.isExternal()) {
            if (elementUtils == null) {
                return true; // Skip validation if no element utils
            }
            targetClass = resolveClass(parsed.className(), enclosingClass, elementUtils);
            if (targetClass == null) {
                errorReporter.error(annotatedElement,
                        "Class '" + parsed.className() + "' not found for @DefaultValue(field=\"" + fieldRef + "\"). " +
                                "Hint: Use the fully qualified class name (e.g., java.lang.Integer.MAX_VALUE).");
                return false;
            }
        } else {
            targetClass = enclosingClass;
        }

        return validateFieldInClass(targetClass, parsed.memberName(), expectedType,
                errorReporter, annotatedElement, typeUtils);
    }

    /**
     * Validates that a field with the given name exists in the target class.
     *
     * <p>Iterates through all fields in the class, checking for a match. If found,
     * validates the field is static and has a compatible type. If not found, reports
     * an error with a typo suggestion or list of available static fields.
     *
     * <p><b>Validation Order:</b>
     * <ol>
     *   <li>Find field by name</li>
     *   <li>Verify {@code static} modifier</li>
     *   <li>Verify type compatibility using {@link Types#isAssignable}</li>
     * </ol>
     *
     * @param targetClass      the class to search for the field
     * @param fieldName        the simple name of the field to find
     * @param expectedType     the type the field must be assignable to
     * @param errorReporter    callback for reporting errors
     * @param annotatedElement the element to report errors against
     * @param typeUtils        type utilities for assignability checks
     * @return {@code true} if the field is valid, {@code false} if an error was reported
     */
    private static boolean validateFieldInClass(TypeElement targetClass, String fieldName,
                                                TypeMirror expectedType, ErrorReporter errorReporter,
                                                Element annotatedElement, Types typeUtils) {
        List<String> staticFields = new ArrayList<>();

        for (Element enclosed : targetClass.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                String name = field.getSimpleName().toString();

                if (field.getModifiers().contains(Modifier.STATIC)) {
                    staticFields.add(name);
                }

                if (name.equals(fieldName)) {
                    if (!field.getModifiers().contains(Modifier.STATIC)) {
                        errorReporter.error(annotatedElement,
                                "Field '" + fieldName + "' in " + targetClass.getSimpleName() + " must be static.");
                        return false;
                    }

                    TypeMirror fieldType = field.asType();
                    if (!typeUtils.isAssignable(fieldType, expectedType)) {
                        errorReporter.error(annotatedElement,
                                "Field '" + fieldName + "' in " + targetClass.getSimpleName() +
                                        " has type " + fieldType + " which is not assignable to " + expectedType + ".");
                        return false;
                    }
                    return true;
                }
            }
        }

        // Field not found - provide helpful error
        reportNotFound("Field", fieldName, targetClass, staticFields, errorReporter, annotatedElement);
        return false;
    }

    // ==================== FACTORY METHOD VALIDATION ====================

    /**
     * Validates a factory method reference for {@code @DefaultFactory}.
     *
     * <p>Validates that the referenced method exists, is static, takes no parameters,
     * returns a non-void type compatible with the annotated parameter. Supports both
     * same-class references (e.g., {@code "createDefault"}) and external class references
     * (e.g., {@code "Defaults.create"}).
     *
     * <p><b>Validation Rules:</b>
     * <ol>
     *   <li>Reference must be non-empty</li>
     *   <li>If qualified (contains a dot), the method name after the last dot must be non-empty</li>
     *   <li>For external references, the class must be resolvable</li>
     *   <li>Method must exist in the resolved class</li>
     *   <li>Method must be {@code static}</li>
     *   <li>Method must have no parameters (zero-argument)</li>
     *   <li>Method must not return {@code void}</li>
     *   <li>Return type must be assignable to the parameter type</li>
     * </ol>
     *
     * <p><b>Error Messages:</b>
     * <p>When a method is not found, the error message includes:
     * <ul>
     *   <li>A typo suggestion if a similar method name exists (using Levenshtein distance)</li>
     *   <li>A list of available static no-arg methods if no similar name is found</li>
     *   <li>A generic hint if no suitable methods exist in the class</li>
     * </ul>
     *
     * <p><b>Example Usage:</b>
     * <pre>{@code
     * // Same-class reference
     * @DefaultFactory("generateId")
     * String id
     *
     * // Same-package class reference
     * @DefaultFactory("IdGenerator.next")
     * String id
     *
     * // External class reference (requires fully qualified name)
     * @DefaultFactory("java.util.UUID.randomUUID")
     * UUID id
     * }</pre>
     *
     * @param methodRef        the method reference; simple name (e.g., {@code "createDefault"})
     *                         or qualified name (e.g., {@code "Defaults.create"})
     * @param expectedType     the parameter type that the method's return type must be assignable to
     * @param enclosingClass   the class containing the annotated parameter
     * @param errorReporter    callback for reporting compilation errors
     * @param annotatedElement the parameter element to report errors against
     * @param typeUtils        type utilities for assignability checks
     * @param elementUtils     element utilities for resolving external classes;
     *                         if {@code null}, external class validation is skipped
     * @return {@code true} if the reference is valid;
     * {@code false} if a validation error was reported
     */
    static boolean validateFactoryMethod(String methodRef, TypeMirror expectedType,
                                         TypeElement enclosingClass, ErrorReporter errorReporter,
                                         Element annotatedElement, Types typeUtils,
                                         Elements elementUtils) {
        ParsedReference parsed = parseReference(methodRef, "@DefaultFactory",
                "method", annotatedElement, errorReporter);
        if (parsed == null) {
            return false;
        }

        TypeElement targetClass;
        if (parsed.isExternal()) {
            if (elementUtils == null) {
                return true; // Skip validation if no element utils
            }
            targetClass = resolveClass(parsed.className(), enclosingClass, elementUtils);
            if (targetClass == null) {
                errorReporter.error(annotatedElement,
                        "Class '" + parsed.className() + "' not found for @DefaultFactory(\"" + methodRef + "\"). " +
                                "Hint: Use the fully qualified class name (e.g., java.util.UUID.randomUUID).");
                return false;
            }
        } else {
            targetClass = enclosingClass;
        }

        return validateMethodInClass(targetClass, parsed.memberName(), expectedType,
                errorReporter, annotatedElement, typeUtils);
    }

    /**
     * Validates that a factory method with the given name exists in the target class.
     *
     * <p>Iterates through all methods in the class, checking for a match. If found,
     * validates the method is static, has no parameters, does not return void, and
     * has a compatible return type. If not found, reports an error with a typo
     * suggestion or list of available static no-arg methods.
     *
     * <p><b>Validation Order:</b>
     * <ol>
     *   <li>Find method by name</li>
     *   <li>Verify {@code static} modifier</li>
     *   <li>Verify zero parameters</li>
     *   <li>Verify non-void return type</li>
     *   <li>Verify return type compatibility using {@link Types#isAssignable}</li>
     * </ol>
     *
     * @param targetClass      the class to search for the method
     * @param methodName       the simple name of the method to find
     * @param expectedType     the type the method's return type must be assignable to
     * @param errorReporter    callback for reporting errors
     * @param annotatedElement the element to report errors against
     * @param typeUtils        type utilities for assignability checks
     * @return {@code true} if the method is valid, {@code false} if an error was reported
     */
    private static boolean validateMethodInClass(TypeElement targetClass, String methodName,
                                                 TypeMirror expectedType, ErrorReporter errorReporter,
                                                 Element annotatedElement, Types typeUtils) {
        List<String> staticMethods = new ArrayList<>();

        for (Element enclosed : targetClass.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                String name = method.getSimpleName().toString();

                if (method.getModifiers().contains(Modifier.STATIC) && method.getParameters().isEmpty()) {
                    staticMethods.add(name);
                }

                if (name.equals(methodName)) {
                    if (!method.getModifiers().contains(Modifier.STATIC)) {
                        errorReporter.error(annotatedElement,
                                "Factory method '" + methodName + "' in " + targetClass.getSimpleName() +
                                        " must be static.");
                        return false;
                    }

                    if (!method.getParameters().isEmpty()) {
                        errorReporter.error(annotatedElement,
                                "Factory method '" + methodName + "' in " + targetClass.getSimpleName() +
                                        " must have no parameters.");
                        return false;
                    }

                    TypeMirror returnType = method.getReturnType();
                    if (returnType.getKind() == TypeKind.VOID) {
                        errorReporter.error(annotatedElement,
                                "Factory method '" + methodName + "' in " + targetClass.getSimpleName() +
                                        " cannot return void.");
                        return false;
                    }

                    if (!typeUtils.isAssignable(returnType, expectedType)) {
                        errorReporter.error(annotatedElement,
                                "Factory method '" + methodName + "' in " + targetClass.getSimpleName() +
                                        " returns " + returnType + " which is not assignable to " + expectedType + ".");
                        return false;
                    }
                    return true;
                }
            }
        }

        // Method not found - provide helpful error
        reportNotFound("Factory method", methodName, targetClass, staticMethods, errorReporter, annotatedElement);
        return false;
    }

    // ==================== ERROR REPORTING ====================

    /**
     * Reports a "not found" error with typo suggestions or available alternatives.
     *
     * <p>Generates user-friendly error messages with actionable suggestions:
     * <ul>
     *   <li>If a similar name is found (via Levenshtein distance): "Did you mean 'X'?"</li>
     *   <li>If candidates exist but none are similar: "Available static fields/methods: X, Y, Z."</li>
     *   <li>If no candidates exist: "Ensure the field/method exists and is static."</li>
     * </ul>
     *
     * <p><b>Example Messages:</b>
     * <pre>
     * Field 'DEFUALT_NAME' not found in Config. Did you mean 'DEFAULT_NAME'?
     * Factory method 'createe' not found in Factory. Did you mean 'create()'?
     * Field 'UNKNOWN' not found in Config. Available static fields: NAME, VALUE, COUNT.
     * Factory method 'missing' not found in Empty. Ensure the method exists and is static with no parameters.
     * </pre>
     *
     * @param memberType       "Field" or "Factory method" for message formatting
     * @param memberName       the name that was not found
     * @param targetClass      the class that was searched
     * @param candidates       list of valid static field/method names for suggestions
     * @param errorReporter    callback for reporting the error
     * @param annotatedElement the element to report the error against
     */
    private static void reportNotFound(String memberType, String memberName, TypeElement targetClass,
                                       List<String> candidates, ErrorReporter errorReporter,
                                       Element annotatedElement) {
        String suggestion = findSimilar(memberName, candidates);
        StringBuilder message = new StringBuilder();
        message.append(memberType).append(" '").append(memberName).append("' not found in ")
                .append(targetClass.getSimpleName()).append(".");

        boolean isMethod = memberType.contains("method");

        if (suggestion != null) {
            message.append(" Did you mean '").append(suggestion);
            if (isMethod) message.append("()");
            message.append("'?");
        } else if (!candidates.isEmpty()) {
            if (isMethod) {
                message.append(" Available static no-arg methods: ");
                message.append(String.join(", ", candidates.stream().map(m -> m + "()").toList()));
            } else {
                message.append(" Available static fields: ");
                message.append(String.join(", ", candidates));
            }
            message.append(".");
        } else {
            if (isMethod) {
                message.append(" Ensure the method exists and is static with no parameters.");
            } else {
                message.append(" Ensure the field exists and is static.");
            }
        }

        errorReporter.error(annotatedElement, message.toString());
    }

    // ==================== TYPO DETECTION ====================

    /**
     * Finds the most similar string from a list of candidates using Levenshtein distance.
     *
     * <p>Compares the target string against each candidate (case-insensitive) and returns
     * the candidate with the smallest edit distance, provided it falls within an acceptable
     * threshold. This is used to provide "Did you mean...?" suggestions in error messages.
     *
     * <p><b>Threshold Calculation:</b>
     * <p>The threshold is calculated as {@code max(2, target.length() / 3)}, meaning:
     * <ul>
     *   <li>Short strings (≤6 chars): up to 2 edits allowed</li>
     *   <li>Longer strings: up to ~33% of the string length as edits</li>
     * </ul>
     *
     * <p><b>Examples:</b>
     * <pre>{@code
     * findSimilar("DEFUALT_NAME", List.of("DEFAULT_NAME", "OTHER_FIELD"))
     * // Returns "DEFAULT_NAME" (edit distance = 2)
     *
     * findSimilar("createe", List.of("create", "build", "make"))
     * // Returns "create" (edit distance = 1)
     *
     * findSimilar("xyz", List.of("abc", "def"))
     * // Returns null (no similar match within threshold)
     * }</pre>
     *
     * @param target     the string to find a match for (e.g., the misspelled name)
     * @param candidates the list of possible correct names
     * @return the most similar candidate within the threshold, or {@code null} if none qualify
     */
    static String findSimilar(String target, List<String> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int threshold = Math.max(2, target.length() / 3);

        for (String candidate : candidates) {
            int distance = levenshteinDistance(target.toLowerCase(), candidate.toLowerCase());
            if (distance < bestDistance && distance <= threshold) {
                bestDistance = distance;
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Computes the Levenshtein (edit) distance between two strings.
     *
     * <p>The Levenshtein distance is the minimum number of single-character edits
     * (insertions, deletions, or substitutions) required to transform one string
     * into another.
     *
     * <p><b>Implementation Notes:</b>
     * <ul>
     *   <li>Uses an optimized two-row dynamic programming approach (O(n) space)</li>
     *   <li>Includes early termination when strings differ greatly in length</li>
     *   <li>Time complexity: O(m × n) where m and n are string lengths</li>
     * </ul>
     *
     * <p><b>Examples:</b>
     * <ul>
     *   <li>{@code levenshteinDistance("kitten", "sitting")} → 3</li>
     *   <li>{@code levenshteinDistance("book", "back")} → 2</li>
     *   <li>{@code levenshteinDistance("same", "same")} → 0</li>
     * </ul>
     *
     * @param s1 the first string
     * @param s2 the second string
     * @return the minimum number of edits to transform s1 into s2 (non-negative)
     * @see <a href="https://en.wikipedia.org/wiki/Levenshtein_distance">Levenshtein distance (Wikipedia)</a>
     */
    static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        // Early termination if strings are very different in length
        if (Math.abs(len1 - len2) > Math.max(len1, len2) / 2) {
            return Math.max(len1, len2);
        }

        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[len2];
    }

    // ==================== SHARED INFRASTRUCTURE ====================

    /**
     * Parses a reference string into class and member components.
     *
     * <p>Handles both simple references (same-class) and qualified references (external class).
     * The last dot in the reference separates the class name from the member name.
     *
     * <p><b>Parsing Examples:</b>
     * <pre>
     * "MY_CONSTANT"              → (null, "MY_CONSTANT")
     * "Defaults.VALUE"           → ("Defaults", "VALUE")
     * "com.example.Util.create"  → ("com.example.Util", "create")
     * </pre>
     *
     * <p><b>Error Cases:</b>
     * <ul>
     *   <li>Empty or null reference → reports "requires a non-empty field/method reference"</li>
     *   <li>Trailing dot (e.g., "Class.") → reports "has empty field/method name"</li>
     * </ul>
     *
     * @param ref              the reference string (e.g., "CONSTANT" or "Class.CONSTANT")
     * @param annotationName   annotation name for error messages (e.g., "@DefaultValue(field=...)")
     * @param memberType       "field" or "method" for error messages
     * @param annotatedElement element to report errors against
     * @param errorReporter    callback for errors
     * @return parsed reference, or {@code null} if invalid (error already reported)
     */
    private static ParsedReference parseReference(String ref, String annotationName,
                                                  String memberType, Element annotatedElement,
                                                  ErrorReporter errorReporter) {
        if (ref == null || ref.isEmpty()) {
            errorReporter.error(annotatedElement,
                    annotationName + " requires a non-empty " + memberType + " reference.");
            return null;
        }

        int lastDot = ref.lastIndexOf('.');
        if (lastDot == -1) {
            return new ParsedReference(null, ref);
        }

        String className = ref.substring(0, lastDot);
        String memberName = ref.substring(lastDot + 1);

        if (memberName.isEmpty()) {
            errorReporter.error(annotatedElement,
                    annotationName + " reference '" + ref + "' has empty " + memberType + " name.");
            return null;
        }

        return new ParsedReference(className, memberName);
    }

    /**
     * Resolves a class name to a {@link TypeElement}.
     *
     * <p>Attempts resolution in the following order:
     * <ol>
     *   <li><b>Fully qualified name:</b> Direct lookup (e.g., {@code "java.util.UUID"})</li>
     *   <li><b>Same-package name:</b> Prepends enclosing class's package (e.g., {@code "Defaults"}
     *       becomes {@code "com.example.Defaults"} if the enclosing class is in {@code com.example})</li>
     * </ol>
     *
     * <p><b>Note:</b> Import statements are not accessible during annotation processing,
     * so classes outside the current package must use fully qualified names.
     *
     * @param className      the class name to resolve (may be simple or fully qualified)
     * @param enclosingClass the class containing the annotated element (for same-package lookup)
     * @param elementUtils   element utilities for resolution
     * @return the resolved type, or {@code null} if not found
     */
    private static TypeElement resolveClass(String className, TypeElement enclosingClass,
                                            Elements elementUtils) {
        if (elementUtils == null) {
            return null;
        }

        // Try fully qualified first
        TypeElement result = elementUtils.getTypeElement(className);
        if (result != null) {
            return result;
        }

        // Try same package
        String pkg = getPackageName(enclosingClass);
        if (!pkg.isEmpty()) {
            result = elementUtils.getTypeElement(pkg + "." + className);
        }

        return result;
    }

    /**
     * Extracts the package name from a type element.
     *
     * <p><b>Examples:</b>
     * <ul>
     *   <li>{@code com.example.MyClass} → {@code "com.example"}</li>
     *   <li>{@code MyClass} (default package) → {@code ""}</li>
     * </ul>
     *
     * @param type the type element to extract the package from
     * @return the package name, or empty string for the default package
     */
    private static String getPackageName(TypeElement type) {
        String qualifiedName = type.getQualifiedName().toString();
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(0, lastDot) : "";
    }

    /**
     * Holds a parsed reference split into class name and member name.
     *
     * <p>This record is the result of parsing a reference string like {@code "MY_CONSTANT"}
     * or {@code "com.example.Defaults.VALUE"} into its constituent parts.
     *
     * <p><b>Examples:</b>
     * <ul>
     *   <li>{@code "MY_CONSTANT"} → className=null, memberName="MY_CONSTANT"</li>
     *   <li>{@code "Defaults.VALUE"} → className="Defaults", memberName="VALUE"</li>
     *   <li>{@code "com.example.Defaults.VALUE"} → className="com.example.Defaults", memberName="VALUE"</li>
     * </ul>
     *
     * @param className  the class part of the reference, or {@code null} for same-class references
     * @param memberName the field or method name (always non-null after successful parsing)
     */
    private record ParsedReference(String className, String memberName) {
        /**
         * Returns whether this reference targets an external class.
         *
         * @return {@code true} if className is non-null (external reference);
         * {@code false} for same-class references
         */
        boolean isExternal() {
            return className != null;
        }
    }
}
