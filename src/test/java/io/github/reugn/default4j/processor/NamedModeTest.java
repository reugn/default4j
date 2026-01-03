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
 * Tests for @WithDefaults(named = true) - builder-style named parameters.
 */
@DisplayName("Named Mode (named = true)")
class NamedModeTest {

    @Nested
    @DisplayName("Methods")
    class Methods {

        @Test
        @DisplayName("Simple named method")
        void simpleNamedMethod() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Database",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Database {
                                @WithDefaults(named = true)
                                public String connect(
                                        @DefaultValue("localhost") String host,
                                        @DefaultValue("5432") int port,
                                        @DefaultValue("postgres") String user,
                                        @DefaultValue("") String password) {
                                    return host + ":" + port + " as " + user;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.DatabaseDefaults");
        }

        @Test
        @DisplayName("Named method with required fields")
        void namedMethodWithRequiredFields() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.HttpClient",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class HttpClient {
                                @WithDefaults(named = true)
                                public String request(
                                        String url,
                                        @DefaultValue("GET") String method,
                                        @DefaultValue("30") int timeout) {
                                    return method + " " + url + " timeout=" + timeout;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.HttpClientDefaults");
        }

        @Test
        @DisplayName("Multiple named methods in same class")
        void multipleNamedMethods() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults(named = true)
                                public void configure(@DefaultValue("default") String value) {}
                            
                                @WithDefaults(named = true)
                                public String fetch(@DefaultValue("http://localhost") String url) {
                                    return url;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ServiceDefaults");
        }

        @Test
        @DisplayName("Named method with return value")
        void namedMethodWithReturnValue() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Calculator",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Calculator {
                                @WithDefaults(named = true)
                                public double calculate(
                                        double value,
                                        @DefaultValue("1.0") double multiplier,
                                        @DefaultValue("0.0") double offset) {
                                    return value * multiplier + offset;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.CalculatorDefaults");
        }

        @Test
        @DisplayName("Named method with all defaults")
        void namedMethodAllDefaults() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Logger",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Logger {
                                @WithDefaults(named = true)
                                public void log(
                                        @DefaultValue("INFO") String level,
                                        @DefaultValue("default message") String message,
                                        @DefaultValue("false") boolean includeTimestamp) {
                                    System.out.println(level + ": " + message);
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.LoggerDefaults");
        }
    }

    @Nested
    @DisplayName("Constructors")
    class Constructors {

        @Test
        @DisplayName("Named constructor")
        void namedConstructor() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.NamedUser",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class NamedUser {
                                @WithDefaults(named = true)
                                public NamedUser(
                                        String name,
                                        @DefaultValue("user@example.com") String email,
                                        @DefaultValue("USER") String role) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.NamedUserDefaults");
        }

        @Test
        @DisplayName("Named constructor with all optional parameters")
        void namedConstructorAllOptional() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.NamedConfig",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class NamedConfig {
                                @WithDefaults(named = true)
                                public NamedConfig(
                                        @DefaultValue("localhost") String host,
                                        @DefaultValue("8080") int port,
                                        @DefaultValue("false") boolean ssl) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.NamedConfigDefaults");
        }

        @Test
        @DisplayName("Named constructor can skip middle parameters")
        void namedConstructorSkipMiddle() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Connection",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Connection {
                                @WithDefaults(named = true)
                                public Connection(
                                        @DefaultValue("localhost") String host,
                                        @DefaultValue("5432") int port,
                                        @DefaultValue("postgres") String user,
                                        @DefaultValue("") String password) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ConnectionDefaults");
        }
    }
}
