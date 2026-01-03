package io.github.reugn.default4j.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static io.github.reugn.default4j.util.CompileHelper.compile;

/**
 * Tests for supported types in default value annotations.
 * <p>
 * For validation tests, see {@link ValidationTest}.
 */
@DisplayName("Supported Types")
class SupportedTypesTest {

    @Test
    @DisplayName("All primitive types")
    void allPrimitiveTypes() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.PrimitiveTest",
                """
                        package test;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class PrimitiveTest {
                            @WithDefaults
                            public String testPrimitives(
                                    @DefaultValue("42") int intVal,
                                    @DefaultValue("100") long longVal,
                                    @DefaultValue("3.14") double doubleVal,
                                    @DefaultValue("2.5") float floatVal,
                                    @DefaultValue("true") boolean boolVal,
                                    @DefaultValue("10") byte byteVal,
                                    @DefaultValue("100") short shortVal,
                                    @DefaultValue("x") char charVal) {
                                return "ok";
                            }
                        }
                        """);

        Compilation compilation = compile(source);
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.PrimitiveTestDefaults");
    }

    @Test
    @DisplayName("Null default value")
    void nullDefaultValue() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.NullTest",
                """
                        package test;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class NullTest {
                            @WithDefaults
                            public String process(String required, @DefaultValue("null") String optional) {
                                return required + (optional != null ? optional : "");
                            }
                        }
                        """);

        Compilation compilation = compile(source);
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.NullTestDefaults");
    }

    @Test
    @DisplayName("Special characters in string")
    void specialCharactersInString() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.SpecialChars",
                """
                        package test;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class SpecialChars {
                            @WithDefaults
                            public String format(@DefaultValue("Hello\\nWorld") String value) {
                                return value;
                            }
                        }
                        """);

        Compilation compilation = compile(source);
        assertThat(compilation).succeeded();
    }
}
