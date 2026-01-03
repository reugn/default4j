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
 * Tests for @DefaultValue annotation validations.
 * <p>
 * Covers:
 * <ul>
 *   <li>Mutual exclusivity of value and field attributes</li>
 *   <li>Literal value parsing for various types</li>
 *   <li>Field reference validation</li>
 *   <li>Empty value validation</li>
 *   <li>Field type compatibility</li>
 * </ul>
 */
@DisplayName("@DefaultValue Validation")
class DefaultValueValidationTest {

    @Nested
    @DisplayName("Value/Field Mutual Exclusivity")
    class MutualExclusivity {

        @Test
        @DisplayName("Error when both value and field are specified")
        void bothValueAndField() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static final String CONSTANT = "constant";
                            
                                @WithDefaults
                                public void process(@DefaultValue(value = "literal", field = "CONSTANT") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("cannot specify both 'value' and 'field'");
        }
    }

    @Nested
    @DisplayName("Literal Parsing")
    class LiteralParsing {

        @Test
        @DisplayName("Error when int value is not parseable")
        void invalidIntValue() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue("abc") int count) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("'abc' is not a valid int");
        }

        @Test
        @DisplayName("Error when long value is not parseable")
        void invalidLongValue() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue("not_a_long") long count) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("is not a valid long");
        }

        @Test
        @DisplayName("Error when boolean value is not true/false")
        void invalidBooleanValue() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue("yes") boolean enabled) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("is not a valid boolean");
        }

        @Test
        @DisplayName("Error when null is used for primitive type")
        void nullForPrimitive() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue("null") int count) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("'null' is not a valid default for primitive type");
        }

        @Test
        @DisplayName("OK when null is used for reference type")
        void nullForReferenceType() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults(named = true)
                                public void process(String required, @DefaultValue("null") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("OK with valid numeric values")
        void validNumericValues() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(
                                        @DefaultValue("42") int count,
                                        @DefaultValue("100L") long bigNumber,
                                        @DefaultValue("3.14") double pi,
                                        @DefaultValue("2.5F") float ratio) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Field Reference")
    class FieldReference {

        @Test
        @DisplayName("Error when external class field reference has empty field name after dot")
        void externalFieldEmptyName() {
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            public class Defaults {
                                public static final String NAME = "test";
                            }
                            """);

            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue(field = "Defaults.") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(defaults, source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("has empty field name");
        }

        @Test
        @DisplayName("Error when field does not exist")
        void fieldNotFound() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue(field = "NON_EXISTENT") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Field 'NON_EXISTENT' not found");
        }

        @Test
        @DisplayName("Error with typo suggests similar field")
        void fieldTypoSuggestion() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static final String DEFAULT_NAME = "test";
                            
                                @WithDefaults
                                public void process(@DefaultValue(field = "DEFUALT_NAME") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Did you mean 'DEFAULT_NAME'?");
        }

        @Test
        @DisplayName("Error lists available static fields")
        void fieldListsAvailable() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static final String HOST = "localhost";
                                static final int PORT = 8080;
                            
                                @WithDefaults
                                public void process(@DefaultValue(field = "COMPLETELY_DIFFERENT") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Available static fields:");
        }

        @Test
        @DisplayName("Error when field is not static")
        void fieldNotStatic() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                final String instanceField = "instance";
                            
                                @WithDefaults
                                public void process(@DefaultValue(field = "instanceField") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("must be static");
        }

        @Test
        @DisplayName("Error when external class field not found with typo suggestion")
        void externalFieldTypoSuggestion() {
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            public class Defaults {
                                public static final String DEFAULT_NAME = "test";
                                public static final int DEFAULT_PORT = 8080;
                            }
                            """);

            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue(field = "Defaults.DEFUALT_NAME") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(defaults, source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Did you mean 'DEFAULT_NAME'?");
        }

        @Test
        @DisplayName("Error when external class field not found lists available fields")
        void externalFieldListsAvailable() {
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            public class Defaults {
                                public static final String NAME = "test";
                                public static final int PORT = 8080;
                            }
                            """);

            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue(field = "Defaults.COMPLETELY_DIFFERENT") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(defaults, source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Available static fields:");
        }

        @Test
        @DisplayName("Error when external class field is not static")
        void externalFieldNotStatic() {
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            public class Defaults {
                                public final String name = "test";
                            }
                            """);

            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue(field = "Defaults.name") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(defaults, source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("must be static");
        }

        @Test
        @DisplayName("Error when external class field has incompatible type")
        void externalFieldTypeMismatch() {
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            public class Defaults {
                                public static final int DEFAULT_VALUE = 42;
                            }
                            """);

            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue(field = "Defaults.DEFAULT_VALUE") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(defaults, source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("is not assignable to");
        }

        @Test
        @DisplayName("OK when external class field is valid")
        void externalFieldValid() {
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            public class Defaults {
                                public static final String DEFAULT_NAME = "test";
                            }
                            """);

            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue(field = "Defaults.DEFAULT_NAME") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(defaults, source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Error when class cannot be resolved suggests fully qualified name")
        void unresolvedClassSuggestsFullyQualified() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.util.concurrent.TimeUnit;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue(field = "TimeUnit.SECONDS") TimeUnit unit) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Class 'TimeUnit' not found");
            assertThat(compilation).hadErrorContaining("fully qualified class name");
        }

        @Test
        @DisplayName("OK when using fully qualified class name for external class field")
        void fullyQualifiedExternalClassWorks() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue(field = "java.lang.Integer.MAX_VALUE") int maxSize) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Empty Value")
    class EmptyValue {

        @Test
        @DisplayName("Error when @DefaultValue(\"\") on int")
        void emptyValueOnInt() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue("") int count) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("empty value is only valid for String type");
        }

        @Test
        @DisplayName("Error when @DefaultValue(\"\") on boolean")
        void emptyValueOnBoolean() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults
                                public void process(@DefaultValue("") boolean enabled) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("empty value is only valid for String type");
        }

        @Test
        @DisplayName("OK when @DefaultValue(\"\") on String")
        void emptyValueOnString() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                @WithDefaults(named = true)
                                public void process(String name, @DefaultValue("") String suffix) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Error when @DefaultValue(\"\") on record int component")
        void emptyValueOnRecordIntComponent() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Config",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults
                            public record Config(String name, @DefaultValue("") int port) {}
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("empty value is only valid for String type");
        }
    }

    @Nested
    @DisplayName("Field Type Compatibility")
    class FieldTypeCompatibility {

        @Test
        @DisplayName("OK when field type is compatible")
        void fieldTypeCompatible() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static final String DEFAULT_NAME = "test";
                            
                                @WithDefaults
                                public void process(@DefaultValue(field = "DEFAULT_NAME") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }
}
