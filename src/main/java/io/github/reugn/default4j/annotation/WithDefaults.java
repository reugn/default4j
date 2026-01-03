package io.github.reugn.default4j.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates helper methods with default parameter values.
 * <p>
 * This annotation can be applied to:
 * <ul>
 *   <li><b>Methods</b>: Generates overloaded or named-parameter helper methods</li>
 *   <li><b>Constructors</b>: Generates factory methods with default parameters</li>
 *   <li><b>Classes</b>: Applies to all constructors that have {@link DefaultValue} parameters</li>
 *   <li><b>Records</b>: Works with Java records - place {@link DefaultValue} on record components</li>
 * </ul>
 * <p>
 * All generated code is placed in a single {@code {ClassName}Defaults} class.
 *
 * <p><b>Method Examples:</b>
 * <p><b>Static utility method:</b>
 * <pre>
 * {@code
 * public class StringUtils {
 *     @WithDefaults
 *     public static String pad(String text, @DefaultValue("20") int width) {
 *         return String.format("%-" + width + "s", text);
 *     }
 * }
 *
 * // Usage - generated helpers call the static method directly:
 * StringUtilsDefaults.pad("hello");      // Pads to 20 chars
 * StringUtilsDefaults.pad("hello", 10);  // Pads to 10 chars
 * }
 * </pre>
 *
 * <p><b>Instance method with state:</b>
 * <p>For non-static methods, the generated helper's first parameter is the instance
 * on which the method will be invoked:
 * <pre>
 * {@code
 * public class Counter {
 *     private int count = 0;
 *
 *     @WithDefaults
 *     public void increment(@DefaultValue("1") int step) {
 *         this.count += step;  // Modifies instance state
 *     }
 * }
 *
 * // Usage - first parameter is the instance:
 * Counter c = new Counter();
 * CounterDefaults.increment(c);     // Calls c.increment(1), c.count is now 1
 * CounterDefaults.increment(c, 5);  // Calls c.increment(5), c.count is now 6
 * }
 * </pre>
 *
 * <p><b>Named mode</b> ({@code named = true}) - skip any parameter:
 * <p>For non-static methods, pass the instance to the entry method, then chain setters
 * and call {@code call()} to invoke:
 * <pre>
 * {@code
 * public class Database {
 *     @WithDefaults(named = true)
 *     public Connection connect(
 *             @DefaultValue("localhost") String host,
 *             @DefaultValue("5432") int port) {
 *         return createConnection(host, port);
 *     }
 * }
 *
 * // Usage - instance passed to entry method:
 * Database db = new Database();
 * DatabaseDefaults.connect(db).port(3306).call();  // Calls db.connect("localhost", 3306)
 * DatabaseDefaults.connect(db).call();             // Calls db.connect("localhost", 5432)
 * }
 * </pre>
 *
 * <p><b>Constructor Examples:</b>
 * <p><b>Factory methods:</b>
 * <pre>
 * {@code
 * public class User {
 *     @WithDefaults
 *     public User(String name, @DefaultValue("USER") String role) {
 *         // ...
 *     }
 * }
 *
 * // Usage:
 * UserDefaults.create("Alice");         // Uses default role
 * UserDefaults.create("Bob", "ADMIN");  // Custom role
 * }
 * </pre>
 *
 * <p><b>Named constructor builder</b> ({@code named = true}):
 * <pre>
 * {@code
 * public class Config {
 *     @WithDefaults(named = true)
 *     public Config(
 *             @DefaultValue("localhost") String host,
 *             @DefaultValue("8080") int port) {
 *         // ...
 *     }
 * }
 *
 * // Usage:
 * ConfigDefaults.create().port(3000).build();  // Uses default host
 * }
 * </pre>
 *
 * <p><b>Class-Level Annotation:</b>
 * <p>Apply to all constructors with {@link DefaultValue} parameters:
 * <pre>
 * {@code
 * @WithDefaults
 * public class Settings {
 *     public Settings(
 *             @DefaultValue("default") String name,
 *             @DefaultValue("100") int value) {
 *         // ...
 *     }
 * }
 *
 * // Usage:
 * SettingsDefaults.create();          // All defaults
 * SettingsDefaults.create("custom");  // Custom name
 * }
 * </pre>
 *
 * <p><b>Record Support:</b>
 * <p>Place {@link DefaultValue} directly on record components:
 * <pre>
 * {@code
 * @WithDefaults
 * public record ServerConfig(
 *         @DefaultValue("localhost") String host,
 *         @DefaultValue("8080") int port) {}
 *
 * // Usage:
 * ServerConfigDefaults.create();                   // All defaults
 * ServerConfigDefaults.create("api.example.com");  // Custom host
 * }
 * </pre>
 *
 * @see DefaultValue
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface WithDefaults {

    /**
     * Whether to generate a named/fluent builder instead of overloaded methods.
     * <p>
     * When {@code true}, generates a builder that allows setting any parameter
     * by name, making it possible to skip parameters in the middle.
     * <p>
     * <b>Applies to:</b> methods, constructors, and classes.
     *
     * @return true for named builder, false for overloaded methods
     */
    boolean named() default false;

    /**
     * The name of the factory method for constructors.
     * Default is "create".
     * <p>
     * <b>Applies to:</b> constructors and classes (ignored for methods).
     *
     * @return the factory method name
     */
    String methodName() default "create";
}
