package io.github.reugn.default4j.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates default value helpers for external classes that you cannot annotate directly.
 * <p>
 * This is useful when working with:
 * <ul>
 *   <li>Records/classes from third-party libraries</li>
 *   <li>Generated code (protobuf, OpenAPI, etc.)</li>
 *   <li>Classes from other modules you don't control</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>
 * {@code
 * // External record from a library (you can't modify this)
 * public record ExternalConfig(String host, int port, Duration timeout) {}
 *
 * // Your code - define defaults via static fields
 * @IncludeDefaults(ExternalConfig.class)
 * public class Defaults {
 *     // Convention: DEFAULT_{componentName} (case-insensitive match)
 *     public static final String DEFAULT_HOST = "localhost";
 *     public static final int DEFAULT_PORT = 8080;
 *
 *     public static Duration defaultTimeout() {
 *         return Duration.ofSeconds(30);
 *     }
 * }
 *
 * // Generated usage:
 * ExternalConfig c1 = ExternalConfigDefaults.create();           // Uses all defaults
 * ExternalConfig c2 = ExternalConfigDefaults.create("prod.com"); // Custom host
 * }
 * </pre>
 *
 * <p><b>Default Value Sources:</b>
 * <p>
 * Defaults are read from the annotated class using these conventions:
 * <ul>
 *   <li>{@code DEFAULT_{COMPONENT_NAME}} - static field (e.g., {@code DEFAULT_HOST})</li>
 *   <li>{@code default{ComponentName}()} - static method (e.g., {@code defaultTimeout()})</li>
 * </ul>
 * <p>
 * Component names are matched case-insensitively after removing underscores.
 *
 * <p><b>Consecutive Defaults Requirement:</b>
 * <p>
 * In non-named mode (default), defaults must be consecutive from some point to the end.
 * You cannot have a required parameter after an optional one.
 * <pre>
 * {@code
 * // record Config(String host, int port, String database)
 *
 * // ERROR: database (required) comes after port (optional)
 * @IncludeDefaults(Config.class)
 * public class Defaults {
 *     public static final int DEFAULT_PORT = 5432;  // port has default
 *     // database has NO default - compile error!
 * }
 *
 * // OK: Use named=true for non-consecutive defaults
 * @IncludeDefaults(value = Config.class, named = true)
 * public class Defaults {
 *     public static final int DEFAULT_PORT = 5432;
 * }
 * }
 * </pre>
 *
 * <p><b>Named Mode:</b>
 * <p>
 * Named mode generates a fluent builder that allows skipping any parameter.
 * Use this when defaults are non-consecutive or you want maximum flexibility.
 * <pre>
 * {@code
 * @IncludeDefaults(value = ExternalConfig.class, named = true)
 * public class Defaults {
 *     public static final String DEFAULT_HOST = "localhost";
 *     public static final int DEFAULT_PORT = 8080;
 * }
 *
 * // Usage with named builder:
 * ExternalConfig cfg = ExternalConfigDefaults.create()
 *     .host("prod.com")
 *     .build();  // port and timeout use defaults
 * }
 * </pre>
 *
 * <p><b>Multiple Classes:</b>
 * <pre>
 * {@code @IncludeDefaults({ExternalConfig.class, AnotherRecord.class})
 * public class Defaults {
 *     // Defaults for ExternalConfig
 *     public static final String DEFAULT_HOST = "localhost";
 *
 *     // Defaults for AnotherRecord
 *     public static final String DEFAULT_NAME = "default";
 * }}
 * </pre>
 *
 * @see WithDefaults
 * @see DefaultValue
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface IncludeDefaults {
    /**
     * The external classes to generate default helpers for.
     * <p>
     * For records, all components are analyzed.
     * For regular classes, the public constructor that best matches the defined defaults
     * is selected (highest match count wins; ties broken by parameter count).
     *
     * @return array of classes to include
     */
    Class<?>[] value();

    /**
     * Whether to generate a named/fluent builder instead of overloaded methods.
     * <p>
     * When {@code true}, generates a builder that allows setting any parameter
     * by name, making it possible to skip parameters in the middle.
     *
     * @return true for named builder, false for overloaded methods
     */
    boolean named() default false;

    /**
     * The name of the factory method.
     * Default is "create".
     *
     * @return the factory method name
     */
    String methodName() default "create";
}
