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
 * Tests for @DefaultFactory annotation validations.
 * <p>
 * Covers:
 * <ul>
 *   <li>Factory method existence</li>
 *   <li>Static modifier requirement</li>
 *   <li>No parameters requirement</li>
 *   <li>Non-void return type</li>
 *   <li>Return type compatibility</li>
 *   <li>Empty factory reference</li>
 * </ul>
 */
@DisplayName("@DefaultFactory Validation")
class DefaultFactoryValidationTest {

    @Nested
    @DisplayName("Method Existence")
    class MethodExistence {

        @Test
        @DisplayName("Error when factory method not found")
        void methodNotFound() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultFactory("nonExistentMethod") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Factory method 'nonExistentMethod' not found");
        }

        @Test
        @DisplayName("Error with factory typo suggests similar method")
        void factoryTypoSuggestion() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static String defaultName() { return "test"; }
                            
                                @WithDefaults
                                public void process(@DefaultFactory("defualtName") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Did you mean 'defaultName()'?");
        }

        @Test
        @DisplayName("Error lists available static methods")
        void factoryListsAvailable() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static String createHost() { return "localhost"; }
                                static int createPort() { return 8080; }
                            
                                @WithDefaults
                                public void process(@DefaultFactory("completelyDifferent") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Available static no-arg methods:");
        }
    }

    @Nested
    @DisplayName("Static Modifier")
    class StaticModifier {

        @Test
        @DisplayName("Error when factory method is not static")
        void methodNotStatic() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                String createName() {
                                    return "default";
                                }
                            
                                @WithDefaults
                                public void process(@DefaultFactory("createName") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("must be static");
        }
    }

    @Nested
    @DisplayName("No Parameters")
    class NoParameters {

        @Test
        @DisplayName("Error when factory method has parameters")
        void methodHasParameters() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static String createName(String prefix) {
                                    return prefix + "default";
                                }
                            
                                @WithDefaults
                                public void process(@DefaultFactory("createName") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("must have no parameters");
        }
    }

    @Nested
    @DisplayName("Return Type")
    class ReturnType {

        @Test
        @DisplayName("Error when factory method returns void")
        void voidReturn() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static void createNothing() {
                                }
                            
                                @WithDefaults
                                public void process(@DefaultFactory("createNothing") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("cannot return void");
        }

        @Test
        @DisplayName("Error when return type is incompatible")
        void incompatibleReturnType() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static int createNumber() {
                                    return 42;
                                }
                            
                                @WithDefaults
                                public void process(@DefaultFactory("createNumber") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("is not assignable to");
        }

        @Test
        @DisplayName("OK when factory method is valid")
        void validFactoryMethod() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static String createName() {
                                    return "default";
                                }
                            
                                @WithDefaults
                                public void process(@DefaultFactory("createName") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("External Class Reference")
    class ExternalClassReference {

        @Test
        @DisplayName("Error with typo in external class method suggests similar method")
        void externalClassTypoSuggestion() {
            JavaFileObject factory = JavaFileObjects.forSourceString("test.Factory",
                    """
                            package test;
                            
                            public class Factory {
                                public static long time() { return System.currentTimeMillis(); }
                            }
                            """);

            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultFactory("Factory.tima") long timestamp) {
                                }
                            }
                            """);

            Compilation compilation = compile(factory, source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Factory method 'tima' not found");
            assertThat(compilation).hadErrorContaining("Did you mean 'time()'?");
        }

        @Test
        @DisplayName("OK when external class method is valid")
        void externalClassMethodValid() {
            JavaFileObject factory = JavaFileObjects.forSourceString("test.Factory",
                    """
                            package test;
                            
                            public class Factory {
                                public static long time() { return System.currentTimeMillis(); }
                            }
                            """);

            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultFactory("Factory.time") long timestamp) {
                                }
                            }
                            """);

            Compilation compilation = compile(factory, source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Error when external class method is not static")
        void externalClassMethodNotStatic() {
            JavaFileObject factory = JavaFileObjects.forSourceString("test.Factory",
                    """
                            package test;
                            
                            public class Factory {
                                public long time() { return System.currentTimeMillis(); }
                            }
                            """);

            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultFactory("Factory.time") long timestamp) {
                                }
                            }
                            """);

            Compilation compilation = compile(factory, source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("must be static");
        }

        @Test
        @DisplayName("Error when class cannot be resolved suggests fully qualified name")
        void unresolvedClassSuggestsFullyQualified() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.util.UUID;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultFactory("UUID.randomUUID") UUID id) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Class 'UUID' not found");
            assertThat(compilation).hadErrorContaining("fully qualified class name");
        }

        @Test
        @DisplayName("OK when using fully qualified class name for external class")
        void fullyQualifiedExternalClassWorks() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.util.UUID;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultFactory("java.util.UUID.randomUUID") UUID id) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Empty Reference")
    class EmptyReference {

        @Test
        @DisplayName("Error when @DefaultFactory value is empty")
        void emptyFactoryValue() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultFactory("") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("non-empty method reference");
        }

        @Test
        @DisplayName("Error when method name is empty after dot")
        void emptyMethodNameAfterDot() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultFactory("SomeClass.") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("empty method name");
        }
    }
}
