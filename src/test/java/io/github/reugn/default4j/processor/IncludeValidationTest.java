package io.github.reugn.default4j.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static io.github.reugn.default4j.util.CompileHelper.compile;

/**
 * Tests for @IncludeDefaults annotation validations.
 * <p>
 * Covers:
 * <ul>
 *   <li>Public constructor requirement</li>
 *   <li>Interface/abstract class restrictions</li>
 *   <li>Non-consecutive defaults for included types</li>
 * </ul>
 */
@DisplayName("@IncludeDefaults Validation")
class IncludeValidationTest {

    @Nested
    @DisplayName("Public Constructor Requirement")
    class PublicConstructor {

        @Test
        @DisplayName("Error when included class has no public constructor")
        void noPublicConstructor() {
            JavaFileObject privateConfig = JavaFileObjects.forSourceString("test.PrivateConfig",
                    """
                            package test;
                            
                            public class PrivateConfig {
                                private final String host;
                            
                                private PrivateConfig(String host) {
                                    this.host = host;
                                }
                            
                                public static PrivateConfig create(String host) {
                                    return new PrivateConfig(host);
                                }
                            }
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(PrivateConfig.class)
                            public class Defaults {
                                public static final String DEFAULT_HOST = "localhost";
                            }
                            """);

            Compilation compilation = compile(privateConfig, defaults);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("has no public constructor");
        }

        @Test
        @DisplayName("OK when included class has public constructor")
        void hasPublicConstructor() {
            JavaFileObject publicConfig = JavaFileObjects.forSourceString("test.PublicConfig",
                    """
                            package test;
                            
                            public class PublicConfig {
                                private final String host;
                            
                                public PublicConfig(String host) {
                                    this.host = host;
                                }
                            }
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(PublicConfig.class)
                            public class Defaults {
                                public static final String DEFAULT_HOST = "localhost";
                            }
                            """);

            Compilation compilation = compile(publicConfig, defaults);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Interface/Abstract Restrictions")
    class InterfaceAbstractRestrictions {

        @Test
        @DisplayName("Error when including interface")
        void includeInterface() {
            JavaFileObject myInterface = JavaFileObjects.forSourceString("test.MyInterface",
                    """
                            package test;
                            
                            public interface MyInterface {
                                String getValue();
                            }
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(MyInterface.class)
                            public class Defaults {
                            }
                            """);

            Compilation compilation = compile(myInterface, defaults);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Cannot include interface");
        }

        @Test
        @DisplayName("Error when including abstract class")
        void includeAbstractClass() {
            JavaFileObject abstractClass = JavaFileObjects.forSourceString("test.AbstractConfig",
                    """
                            package test;
                            
                            public abstract class AbstractConfig {
                                public abstract String getValue();
                            }
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(AbstractConfig.class)
                            public class Defaults {
                            }
                            """);

            Compilation compilation = compile(abstractClass, defaults);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Cannot include abstract class");
        }
    }

    @Nested
    @DisplayName("Non-Consecutive Defaults")
    class NonConsecutiveDefaults {

        @Test
        @DisplayName("Error in @IncludeDefaults with non-consecutive defaults")
        void nonConsecutive() {
            JavaFileObject externalRecord = JavaFileObjects.forSourceString("test.DatabaseConfig",
                    """
                            package test;
                            
                            public record DatabaseConfig(String host, int port, String database) {}
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(DatabaseConfig.class)
                            public class Defaults {
                                // Only port has default - database after it is required (ERROR!)
                                public static final int DEFAULT_PORT = 5432;
                            }
                            """);

            Compilation compilation = compile(externalRecord, defaults);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Non-consecutive defaults");
            assertThat(compilation).hadErrorContaining("database");
        }

        @Test
        @DisplayName("OK in @IncludeDefaults with named mode")
        void namedModeAllowsNonConsecutive() {
            JavaFileObject externalRecord = JavaFileObjects.forSourceString("test.DatabaseConfig",
                    """
                            package test;
                            
                            public record DatabaseConfig(String host, int port, String database) {}
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(value = DatabaseConfig.class, named = true)
                            public class Defaults {
                                public static final int DEFAULT_PORT = 5432;
                            }
                            """);

            Compilation compilation = compile(externalRecord, defaults);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Constructor Selection")
    class ConstructorSelection {

        @Test
        @DisplayName("Selects constructor that best matches defined defaults")
        void selectsBestMatchingConstructor() {
            // Class with two constructors: (int port) and (String host, int port)
            JavaFileObject multiConstructor = JavaFileObjects.forSourceString("test.ServerConfig",
                    """
                            package test;
                            
                            public class ServerConfig {
                                private final String host;
                                private final int port;
                            
                                public ServerConfig(int port) {
                                    this("localhost", port);
                                }
                            
                                public ServerConfig(String host, int port) {
                                    this.host = host;
                                    this.port = port;
                                }
                            
                                public String getHost() { return host; }
                                public int getPort() { return port; }
                            }
                            """);

            // Define defaults for both host and port
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(ServerConfig.class)
                            public class Defaults {
                                public static final String DEFAULT_HOST = "localhost";
                                public static final int DEFAULT_PORT = 8080;
                            }
                            """);

            Compilation compilation = compile(multiConstructor, defaults);
            assertThat(compilation).succeeded();

            // Verify it generated factory methods for the (String, int) constructor
            assertThat(compilation)
                    .generatedSourceFile("test.ServerConfigDefaults")
                    .contentsAsUtf8String()
                    .contains("create(String host, int port)");
            assertThat(compilation)
                    .generatedSourceFile("test.ServerConfigDefaults")
                    .contentsAsUtf8String()
                    .contains("create(String host)");
            assertThat(compilation)
                    .generatedSourceFile("test.ServerConfigDefaults")
                    .contentsAsUtf8String()
                    .contains("create()");
        }

        @Test
        @DisplayName("Selects constructor with more parameters on score tie")
        void prefersMoreParametersOnTie() {
            // Class with constructors: (int x) and (int x, int y)
            // Both have x, so if we define defaults for both x and y, both score 2 vs 1
            // The (x, y) constructor should win on parameter count
            JavaFileObject multiConstructor = JavaFileObjects.forSourceString("test.Point",
                    """
                            package test;
                            
                            public class Point {
                                private final int x;
                                private final int y;
                            
                                public Point(int x) {
                                    this(x, 0);
                                }
                            
                                public Point(int x, int y) {
                                    this.x = x;
                                    this.y = y;
                                }
                            }
                            """);

            // Define defaults for both - (int x) scores 1, (int x, int y) scores 2
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(Point.class)
                            public class Defaults {
                                public static final int DEFAULT_X = 0;
                                public static final int DEFAULT_Y = 0;
                            }
                            """);

            Compilation compilation = compile(multiConstructor, defaults);
            assertThat(compilation).succeeded();

            // Should select (int x, int y) constructor (higher score)
            assertThat(compilation)
                    .generatedSourceFile("test.PointDefaults")
                    .contentsAsUtf8String()
                    .contains("create(int x, int y)");
        }

        @Test
        @DisplayName("Warns about unused defaults that don't match any constructor parameter")
        void warnsAboutUnusedDefaults() {
            JavaFileObject simpleClass = JavaFileObjects.forSourceString("test.SimpleConfig",
                    """
                            package test;
                            
                            public class SimpleConfig {
                                private final int port;
                            
                                public SimpleConfig(int port) {
                                    this.port = port;
                                }
                            }
                            """);

            // Define a default that doesn't match any parameter (hostname doesn't exist)
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(SimpleConfig.class)
                            public class Defaults {
                                public static final String DEFAULT_HOSTNAME = "localhost";
                                public static final int DEFAULT_PORT = 8080;
                            }
                            """);

            Compilation compilation = compile(simpleClass, defaults);
            assertThat(compilation).succeeded();
            assertThat(compilation).hadWarningContaining("DEFAULT_HOSTNAME");
            assertThat(compilation).hadWarningContaining("does not match any parameter");
        }

        @Test
        @DisplayName("No warning when all defaults match constructor parameters")
        void noWarningWhenAllDefaultsMatch() {
            JavaFileObject simpleClass = JavaFileObjects.forSourceString("test.Config",
                    """
                            package test;
                            
                            public class Config {
                                private final String host;
                                private final int port;
                            
                                public Config(String host, int port) {
                                    this.host = host;
                                    this.port = port;
                                }
                            }
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(Config.class)
                            public class Defaults {
                                public static final String DEFAULT_HOST = "localhost";
                                public static final int DEFAULT_PORT = 8080;
                            }
                            """);

            Compilation compilation = compile(simpleClass, defaults);
            assertThat(compilation).succeeded();
            // Verify generated code exists (confirms no processing errors)
            assertThat(compilation)
                    .generatedSourceFile("test.ConfigDefaults")
                    .contentsAsUtf8String()
                    .contains("create()");
        }

        @Test
        @DisplayName("Works correctly with single constructor")
        void worksWithSingleConstructor() {
            JavaFileObject singleConstructor = JavaFileObjects.forSourceString("test.SingleConfig",
                    """
                            package test;
                            
                            public class SingleConfig {
                                private final String name;
                                private final int value;
                            
                                public SingleConfig(String name, int value) {
                                    this.name = name;
                                    this.value = value;
                                }
                            }
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(SingleConfig.class)
                            public class Defaults {
                                public static final String DEFAULT_NAME = "default";
                                public static final int DEFAULT_VALUE = 42;
                            }
                            """);

            Compilation compilation = compile(singleConstructor, defaults);
            assertThat(compilation).succeeded();
            assertThat(compilation)
                    .generatedSourceFile("test.SingleConfigDefaults")
                    .contentsAsUtf8String()
                    .contains("create()");
        }

        @Test
        @DisplayName("Selects constructor matching most defined defaults - network-style scenario")
        void selectsConstructorMatchingMostDefaults() {
            // Simulates a scenario like InetSocketAddress with multiple constructors:
            // - (int port)
            // - (String hostname, int port)
            // When user defines both DEFAULT_HOSTNAME and DEFAULT_PORT, 
            // the (String, int) constructor should be selected
            JavaFileObject networkAddress = JavaFileObjects.forSourceString("test.NetworkAddress",
                    """
                            package test;
                            
                            public class NetworkAddress {
                                private final String hostname;
                                private final int port;
                            
                                public NetworkAddress(int port) {
                                    this("0.0.0.0", port);
                                }
                            
                                public NetworkAddress(String hostname, int port) {
                                    this.hostname = hostname;
                                    this.port = port;
                                }
                            
                                public String getHostname() { return hostname; }
                                public int getPort() { return port; }
                            }
                            """);

            // Define defaults for both hostname and port - should pick (String, int) constructor
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.NetworkDefaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(NetworkAddress.class)
                            public class NetworkDefaults {
                                public static final String DEFAULT_HOSTNAME = "localhost";
                                public static final int DEFAULT_PORT = 8080;
                            }
                            """);

            Compilation compilation = compile(networkAddress, defaults);
            assertThat(compilation).succeeded();

            // Verify the (String, int) constructor was selected
            assertThat(compilation)
                    .generatedSourceFile("test.NetworkAddressDefaults")
                    .contentsAsUtf8String()
                    .contains("create(String hostname, int port)");
            assertThat(compilation)
                    .generatedSourceFile("test.NetworkAddressDefaults")
                    .contentsAsUtf8String()
                    .contains("create(String hostname)");  // port has default
            assertThat(compilation)
                    .generatedSourceFile("test.NetworkAddressDefaults")
                    .contentsAsUtf8String()
                    .contains("create()");  // both have defaults

            // Should NOT just have create(int port) from the simple constructor
            assertThat(compilation)
                    .generatedSourceFile("test.NetworkAddressDefaults")
                    .contentsAsUtf8String()
                    .doesNotContain("create(int port)");
        }

        @Test
        @DisplayName("Falls back to simpler constructor when no defaults match complex constructor")
        void fallsBackToSimplerConstructorWhenNoMatch() {
            JavaFileObject networkAddress = JavaFileObjects.forSourceString("test.NetworkAddress2",
                    """
                            package test;
                            
                            public class NetworkAddress2 {
                                private final String hostname;
                                private final int port;
                            
                                public NetworkAddress2(int port) {
                                    this("0.0.0.0", port);
                                }
                            
                                public NetworkAddress2(String hostname, int port) {
                                    this.hostname = hostname;
                                    this.port = port;
                                }
                            }
                            """);

            // Only define DEFAULT_PORT - both constructors score 1, 
            // but (String, int) has more params so it wins
            // Then user gets a warning about hostname not having a default
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.NetworkDefaults2",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @IncludeDefaults(value = NetworkAddress2.class, named = true)
                            public class NetworkDefaults2 {
                                public static final int DEFAULT_PORT = 8080;
                            }
                            """);

            Compilation compilation = compile(networkAddress, defaults);
            assertThat(compilation).succeeded();

            // In named mode, non-consecutive defaults are OK
            assertThat(compilation)
                    .generatedSourceFile("test.NetworkAddress2Defaults")
                    .contentsAsUtf8String()
                    .contains("NetworkAddress2Builder");
        }
    }
}
