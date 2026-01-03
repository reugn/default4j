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
 * Additional compile-time validation tests.
 * <p>
 * For comprehensive validation tests, see also:
 * <ul>
 *   <li>{@link DefaultValueValidationTest} - @DefaultValue annotation tests</li>
 *   <li>{@link DefaultFactoryValidationTest} - @DefaultFactory annotation tests</li>
 *   <li>{@link StructuralValidationTest} - consecutive defaults, visibility, builder conflicts</li>
 *   <li>{@link IncludeValidationTest} - @IncludeDefaults annotation tests</li>
 * </ul>
 */
@DisplayName("Validation Tests (Misc)")
class ValidationTest {

    @Nested
    @DisplayName("Array Types")
    class ArrayTypes {

        @Test
        @DisplayName("OK with array type and field reference")
        void arrayWithFieldReference() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static final String[] DEFAULT_TAGS = {"default"};
                            
                                @WithDefaults
                                public void process(@DefaultValue(field = "DEFAULT_TAGS") String[] tags) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("OK with array type and factory method")
        void arrayWithFactory() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Service {
                                static String[] defaultTags() {
                                    return new String[]{"default", "test"};
                                }
                            
                                @WithDefaults
                                public void process(@DefaultFactory("defaultTags") String[] tags) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Exception Propagation")
    class ExceptionPropagation {

        @Test
        @DisplayName("Method with checked exception compiles")
        void methodWithCheckedException() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.FileService",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.io.IOException;
                            
                            public class FileService {
                                @WithDefaults
                                public String readFile(String path, @DefaultValue("UTF-8") String charset) 
                                        throws IOException {
                                    return "content";
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.FileServiceDefaults");
        }

        @Test
        @DisplayName("Constructor with checked exception compiles")
        void constructorWithCheckedException() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Connection",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.sql.SQLException;
                            
                            public class Connection {
                                @WithDefaults
                                public Connection(String url, @DefaultValue("root") String user) 
                                        throws SQLException {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ConnectionDefaults");
        }
    }

    @Nested
    @DisplayName("Generic Types")
    class GenericTypes {

        @Test
        @DisplayName("Generic type with factory compiles")
        void genericTypeWithFactory() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.DataService",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.util.List;
                            import java.util.ArrayList;
                            
                            public class DataService {
                                static List<String> defaultItems() {
                                    return new ArrayList<>();
                                }
                            
                                @WithDefaults
                                public void process(String name, @DefaultFactory("defaultItems") List<String> items) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Map type with factory compiles")
        void mapTypeWithFactory() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.ConfigService",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.util.Map;
                            import java.util.HashMap;
                            
                            public class ConfigService {
                                static Map<String, Object> defaultConfig() {
                                    return new HashMap<>();
                                }
                            
                                @WithDefaults
                                public void configure(@DefaultFactory("defaultConfig") Map<String, Object> config) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("Annotation Preservation")
    class AnnotationPreservation {

        @Test
        @DisplayName("Nullable annotation is preserved on generated parameters")
        void nullableAnnotationPreserved() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import javax.annotation.Nullable;
                            
                            public class Service {
                                @WithDefaults
                                public void process(
                                        String required,
                                        @Nullable @DefaultValue("optional") String optional) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ServiceDefaults");
        }
    }
}
