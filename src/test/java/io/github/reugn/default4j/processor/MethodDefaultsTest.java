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
 * Tests for @WithDefaults on methods.
 */
@DisplayName("Method Defaults")
class MethodDefaultsTest {

    @Nested
    @DisplayName("Instance Methods")
    class InstanceMethods {

        @Test
        @DisplayName("Simple single default parameter")
        void simpleDefaults() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Greeter",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Greeter {
                                @WithDefaults
                                public String greet(@DefaultValue("World") String name) {
                                    return "Hello, " + name + "!";
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.GreeterDefaults");
        }

        @Test
        @DisplayName("Multiple default parameters")
        void multipleDefaults() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Calculator",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Calculator {
                                @WithDefaults
                                public int add(int a, @DefaultValue("0") int b, @DefaultValue("0") int c) {
                                    return a + b + c;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.CalculatorDefaults");
        }

        @Test
        @DisplayName("Mixed parameter types")
        void mixedParameterTypes() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Formatter",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Formatter {
                                @WithDefaults
                                public String format(
                                        String template,
                                        @DefaultValue("default") String value,
                                        @DefaultValue("3") int repeat,
                                        @DefaultValue("true") boolean uppercase) {
                                    return template + value + repeat + uppercase;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.FormatterDefaults");
        }

        @Test
        @DisplayName("Multiple methods in same class")
        void multipleMethods() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.MultiMethod",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class MultiMethod {
                                @WithDefaults
                                public String methodOne(@DefaultValue("one") String value) {
                                    return value;
                                }
                            
                                @WithDefaults
                                public String methodTwo(@DefaultValue("two") String value, @DefaultValue("2") int count) {
                                    return value.repeat(count);
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.MultiMethodDefaults");
        }

        @Test
        @DisplayName("Void return type")
        void voidReturnType() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.VoidReturn",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class VoidReturn {
                                private String lastValue;
                            
                                @WithDefaults
                                public void setValue(@DefaultValue("default") String value) {
                                    this.lastValue = value;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.VoidReturnDefaults");
        }
    }

    @Nested
    @DisplayName("Static Methods")
    class StaticMethods {

        @Test
        @DisplayName("Simple static method")
        void simpleStaticMethod() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.StringUtils",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class StringUtils {
                                @WithDefaults
                                public static String pad(String text, @DefaultValue("20") int width) {
                                    return String.format("%-" + width + "s", text);
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.StringUtilsDefaults");
        }

        @Test
        @DisplayName("Static method with multiple defaults")
        void staticMethodMultipleDefaults() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.MathUtils",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class MathUtils {
                                @WithDefaults
                                public static double calculate(
                                        double value,
                                        @DefaultValue("1.0") double multiplier,
                                        @DefaultValue("0.0") double offset) {
                                    return value * multiplier + offset;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.MathUtilsDefaults");
        }

        @Test
        @DisplayName("Static method with named mode")
        void staticMethodNamed() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.FileUtils",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class FileUtils {
                                @WithDefaults(named = true)
                                public static String readFile(
                                        String path,
                                        @DefaultValue("UTF-8") String charset,
                                        @DefaultValue("4096") int bufferSize) {
                                    return "content";
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.FileUtilsDefaults");
        }
    }

    @Nested
    @DisplayName("Mixed Modes")
    class MixedModes {

        @Test
        @DisplayName("Static and instance methods in same class")
        void staticAndInstanceMethods() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.MixedMethods",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class MixedMethods {
                                @WithDefaults
                                public static String staticMethod(@DefaultValue("static") String value) {
                                    return value;
                                }
                            
                                @WithDefaults
                                public String instanceMethod(@DefaultValue("instance") String value) {
                                    return value;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.MixedMethodsDefaults");
        }

        @Test
        @DisplayName("Default and named modes in same class")
        void defaultAndNamed() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.MixedModes",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class MixedModes {
                                @WithDefaults
                                public String overloadedMethod(@DefaultValue("default") String value) {
                                    return value;
                                }
                            
                                @WithDefaults(named = true)
                                public String namedMethod(@DefaultValue("named") String value) {
                                    return value;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.MixedModesDefaults");
        }
    }
}
