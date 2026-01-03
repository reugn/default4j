package io.github.reugn.default4j.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a default value for a method parameter or record component.
 * <p>
 * The default can be either:
 * <ul>
 *   <li>A <b>string literal</b> via {@link #value()} - parsed according to the parameter type</li>
 *   <li>A <b>static field reference</b> via {@link #field()} - for constants and complex types</li>
 * </ul>
 *
 * <p><b>String Literal (value):</b>
 * <pre>
 * {@code
 * @WithDefaults
 * public void greet(
 *         @DefaultValue("World") String name,
 *         @DefaultValue("3") int times,
 *         @DefaultValue("true") boolean loud) {
 *     // ...
 * }
 * }
 * </pre>
 * <p>
 * Supported types for string literals:
 * <ul>
 *   <li>Primitives: int, long, double, float, boolean, byte, short, char</li>
 *   <li>Wrapper types: Integer, Long, Double, Float, Boolean, Byte, Short, Character</li>
 *   <li>String</li>
 *   <li>null (use "null" as value for nullable types)</li>
 * </ul>
 *
 * <p><b>Static Field Reference (field):</b>
 * <pre>
 * {@code
 * public class Defaults {
 *     public static final Duration TIMEOUT = Duration.ofSeconds(30);
 *     public static final String HOST = "localhost";
 * }
 *
 * public class Client {
 *     @WithDefaults
 *     public void connect(
 *             @DefaultValue(field = "Defaults.HOST") String host,
 *             @DefaultValue(field = "Defaults.TIMEOUT") Duration timeout) {
 *         // ...
 *     }
 * }
 * }
 * </pre>
 * <p>
 * For fields in the same class, just use the field name:
 * <pre>
 * {@code
 * public class Service {
 *     static final int DEFAULT_PORT = 8080;
 *
 *     @WithDefaults
 *     public void start(@DefaultValue(field = "DEFAULT_PORT") int port) {
 *         // ...
 *     }
 * }
 * }
 * </pre>
 *
 * <p><b>On Record Components:</b>
 * <pre>
 * {@code
 * @WithDefaults
 * public record Config(
 *         @DefaultValue("localhost") String host,
 *         @DefaultValue(field = "Config.DEFAULT_PORT") int port) {
 *     static final int DEFAULT_PORT = 8080;
 * }
 * }
 * </pre>
 *
 * @see DefaultFactory
 * @see WithDefaults
 */
@Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface DefaultValue {
    /**
     * The default value as a string literal.
     * <p>
     * Examples:
     * <ul>
     *   <li>For String: "hello"</li>
     *   <li>For int/Integer: "42"</li>
     *   <li>For boolean/Boolean: "true" or "false"</li>
     *   <li>For double/Double: "3.14"</li>
     *   <li>For null: "null"</li>
     *   <li>For char/Character: "a" (single character)</li>
     * </ul>
     * <p>
     * Either {@code value} or {@link #field()} must be specified, but not both.
     *
     * @return the default value as a string, or empty if using {@link #field()}
     */
    String value() default "";

    /**
     * Reference to a static field (constant).
     * <p>
     * The field can be specified as:
     * <ul>
     *   <li>{@code "FIELD_NAME"} - for fields in the same class</li>
     *   <li>{@code "ClassName.FIELD_NAME"} - for fields in another class (same package)</li>
     *   <li>{@code "com.example.ClassName.FIELD_NAME"} - fully qualified (required for other packages)</li>
     * </ul>
     * <p>
     * <b>Important:</b> For classes outside the current package, you must use the fully qualified
     * class name. The annotation processor cannot access import statements, so simple names like
     * {@code "UUID.something"} won't resolve. Use {@code "java.util.UUID.something"} instead.
     * <p>
     * The field must be:
     * <ul>
     *   <li>Static</li>
     *   <li>Accessible from the generated {@code {ClassName}Defaults} class
     *       (package-private or public; {@code private} is not allowed, even in the same class)</li>
     *   <li>Of a type assignable to the annotated parameter</li>
     * </ul>
     * <p>
     * Either {@code field} or {@link #value()} must be specified, but not both.
     *
     * @return the static field reference, or empty if using {@link #value()}
     */
    String field() default "";
}
