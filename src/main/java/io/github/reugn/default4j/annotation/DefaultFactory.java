package io.github.reugn.default4j.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a factory method to produce default values.
 * <p>
 * Use this annotation when you need:
 * <ul>
 *   <li>Computed defaults (e.g., {@code LocalDateTime.now()})</li>
 *   <li>Complex object construction</li>
 *   <li>Values that can't be expressed as literals</li>
 * </ul>
 * <p>
 * For static constants, prefer {@link DefaultValue#field()} instead.
 *
 * <p><b>Evaluation Timing:</b>
 * <p>
 * <b>Non-named mode (overloaded methods):</b> Factory method is called each invocation.
 * <pre>{@code
 * // Generated:
 * public static void log(Service instance, String message) {
 *     instance.log(message, Service.timestamp());  // Called each time
 * }
 * }</pre>
 * <p>
 * <b>Named mode (builder pattern):</b> Factory method is called once when the builder
 * is created, not when {@code call()} or {@code build()} is invoked.
 * <pre>{@code
 * // Generated:
 * public static final class LogBuilder {
 *     private LocalDateTime time = Service.timestamp();  // Called at builder creation
 *
 *     public void call() {
 *         instance.log(message, time);  // Uses cached value
 *     }
 * }
 * }</pre>
 * <p>
 * If you need fresh values in named mode, call the setter explicitly:
 * <pre>{@code
 * ServiceDefaults.log(svc).time(Service.timestamp()).call();
 * }</pre>
 *
 * <p><b>Factory Method in Same Class:</b>
 * <pre>
 * {@code
 * public class Service {
 *     static List<String> defaultTags() {
 *         return List.of("default", "service");
 *     }
 *
 *     static LocalDateTime timestamp() {
 *         return LocalDateTime.now();
 *     }
 *
 *     @WithDefaults
 *     public void process(
 *             @DefaultFactory("defaultTags") List<String> tags,
 *             @DefaultFactory("timestamp") LocalDateTime time) {
 *         // ...
 *     }
 * }
 * }
 * </pre>
 *
 * <p><b>Factory Method in External Class:</b>
 * <pre>
 * {@code
 * public class Factories {
 *     public static Config defaultConfig() {
 *         return new Config("localhost", 8080);
 *     }
 * }
 *
 * public class Service {
 *     @WithDefaults
 *     public void configure(
 *             @DefaultFactory("Factories.defaultConfig") Config config) {
 *         // ...
 *     }
 * }
 * }
 * </pre>
 *
 * <p><b>Method Reference Format:</b>
 * <ul>
 *   <li>{@code "methodName"} - method in the same class</li>
 *   <li>{@code "ClassName.methodName"} - method in another class (same package)</li>
 *   <li>{@code "com.example.ClassName.methodName"} - fully qualified (required for other packages)</li>
 * </ul>
 * <p>
 * <b>Important:</b> For classes outside the current package, you must use the fully qualified
 * class name. The annotation processor cannot access import statements, so simple names like
 * {@code "UUID.randomUUID"} won't resolve. Use {@code "java.util.UUID.randomUUID"} instead.
 *
 * <p><b>Requirements:</b>
 * <ul>
 *   <li>Factory method must be {@code static}</li>
 *   <li>Factory method must have no parameters</li>
 *   <li>Factory method must be accessible from the generated {@code {ClassName}Defaults} class
 *       (package-private or public; {@code private} is not allowed, even in the same class)</li>
 *   <li>Return type must be assignable to the parameter type</li>
 * </ul>
 *
 * @see DefaultValue
 * @see WithDefaults
 */
@Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface DefaultFactory {
    /**
     * Reference to a static factory method.
     * <p>
     * The method can be specified as:
     * <ul>
     *   <li>{@code "methodName"} - for methods in the same class</li>
     *   <li>{@code "ClassName.methodName"} - for methods in another class (same package)</li>
     *   <li>{@code "com.example.ClassName.methodName"} - fully qualified</li>
     * </ul>
     * <p>
     * The method must be static, have no parameters, and be accessible
     * from the generated class (package-private or public, not private).
     *
     * @return the factory method reference
     */
    String value();
}
