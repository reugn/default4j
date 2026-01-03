package io.github.reugn.default4j.integration;

import com.google.testing.compile.JavaFileObjects;
import io.github.reugn.default4j.util.RuntimeTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration tests for constructor-level @WithDefaults.
 * These tests actually execute the generated code and verify runtime behavior.
 *
 * <p>Note: Constructor helpers generate overloads for trailing defaults only.
 * The full-params signature is not generated (use the original constructor).
 */
@DisplayName("Constructor Examples (E2E)")
class ConstructorExamplesTest {

    @Test
    @DisplayName("Factory methods create instances with defaults")
    void factoryMethods() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.User",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class User {
                            private final String name;
                            private final String email;
                            private final String role;
                        
                            @WithDefaults
                            public User(
                                    String name,
                                    @DefaultValue("user@example.com") String email,
                                    @DefaultValue("USER") String role) {
                                this.name = name;
                                this.email = email;
                                this.role = role;
                            }
                        
                            public String getName() { return name; }
                            public String getEmail() { return email; }
                            public String getRole() { return role; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Create with only required param - uses all defaults
        Object user1 = helper.invoke("example.UserDefaults", "create", "Alice");
        assertThat(helper.invokeOn(user1, "getName")).isEqualTo("Alice");
        assertThat(helper.invokeOn(user1, "getEmail")).isEqualTo("user@example.com");
        assertThat(helper.invokeOn(user1, "getRole")).isEqualTo("USER");

        // Create with custom email - role uses default
        Object user2 = helper.invoke("example.UserDefaults", "create", "Bob", "bob@test.com");
        assertThat(helper.invokeOn(user2, "getName")).isEqualTo("Bob");
        assertThat(helper.invokeOn(user2, "getEmail")).isEqualTo("bob@test.com");
        assertThat(helper.invokeOn(user2, "getRole")).isEqualTo("USER");

        // For all custom params, use the original constructor
        Object user3 = helper.newInstance("example.User",
                new Class<?>[]{String.class, String.class, String.class},
                "Carol", "carol@x.com", "ADMIN");
        assertThat(helper.invokeOn(user3, "getRole")).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Named constructor builder - skip any parameter")
    void namedConstructorBuilder() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.HttpClient",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class HttpClient {
                            private final String baseUrl;
                            private final int timeout;
                            private final int retries;
                            private final boolean followRedirects;
                        
                            @WithDefaults(named = true)
                            public HttpClient(
                                    @DefaultValue("http://localhost") String baseUrl,
                                    @DefaultValue("3000") int timeout,
                                    @DefaultValue("3") int retries,
                                    @DefaultValue("true") boolean followRedirects) {
                                this.baseUrl = baseUrl;
                                this.timeout = timeout;
                                this.retries = retries;
                                this.followRedirects = followRedirects;
                            }
                        
                            public String getBaseUrl() { return baseUrl; }
                            public int getTimeout() { return timeout; }
                            public int getRetries() { return retries; }
                            public boolean isFollowRedirects() { return followRedirects; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Use all defaults
        Object builder1 = helper.invoke("example.HttpClientDefaults", "create");
        Object client1 = helper.invokeOn(builder1, "build");
        assertThat(helper.invokeOn(client1, "getBaseUrl")).isEqualTo("http://localhost");
        assertThat(helper.invokeOn(client1, "getTimeout")).isEqualTo(3000);
        assertThat(helper.invokeOn(client1, "getRetries")).isEqualTo(3);
        assertThat(helper.invokeOn(client1, "isFollowRedirects")).isEqualTo(true);

        // Override only timeout (skip baseUrl, retries, followRedirects)
        Object builder2 = helper.invoke("example.HttpClientDefaults", "create");
        helper.invokeOn(builder2, "timeout", 5000);
        Object client2 = helper.invokeOn(builder2, "build");
        assertThat(helper.invokeOn(client2, "getBaseUrl")).isEqualTo("http://localhost");
        assertThat(helper.invokeOn(client2, "getTimeout")).isEqualTo(5000);

        // Override baseUrl and retries (skip timeout, followRedirects)
        Object builder3 = helper.invoke("example.HttpClientDefaults", "create");
        helper.invokeOn(builder3, "baseUrl", "https://api.example.com");
        helper.invokeOn(builder3, "retries", 5);
        Object client3 = helper.invokeOn(builder3, "build");
        assertThat(helper.invokeOn(client3, "getBaseUrl")).isEqualTo("https://api.example.com");
        assertThat(helper.invokeOn(client3, "getTimeout")).isEqualTo(3000);
        assertThat(helper.invokeOn(client3, "getRetries")).isEqualTo(5);
    }

    @Test
    @DisplayName("Custom factory method name")
    void customMethodName() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Point",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Point {
                            private final int x;
                            private final int y;
                        
                            @WithDefaults(methodName = "of")
                            public Point(
                                    @DefaultValue("0") int x,
                                    @DefaultValue("0") int y) {
                                this.x = x;
                                this.y = y;
                            }
                        
                            public int getX() { return x; }
                            public int getY() { return y; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Origin - all defaults
        Object origin = helper.invoke("example.PointDefaults", "of");
        assertThat(helper.invokeOn(origin, "getX")).isEqualTo(0);
        assertThat(helper.invokeOn(origin, "getY")).isEqualTo(0);

        // Custom x only
        Object p1 = helper.invoke("example.PointDefaults", "of", 5);
        assertThat(helper.invokeOn(p1, "getX")).isEqualTo(5);
        assertThat(helper.invokeOn(p1, "getY")).isEqualTo(0);

        // Both custom - use original constructor
        Object p2 = helper.newInstance("example.Point",
                new Class<?>[]{int.class, int.class}, 10, 20);
        assertThat(helper.invokeOn(p2, "getX")).isEqualTo(10);
        assertThat(helper.invokeOn(p2, "getY")).isEqualTo(20);
    }

    @Test
    @DisplayName("All parameters with defaults - zero-arg factory")
    void allDefaults() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Config",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Config {
                            private final String environment;
                            private final boolean debug;
                            private final int port;
                        
                            @WithDefaults
                            public Config(
                                    @DefaultValue("development") String environment,
                                    @DefaultValue("false") boolean debug,
                                    @DefaultValue("8080") int port) {
                                this.environment = environment;
                                this.debug = debug;
                                this.port = port;
                            }
                        
                            public String getEnvironment() { return environment; }
                            public boolean isDebug() { return debug; }
                            public int getPort() { return port; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // All defaults - zero-arg call
        Object config1 = helper.invoke("example.ConfigDefaults", "create");
        assertThat(helper.invokeOn(config1, "getEnvironment")).isEqualTo("development");
        assertThat(helper.invokeOn(config1, "isDebug")).isEqualTo(false);
        assertThat(helper.invokeOn(config1, "getPort")).isEqualTo(8080);

        // Override environment only
        Object config2 = helper.invoke("example.ConfigDefaults", "create", "production");
        assertThat(helper.invokeOn(config2, "getEnvironment")).isEqualTo("production");
        assertThat(helper.invokeOn(config2, "isDebug")).isEqualTo(false);

        // Override environment and debug
        Object config3 = helper.invoke("example.ConfigDefaults", "create", "production", true);
        assertThat(helper.invokeOn(config3, "getEnvironment")).isEqualTo("production");
        assertThat(helper.invokeOn(config3, "isDebug")).isEqualTo(true);
        assertThat(helper.invokeOn(config3, "getPort")).isEqualTo(8080);
    }

    @Test
    @DisplayName("Constructor with mixed required and optional")
    void mixedRequiredAndOptional() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Database",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Database {
                            private final String name;
                            private final String host;
                            private final int port;
                        
                            @WithDefaults
                            public Database(
                                    String name,
                                    @DefaultValue("localhost") String host,
                                    @DefaultValue("5432") int port) {
                                this.name = name;
                                this.host = host;
                                this.port = port;
                            }
                        
                            public String getName() { return name; }
                            public String getHost() { return host; }
                            public int getPort() { return port; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Required only - uses defaults
        Object db1 = helper.invoke("example.DatabaseDefaults", "create", "mydb");
        assertThat(helper.invokeOn(db1, "getName")).isEqualTo("mydb");
        assertThat(helper.invokeOn(db1, "getHost")).isEqualTo("localhost");
        assertThat(helper.invokeOn(db1, "getPort")).isEqualTo(5432);

        // Required + host
        Object db2 = helper.invoke("example.DatabaseDefaults", "create", "mydb", "prod.example.com");
        assertThat(helper.invokeOn(db2, "getName")).isEqualTo("mydb");
        assertThat(helper.invokeOn(db2, "getHost")).isEqualTo("prod.example.com");
        assertThat(helper.invokeOn(db2, "getPort")).isEqualTo(5432);

        // All params - use original constructor
        Object db3 = helper.newInstance("example.Database",
                new Class<?>[]{String.class, String.class, int.class},
                "mydb", "prod.example.com", 5433);
        assertThat(helper.invokeOn(db3, "getPort")).isEqualTo(5433);
    }
}
