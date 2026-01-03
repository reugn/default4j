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
 * Tests for structural validations.
 * <p>
 * Covers:
 * <ul>
 *   <li>Consecutive defaults requirement</li>
 *   <li>Private element visibility</li>
 *   <li>Builder name conflicts</li>
 *   <li>Duplicate annotation detection</li>
 *   <li>Unsupported element types</li>
 * </ul>
 */
@DisplayName("Structural Validations")
class StructuralValidationTest {

    @Nested
    @DisplayName("Consecutive Defaults")
    class ConsecutiveDefaults {

        @Test
        @DisplayName("Error when non-consecutive defaults in method")
        void nonConsecutiveDefaultsMethod() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.BadOrder",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class BadOrder {
                                @WithDefaults
                                public String bad(@DefaultValue("first") String a, String b) {
                                    return a + b;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining(
                    "without @DefaultValue/@DefaultFactory found after parameter with default");
        }

        @Test
        @DisplayName("Error when non-consecutive defaults in constructor")
        void nonConsecutiveDefaultsConstructor() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.BadConstructor",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class BadConstructor {
                                @WithDefaults
                                public BadConstructor(@DefaultValue("first") String a, String b) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining(
                    "without @DefaultValue/@DefaultFactory found after parameter with default");
        }

        @Test
        @DisplayName("OK when all trailing parameters have defaults")
        void consecutiveDefaultsFromEnd() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.GoodOrder",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class GoodOrder {
                                @WithDefaults
                                public String good(String required, @DefaultValue("a") String opt1, @DefaultValue("b") String opt2) {
                                    return required + opt1 + opt2;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("OK with named mode - non-consecutive defaults allowed")
        void namedModeAllowsNonConsecutive() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.NamedMode",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class NamedMode {
                                @WithDefaults(named = true)
                                public String method(@DefaultValue("first") String a, String b, @DefaultValue("third") String c) {
                                    return a + b + c;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Private Element Visibility")
    class PrivateVisibility {

        @Test
        @DisplayName("Error when @WithDefaults on private method")
        void privateMethod() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                private void process(@DefaultValue("test") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("is private");
        }

        @Test
        @DisplayName("Error when @WithDefaults on private constructor")
        void privateConstructor() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                private Service(@DefaultValue("test") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("is private");
        }

        @Test
        @DisplayName("OK when method is package-private")
        void packagePrivateMethod() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                void process(@DefaultValue("test") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("OK when method is protected")
        void protectedMethod() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                protected void process(@DefaultValue("test") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Builder Name Conflicts")
    class BuilderNameConflicts {

        @Test
        @DisplayName("Error when method named builder conflicts with constructor builder")
        void methodConflictsWithConstructor() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Person",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Person {
                                private final String name;
                            
                                @WithDefaults(named = true)
                                public Person(@DefaultValue("Sam") String name) {
                                    this.name = name;
                                }
                            
                                @WithDefaults(named = true)
                                public void person(@DefaultValue("test") String value) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Builder name conflict");
            assertThat(compilation).hadErrorContaining("PersonBuilder");
        }

        @Test
        @DisplayName("OK when only constructor uses named mode")
        void noConflictWhenMethodNotNamed() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Person",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Person {
                                private final String name;
                            
                                @WithDefaults(named = true)
                                public Person(@DefaultValue("Sam") String name) {
                                    this.name = name;
                                }
                            
                                @WithDefaults
                                public void person(@DefaultValue("test") String value) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Duplicate Annotations")
    class DuplicateAnnotations {

        @Test
        @DisplayName("Error when both @DefaultValue and @DefaultFactory on method parameter")
        void duplicateOnMethodParam() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static String create() { return "value"; }
                            
                                @WithDefaults
                                public void process(@DefaultValue("test") @DefaultFactory("create") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining(
                    "cannot have both @DefaultValue and @DefaultFactory");
        }

        @Test
        @DisplayName("Error when both @DefaultValue and @DefaultFactory on constructor parameter")
        void duplicateOnConstructorParam() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static String create() { return "value"; }
                            
                                @WithDefaults
                                public Service(@DefaultValue("test") @DefaultFactory("create") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining(
                    "cannot have both @DefaultValue and @DefaultFactory");
        }
    }

    @Nested
    @DisplayName("Unsupported Element Types")
    class UnsupportedElementTypes {

        @Test
        @DisplayName("Error when @WithDefaults on interface")
        void interfaceNotSupported() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.MyInterface",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults
                            public interface MyInterface {
                                void doSomething();
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining(
                    "can only be applied to methods, constructors, classes, or records");
        }

        @Test
        @DisplayName("Error when @WithDefaults on enum")
        void enumNotSupported() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Status",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults
                            public enum Status {
                                ACTIVE, INACTIVE
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining(
                    "can only be applied to methods, constructors, classes, or records");
        }

        @Test
        @DisplayName("OK when @WithDefaults on default method in interface")
        void defaultMethodInInterface() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.MyInterface",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public interface MyInterface {
                                @WithDefaults
                                default String greet(@DefaultValue("World") String name) {
                                    return "Hello, " + name;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }
}
