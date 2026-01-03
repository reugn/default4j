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
 * Tests for @WithDefaults on constructors, classes, and records.
 */
@DisplayName("Constructor Defaults")
class ConstructorDefaultsTest {

    @Nested
    @DisplayName("Constructor-Level Annotation")
    class ConstructorLevel {

        @Test
        @DisplayName("Simple constructor defaults")
        void simpleConstructorDefaults() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.User",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class User {
                                private final String name;
                                private final String email;
                            
                                @WithDefaults
                                public User(
                                        String name,
                                        @DefaultValue("user@example.com") String email) {
                                    this.name = name;
                                    this.email = email;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.UserDefaults");
        }

        @Test
        @DisplayName("All optional parameters")
        void allOptionalParameters() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Config",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Config {
                                @WithDefaults
                                public Config(
                                        @DefaultValue("localhost") String host,
                                        @DefaultValue("8080") int port) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ConfigDefaults");
        }

        @Test
        @DisplayName("Custom factory method name")
        void customMethodName() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Product",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Product {
                                @WithDefaults(methodName = "newProduct")
                                public Product(
                                        String name,
                                        @DefaultValue("0.0") double price) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ProductDefaults");
        }

        @Test
        @DisplayName("Primitive parameter types")
        void primitiveTypes() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Settings",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Settings {
                                @WithDefaults
                                public Settings(
                                        @DefaultValue("30") int timeout,
                                        @DefaultValue("true") boolean enabled,
                                        @DefaultValue("1024") long maxSize) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.SettingsDefaults");
        }
    }

    @Nested
    @DisplayName("Class-Level Annotation")
    class ClassLevel {

        @Test
        @DisplayName("Simple class-level annotation")
        void simpleClassLevel() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.ClassLevel",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults
                            public class ClassLevel {
                                public ClassLevel(
                                        @DefaultValue("default") String name,
                                        @DefaultValue("100") int value) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ClassLevelDefaults");
        }

        @Test
        @DisplayName("Class-level with named mode")
        void classLevelNamed() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.ClassNamed",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults(named = true)
                            public class ClassNamed {
                                public ClassNamed(
                                        @DefaultValue("localhost") String host,
                                        @DefaultValue("8080") int port) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ClassNamedDefaults");
        }

        @Test
        @DisplayName("Class-level with custom method name")
        void classLevelCustomMethodName() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.ClassCustomMethod",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults(methodName = "of")
                            public class ClassCustomMethod {
                                public ClassCustomMethod(@DefaultValue("default") String value) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ClassCustomMethodDefaults");
        }
    }

    @Nested
    @DisplayName("Records")
    class Records {

        @Test
        @DisplayName("Record with all defaults")
        void recordWithDefaults() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Config",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults
                            public record Config(
                                    @DefaultValue("localhost") String host,
                                    @DefaultValue("8080") int port) {}
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ConfigDefaults");
        }

        @Test
        @DisplayName("Record with partial defaults")
        void recordWithPartialDefaults() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.User",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults
                            public record User(
                                    String name,
                                    @DefaultValue("user@example.com") String email,
                                    @DefaultValue("USER") String role) {}
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.UserDefaults");
        }

        @Test
        @DisplayName("Record with named mode")
        void recordNamedMode() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Settings",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults(named = true)
                            public record Settings(
                                    @DefaultValue("default") String name,
                                    @DefaultValue("100") int value,
                                    @DefaultValue("true") boolean enabled) {}
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.SettingsDefaults");
        }

        @Test
        @DisplayName("Record with custom method name")
        void recordCustomMethodName() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Point",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults(methodName = "of")
                            public record Point(
                                    @DefaultValue("0") int x,
                                    @DefaultValue("0") int y) {}
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.PointDefaults");
        }
    }

    @Nested
    @DisplayName("Unified Generation")
    class UnifiedGeneration {

        @Test
        @DisplayName("Method and constructor in same class")
        void methodAndConstructor() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                private final String name;
                            
                                @WithDefaults
                                public Service(@DefaultValue("default-service") String name) {
                                    this.name = name;
                                }
                            
                                @WithDefaults
                                public String greet(@DefaultValue("World") String target) {
                                    return "Hello, " + target + " from " + name;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ServiceDefaults");
        }
    }
}
