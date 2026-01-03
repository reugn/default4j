package io.github.reugn.default4j.integration;

import com.google.testing.compile.JavaFileObjects;
import io.github.reugn.default4j.util.RuntimeTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration tests for method-level @WithDefaults.
 * These tests actually execute the generated code and verify runtime behavior.
 *
 * <p>Note: Method helpers generate overloads for trailing defaults only.
 * The full-params signature is not generated (use the original method directly).
 */
@DisplayName("Method Examples (E2E)")
class MethodExamplesTest {

    @Test
    @DisplayName("Static utility method - executes with default parameter")
    void staticUtilityMethod() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.StringUtils",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class StringUtils {
                            @WithDefaults
                            public static String pad(String text, @DefaultValue("20") int width) {
                                return String.format("%-" + width + "s", text);
                            }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object instance = helper.newInstance("example.StringUtils");

        // Test with default width (20) - helper provides the default
        String result = (String) helper.invoke("example.StringUtilsDefaults", "pad", instance, "hello");
        assertThat(result).hasSize(20).startsWith("hello");
    }

    @Test
    @DisplayName("Instance method - modifies object state using default")
    void instanceMethodWithState() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Counter",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Counter {
                            private int count = 0;
                        
                            @WithDefaults
                            public void increment(@DefaultValue("1") int step) {
                                this.count += step;
                            }
                        
                            public int getCount() { return count; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object counter = helper.newInstance("example.Counter");

        // Increment with default step (1)
        helper.invoke("example.CounterDefaults", "increment", counter);
        assertThat(helper.invokeOn(counter, "getCount")).isEqualTo(1);

        // Increment again - state accumulates
        helper.invoke("example.CounterDefaults", "increment", counter);
        assertThat(helper.invokeOn(counter, "getCount")).isEqualTo(2);

        // Use original method directly for explicit step
        helper.invokeOn(counter, "increment", 5);
        assertThat(helper.invokeOn(counter, "getCount")).isEqualTo(7);
    }

    @Test
    @DisplayName("Instance overloaded methods - multiple defaults")
    void instanceOverloadedMethods() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Greeter",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Greeter {
                            @WithDefaults
                            public String greet(
                                    @DefaultValue("World") String name,
                                    @DefaultValue("Hello") String greeting) {
                                return greeting + ", " + name + "!";
                            }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object greeter = helper.newInstance("example.Greeter");

        // All defaults
        assertThat(helper.invoke("example.GreeterDefaults", "greet", greeter))
                .isEqualTo("Hello, World!");

        // Override first default only
        assertThat(helper.invoke("example.GreeterDefaults", "greet", greeter, "Alice"))
                .isEqualTo("Hello, Alice!");

        // For both custom, call original method directly
        assertThat(helper.invokeOn(greeter, "greet", "Bob", "Hi"))
                .isEqualTo("Hi, Bob!");
    }

    @Test
    @DisplayName("Named parameters - builder pattern with skipped parameters")
    void namedParameterBuilder() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Database",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Database {
                            @WithDefaults(named = true)
                            public String connect(
                                    @DefaultValue("localhost") String host,
                                    @DefaultValue("5432") int port,
                                    @DefaultValue("postgres") String user) {
                                return String.format("jdbc:postgresql://%s:%d?user=%s", host, port, user);
                            }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object db = helper.newInstance("example.Database");

        // Get the builder and set only host (skip port, use default user)
        Object builder = helper.invoke("example.DatabaseDefaults", "connect", db);
        helper.invokeOn(builder, "host", "prod.example.com");
        String result = (String) helper.invokeOn(builder, "call");

        assertThat(result).isEqualTo("jdbc:postgresql://prod.example.com:5432?user=postgres");
    }

    @Test
    @DisplayName("Named parameters - override specific parameters")
    void namedParameterOverrides() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Config",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Config {
                            @WithDefaults(named = true)
                            public String format(
                                    @DefaultValue("json") String type,
                                    @DefaultValue("false") boolean pretty,
                                    @DefaultValue("utf-8") String encoding) {
                                return type + ":" + pretty + ":" + encoding;
                            }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object config = helper.newInstance("example.Config");

        // All defaults
        Object builder1 = helper.invoke("example.ConfigDefaults", "format", config);
        assertThat(helper.invokeOn(builder1, "call")).isEqualTo("json:false:utf-8");

        // Override middle parameter only
        Object builder2 = helper.invoke("example.ConfigDefaults", "format", config);
        helper.invokeOn(builder2, "pretty", true);
        assertThat(helper.invokeOn(builder2, "call")).isEqualTo("json:true:utf-8");

        // Override first and last, skip middle
        Object builder3 = helper.invoke("example.ConfigDefaults", "format", config);
        helper.invokeOn(builder3, "type", "xml");
        helper.invokeOn(builder3, "encoding", "utf-16");
        assertThat(helper.invokeOn(builder3, "call")).isEqualTo("xml:false:utf-16");
    }

    @Test
    @DisplayName("Mixed required and optional parameters")
    void mixedRequiredAndOptional() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Formatter",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Formatter {
                            @WithDefaults
                            public String format(
                                    String message,
                                    @DefaultValue("INFO") String level,
                                    @DefaultValue("[") String prefix,
                                    @DefaultValue("]") String suffix) {
                                return prefix + level + suffix + " " + message;
                            }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object formatter = helper.newInstance("example.Formatter");

        // Required param only, all defaults
        assertThat(helper.invoke("example.FormatterDefaults", "format", formatter, "test"))
                .isEqualTo("[INFO] test");

        // Override level
        assertThat(helper.invoke("example.FormatterDefaults", "format", formatter, "test", "ERROR"))
                .isEqualTo("[ERROR] test");

        // Override level and prefix
        assertThat(helper.invoke("example.FormatterDefaults", "format", formatter, "test", "ERROR", "<"))
                .isEqualTo("<ERROR] test");

        // All custom - call original method directly
        assertThat(helper.invokeOn(formatter, "format", "test", "ERROR", "<", ">"))
                .isEqualTo("<ERROR> test");
    }

    @Test
    @DisplayName("Fluent method returning this")
    void fluentMethodReturningThis() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Builder",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Builder {
                            private int timeout;
                            private String host;
                        
                            @WithDefaults
                            public Builder withTimeout(@DefaultValue("30") int timeout) {
                                this.timeout = timeout;
                                return this;
                            }
                        
                            @WithDefaults
                            public Builder withHost(@DefaultValue("localhost") String host) {
                                this.host = host;
                                return this;
                            }
                        
                            public int getTimeout() { return timeout; }
                            public String getHost() { return host; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object builder = helper.newInstance("example.Builder");

        // Use default timeout
        Object result = helper.invoke("example.BuilderDefaults", "withTimeout", builder);
        assertThat(result).isSameAs(builder); // Fluent - returns same instance
        assertThat(helper.invokeOn(builder, "getTimeout")).isEqualTo(30);

        // Chain with default host
        helper.invoke("example.BuilderDefaults", "withHost", builder);
        assertThat(helper.invokeOn(builder, "getHost")).isEqualTo("localhost");

        // Use original method for explicit values
        helper.invokeOn(builder, "withTimeout", 60);
        assertThat(helper.invokeOn(builder, "getTimeout")).isEqualTo(60);
    }
}
