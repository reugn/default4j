package io.github.reugn.default4j.integration;

import com.google.testing.compile.JavaFileObjects;
import io.github.reugn.default4j.util.RuntimeTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration tests for class-level @WithDefaults.
 * These tests actually execute the generated code and verify runtime behavior.
 */
@DisplayName("Class-Level Examples (E2E)")
class ClassLevelExamplesTest {

    @Test
    @DisplayName("Class-level with factory methods")
    void classLevelFactory() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Settings",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults
                        public class Settings {
                            private final String theme;
                            private final String language;
                            private final int fontSize;
                        
                            public Settings(
                                    @DefaultValue("light") String theme,
                                    @DefaultValue("en") String language,
                                    @DefaultValue("12") int fontSize) {
                                this.theme = theme;
                                this.language = language;
                                this.fontSize = fontSize;
                            }
                        
                            public String getTheme() { return theme; }
                            public String getLanguage() { return language; }
                            public int getFontSize() { return fontSize; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // All defaults
        Object settings1 = helper.invoke("example.SettingsDefaults", "create");
        assertThat(helper.invokeOn(settings1, "getTheme")).isEqualTo("light");
        assertThat(helper.invokeOn(settings1, "getLanguage")).isEqualTo("en");
        assertThat(helper.invokeOn(settings1, "getFontSize")).isEqualTo(12);

        // Override theme
        Object settings2 = helper.invoke("example.SettingsDefaults", "create", "dark");
        assertThat(helper.invokeOn(settings2, "getTheme")).isEqualTo("dark");
        assertThat(helper.invokeOn(settings2, "getLanguage")).isEqualTo("en");

        // Override theme and language
        Object settings3 = helper.invoke("example.SettingsDefaults", "create", "dark", "fr");
        assertThat(helper.invokeOn(settings3, "getTheme")).isEqualTo("dark");
        assertThat(helper.invokeOn(settings3, "getLanguage")).isEqualTo("fr");
    }

    @Test
    @DisplayName("Class-level with named builder")
    void classLevelNamed() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.AppConfig",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults(named = true, methodName = "builder")
                        public class AppConfig {
                            private final String host;
                            private final int port;
                            private final int maxConnections;
                        
                            public AppConfig(
                                    @DefaultValue("localhost") String host,
                                    @DefaultValue("8080") int port,
                                    @DefaultValue("10") int maxConnections) {
                                this.host = host;
                                this.port = port;
                                this.maxConnections = maxConnections;
                            }
                        
                            public String getHost() { return host; }
                            public int getPort() { return port; }
                            public int getMaxConnections() { return maxConnections; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // All defaults via builder
        Object builder1 = helper.invoke("example.AppConfigDefaults", "builder");
        Object config1 = helper.invokeOn(builder1, "build");
        assertThat(helper.invokeOn(config1, "getHost")).isEqualTo("localhost");
        assertThat(helper.invokeOn(config1, "getPort")).isEqualTo(8080);
        assertThat(helper.invokeOn(config1, "getMaxConnections")).isEqualTo(10);

        // Override only maxConnections (skip host and port)
        Object builder2 = helper.invoke("example.AppConfigDefaults", "builder");
        helper.invokeOn(builder2, "maxConnections", 100);
        Object config2 = helper.invokeOn(builder2, "build");
        assertThat(helper.invokeOn(config2, "getHost")).isEqualTo("localhost");
        assertThat(helper.invokeOn(config2, "getPort")).isEqualTo(8080);
        assertThat(helper.invokeOn(config2, "getMaxConnections")).isEqualTo(100);

        // Override host and port
        Object builder3 = helper.invoke("example.AppConfigDefaults", "builder");
        helper.invokeOn(builder3, "host", "prod.example.com");
        helper.invokeOn(builder3, "port", 443);
        Object config3 = helper.invokeOn(builder3, "build");
        assertThat(helper.invokeOn(config3, "getHost")).isEqualTo("prod.example.com");
        assertThat(helper.invokeOn(config3, "getPort")).isEqualTo(443);
    }

    @Test
    @DisplayName("Class with multiple constructors - only ones with defaults get factories")
    void multipleConstructors() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Service",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults
                        public class Service {
                            private final String name;
                            private final boolean enabled;
                        
                            // This constructor has defaults - gets factory methods
                            public Service(
                                    @DefaultValue("default-service") String name,
                                    @DefaultValue("true") boolean enabled) {
                                this.name = name;
                                this.enabled = enabled;
                            }
                        
                            public String getName() { return name; }
                            public boolean isEnabled() { return enabled; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // All defaults
        Object service1 = helper.invoke("example.ServiceDefaults", "create");
        assertThat(helper.invokeOn(service1, "getName")).isEqualTo("default-service");
        assertThat(helper.invokeOn(service1, "isEnabled")).isEqualTo(true);

        // Override name
        Object service2 = helper.invoke("example.ServiceDefaults", "create", "my-service");
        assertThat(helper.invokeOn(service2, "getName")).isEqualTo("my-service");
        assertThat(helper.invokeOn(service2, "isEnabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("Class-level annotation applies to methods with defaults")
    void classLevelIncludesMethods() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Calculator",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults
                        public class Calculator {
                            private int result = 0;
                        
                            // Constructor with defaults
                            public Calculator(@DefaultValue("0") int initial) {
                                this.result = initial;
                            }
                        
                            // Method with defaults - should also get helpers
                            public int add(int a, @DefaultValue("1") int b) {
                                result = a + b;
                                return result;
                            }
                        
                            // Another method with defaults
                            public String format(@DefaultValue("Result: ") String prefix) {
                                return prefix + result;
                            }
                        
                            public int getResult() { return result; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Test constructor defaults
        Object calc1 = helper.invoke("example.CalculatorDefaults", "create");
        assertThat(helper.invokeOn(calc1, "getResult")).isEqualTo(0);

        // Test method defaults - add with default b=1
        // Use newInstance to create calculator directly since we can't use factory with Integer
        Object calc2 = helper.newInstance("example.Calculator", new Class<?>[]{int.class}, 10);
        int addResult = (int) helper.invoke("example.CalculatorDefaults", "add", calc2, 5);
        assertThat(addResult).isEqualTo(6); // 5 + 1 (default)

        // Test format method with default prefix
        Object calc3 = helper.newInstance("example.Calculator", new Class<?>[]{int.class}, 42);
        String formatted = (String) helper.invoke("example.CalculatorDefaults", "format", calc3);
        assertThat(formatted).isEqualTo("Result: 42");

        // Test format with custom prefix - call original method directly
        String customFormatted = (String) helper.invokeOn(calc3, "format", "Value: ");
        assertThat(customFormatted).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("Class-level annotation processes methods with @DefaultFactory")
    void classLevelWithDefaultFactory() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Logger",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultFactory;
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults
                        public class Logger {
                            private String lastLog;
                        
                            public Logger() {}
                        
                            static long getTimestamp() {
                                return 1234567890L;
                            }
                        
                            // Required param first, then all defaults
                            public String log(
                                    String message,
                                    @DefaultValue("INFO") String level,
                                    @DefaultFactory("getTimestamp") long timestamp) {
                                lastLog = "[" + level + "] " + message + " @" + timestamp;
                                return lastLog;
                            }
                        
                            public String getLastLog() { return lastLog; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Create logger
        Object logger = helper.newInstance("example.Logger");

        // Log with defaults for level and timestamp
        String result = (String) helper.invoke("example.LoggerDefaults", "log", logger, "Test message");
        assertThat(result).isEqualTo("[INFO] Test message @1234567890");

        // Log with custom level
        String result2 = (String) helper.invoke("example.LoggerDefaults", "log", logger, "Warning!", "WARN");
        assertThat(result2).isEqualTo("[WARN] Warning! @1234567890");
    }

    @Test
    @DisplayName("Method-level annotation takes precedence over class-level (named override)")
    void methodLevelPrecedenceNamed() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Service",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults(named = true)  // Class wants builder mode
                        public class Service {
                            private String lastCall;
                        
                            public Service() {}
                        
                            // This method uses class-level named=true (builder)
                            public void configure(
                                    @DefaultValue("localhost") String host,
                                    @DefaultValue("8080") int port) {
                                lastCall = "configure:" + host + ":" + port;
                            }
                        
                            // This method overrides with named=false (overloads)
                            @WithDefaults(named = false)
                            public void connect(
                                    @DefaultValue("default-db") String database,
                                    @DefaultValue("5") int timeout) {
                                lastCall = "connect:" + database + ":" + timeout;
                            }
                        
                            public String getLastCall() { return lastCall; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object service = helper.newInstance("example.Service");

        // configure uses class-level named=true -> has builder
        Object configureBuilder = helper.invoke("example.ServiceDefaults", "configure", service);
        assertThat(configureBuilder).isNotNull();
        assertThat(configureBuilder.getClass().getSimpleName()).isEqualTo("ConfigureBuilder");
        helper.invokeOn(configureBuilder, "call");
        assertThat(helper.invokeOn(service, "getLastCall")).isEqualTo("configure:localhost:8080");

        // connect uses method-level named=false -> has static overloads
        helper.invoke("example.ServiceDefaults", "connect", service);  // all defaults
        assertThat(helper.invokeOn(service, "getLastCall")).isEqualTo("connect:default-db:5");

        helper.invoke("example.ServiceDefaults", "connect", service, "mydb");  // override database
        assertThat(helper.invokeOn(service, "getLastCall")).isEqualTo("connect:mydb:5");
    }

    @Test
    @DisplayName("Constructor-level annotation takes precedence over class-level")
    void constructorLevelPrecedence() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Config",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults(named = true, methodName = "builder")  // Class wants builder
                        public class Config {
                            private final String host;
                            private final int port;
                            private final String mode;
                        
                            // Uses class-level settings (named=true, methodName="builder")
                            public Config(
                                    @DefaultValue("localhost") String host,
                                    @DefaultValue("8080") int port) {
                                this.host = host;
                                this.port = port;
                                this.mode = "simple";
                            }
                        
                            // Overrides with its own settings (named=false, methodName="create")
                            @WithDefaults(named = false, methodName = "create")
                            public Config(
                                    @DefaultValue("localhost") String host,
                                    @DefaultValue("8080") int port,
                                    @DefaultValue("standard") String mode) {
                                this.host = host;
                                this.port = port;
                                this.mode = mode;
                            }
                        
                            public String getHost() { return host; }
                            public int getPort() { return port; }
                            public String getMode() { return mode; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // 2-arg constructor uses class-level named=true -> builder via "builder"
        Object builderInstance = helper.invoke("example.ConfigDefaults", "builder");
        assertThat(builderInstance).isNotNull();
        Object config1 = helper.invokeOn(builderInstance, "build");
        assertThat(helper.invokeOn(config1, "getMode")).isEqualTo("simple");

        // 3-arg constructor uses its own named=false -> static factories via "create"
        Object config2 = helper.invoke("example.ConfigDefaults", "create");
        assertThat(helper.invokeOn(config2, "getMode")).isEqualTo("standard");

        Object config3 = helper.invoke("example.ConfigDefaults", "create", "prod.example.com");
        assertThat(helper.invokeOn(config3, "getHost")).isEqualTo("prod.example.com");
        assertThat(helper.invokeOn(config3, "getMode")).isEqualTo("standard");
    }

    @Test
    @DisplayName("Method without @WithDefaults but with defaults inherits class settings")
    void methodInheritsClassSettings() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Api",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults(named = true)
                        public class Api {
                            public Api() {}
                        
                            // No @WithDefaults on method - inherits class-level named=true
                            public String fetch(
                                    @DefaultValue("/api") String path,
                                    @DefaultValue("GET") String method) {
                                return method + " " + path;
                            }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object api = helper.newInstance("example.Api");

        // fetch inherits named=true from class -> has builder
        Object fetchBuilder = helper.invoke("example.ApiDefaults", "fetch", api);
        assertThat(fetchBuilder).isNotNull();
        assertThat(fetchBuilder.getClass().getSimpleName()).isEqualTo("FetchBuilder");

        // Use the builder
        helper.invokeOn(fetchBuilder, "path", "/users");
        String result = (String) helper.invokeOn(fetchBuilder, "call");
        assertThat(result).isEqualTo("GET /users");
    }
}
