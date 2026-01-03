package io.github.reugn.default4j.processor;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import java.util.Set;

/**
 * Validates literal default values for primitive and wrapper types.
 * <p>
 * Handles parsing validation for:
 * <ul>
 *   <li>Numeric types (int, long, double, float, byte, short and their wrappers)</li>
 *   <li>Boolean types (boolean and Boolean)</li>
 *   <li>Character types (char and Character)</li>
 *   <li>Null literals for reference types</li>
 *   <li>String and other reference types</li>
 * </ul>
 *
 * @see ValidationUtils
 */
final class LiteralValidator {

    static final Set<String> NUMERIC_TYPES = Set.of(
            "int", "java.lang.Integer",
            "long", "java.lang.Long",
            "double", "java.lang.Double",
            "float", "java.lang.Float",
            "byte", "java.lang.Byte",
            "short", "java.lang.Short"
    );

    static final Set<String> BOOLEAN_TYPES = Set.of(
            "boolean", "java.lang.Boolean"
    );

    static final Set<String> CHAR_TYPES = Set.of(
            "char", "java.lang.Character"
    );

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "int", "long", "double", "float", "byte", "short", "char", "boolean"
    );

    private LiteralValidator() {
    }

    /**
     * Validates that a string literal value can be parsed as the target type.
     * <p>
     * Handles:
     * <ul>
     *   <li>Numeric types: validates the value can be parsed as int, long, double, float, byte, or short</li>
     *   <li>Boolean types: value must be exactly "true" or "false"</li>
     *   <li>Character types: value must not be empty</li>
     *   <li>Null literal: valid for reference types, invalid for primitives</li>
     *   <li>String and other reference types: any value is acceptable</li>
     * </ul>
     *
     * @param value            the string value to validate
     * @param type             the target type the value should be parseable as
     * @param annotatedElement the element to report errors against
     * @param errorReporter    callback for reporting errors
     * @return {@code true} if the value is valid, {@code false} if an error was reported
     */
    static boolean validateParseable(String value, TypeMirror type,
                                     Element annotatedElement, ErrorReporter errorReporter) {
        String typeStr = type.toString();

        // "null" is valid for any reference type
        if ("null".equals(value)) {
            if (isPrimitive(typeStr)) {
                errorReporter.error(annotatedElement,
                        "'null' is not a valid default for primitive type '" + typeStr + "'.");
                return false;
            }
            return true;
        }

        // Validate numeric types
        if (NUMERIC_TYPES.contains(typeStr)) {
            return validateNumericLiteral(value, typeStr, annotatedElement, errorReporter);
        }

        // Validate boolean types
        if (BOOLEAN_TYPES.contains(typeStr)) {
            if (!"true".equals(value) && !"false".equals(value)) {
                errorReporter.error(annotatedElement,
                        "'" + value + "' is not a valid boolean. Use 'true' or 'false'.");
                return false;
            }
            return true;
        }

        // Validate char types
        if (CHAR_TYPES.contains(typeStr)) {
            if (value.isEmpty()) {
                errorReporter.error(annotatedElement, "Empty string is not a valid char.");
                return false;
            }
            return true;
        }

        // String and other types - any value is acceptable
        return true;
    }

    /**
     * Validates a numeric literal value can be parsed as the specified type.
     *
     * @param value            the string value to parse
     * @param typeStr          the target numeric type
     * @param annotatedElement the element to report errors against
     * @param errorReporter    callback for reporting errors
     * @return {@code true} if the value is valid, {@code false} if an error was reported
     */
    static boolean validateNumericLiteral(String value, String typeStr,
                                          Element annotatedElement, ErrorReporter errorReporter) {
        // Remove type suffixes for parsing
        String cleanValue = value.replaceAll("[LlDdFf]$", "");

        try {
            switch (typeStr) {
                case "int", "java.lang.Integer" -> Integer.parseInt(cleanValue);
                case "long", "java.lang.Long" -> Long.parseLong(cleanValue);
                case "double", "java.lang.Double" -> Double.parseDouble(cleanValue);
                case "float", "java.lang.Float" -> Float.parseFloat(cleanValue);
                case "byte", "java.lang.Byte" -> {
                    int byteVal = Integer.parseInt(cleanValue);
                    if (byteVal < Byte.MIN_VALUE || byteVal > Byte.MAX_VALUE) {
                        throw new NumberFormatException("Value out of range for byte");
                    }
                }
                case "short", "java.lang.Short" -> {
                    int shortVal = Integer.parseInt(cleanValue);
                    if (shortVal < Short.MIN_VALUE || shortVal > Short.MAX_VALUE) {
                        throw new NumberFormatException("Value out of range for short");
                    }
                }
            }
        } catch (NumberFormatException e) {
            String simpleType = getSimpleTypeName(typeStr);
            errorReporter.error(annotatedElement,
                    "'" + value + "' is not a valid " + simpleType.toLowerCase() + ".");
            return false;
        }
        return true;
    }

    /**
     * Checks if a type string represents a primitive type.
     *
     * @param typeStr the type string to check
     * @return {@code true} if the type is a primitive
     */
    static boolean isPrimitive(String typeStr) {
        return PRIMITIVE_TYPES.contains(typeStr);
    }

    /**
     * Gets a simple type name for error messages.
     * Removes package prefix if present.
     *
     * @param typeStr the fully qualified type string
     * @return the simple type name
     */
    static String getSimpleTypeName(String typeStr) {
        int lastDot = typeStr.lastIndexOf('.');
        return lastDot >= 0 ? typeStr.substring(lastDot + 1) : typeStr;
    }
}
