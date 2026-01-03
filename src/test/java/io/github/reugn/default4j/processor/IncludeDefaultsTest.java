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
 * Tests for @IncludeDefaults annotation.
 * <p>
 * For validation tests related to @IncludeDefaults (e.g., non-consecutive defaults,
 * interface/abstract class errors), see {@link ValidationTest}.
 */
@DisplayName("@IncludeDefaults")
class IncludeDefaultsTest {

    @Nested
    @DisplayName("External Records")
    class ExternalRecords {

        @Test
        @DisplayName("Generate defaults for external record")
        void externalRecord() {
            JavaFileObject externalRecord = JavaFileObjects.forSourceString("test.ExternalConfig",
                    """
                            package test;
                            
                            public record ExternalConfig(String host, int port) {}
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(ExternalConfig.class)
                            public class Defaults {
                                public static final String DEFAULT_HOST = "localhost";
                                public static final int DEFAULT_PORT = 8080;
                            }
                            """);

            Compilation compilation = compile(externalRecord, defaults);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ExternalConfigDefaults");
        }

        @Test
        @DisplayName("Generate defaults with method factory")
        void externalRecordWithMethodFactory() {
            JavaFileObject externalRecord = JavaFileObjects.forSourceString("test.ServerConfig",
                    """
                            package test;
                            
                            import java.time.Duration;
                            
                            public record ServerConfig(String host, int port, Duration timeout) {}
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            import java.time.Duration;
                            
                            @IncludeDefaults(ServerConfig.class)
                            public class Defaults {
                                public static final String DEFAULT_HOST = "localhost";
                                public static final int DEFAULT_PORT = 8080;
                            
                                public static Duration defaultTimeout() {
                                    return Duration.ofSeconds(30);
                                }
                            }
                            """);

            Compilation compilation = compile(externalRecord, defaults);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ServerConfigDefaults");
        }

        // Note: Non-consecutive defaults validation tests are in ValidationTest.java

        @Test
        @DisplayName("Consecutive defaults from end - OK")
        void consecutiveDefaultsFromEnd() {
            JavaFileObject externalRecord = JavaFileObjects.forSourceString("test.ServerConfig",
                    """
                            package test;
                            
                            public record ServerConfig(String host, int port, int timeout) {}
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(ServerConfig.class)
                            public class Defaults {
                                // port and timeout have defaults (consecutive from middle)
                                public static final int DEFAULT_PORT = 8080;
                                public static final int DEFAULT_TIMEOUT = 30;
                            }
                            """);

            Compilation compilation = compile(externalRecord, defaults);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ServerConfigDefaults");
        }
    }

    @Nested
    @DisplayName("External Classes")
    class ExternalClasses {

        @Test
        @DisplayName("Generate defaults for external class")
        void externalClass() {
            JavaFileObject externalClass = JavaFileObjects.forSourceString("test.ConnectionSettings",
                    """
                            package test;
                            
                            public class ConnectionSettings {
                                private final String host;
                                private final int port;
                            
                                public ConnectionSettings(String host, int port) {
                                    this.host = host;
                                    this.port = port;
                                }
                            }
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(ConnectionSettings.class)
                            public class Defaults {
                                public static final String DEFAULT_HOST = "127.0.0.1";
                                public static final int DEFAULT_PORT = 3306;
                            }
                            """);

            Compilation compilation = compile(externalClass, defaults);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ConnectionSettingsDefaults");
        }
    }

    @Nested
    @DisplayName("Named Mode")
    class NamedMode {

        @Test
        @DisplayName("Generate named builder for external record")
        void namedBuilder() {
            JavaFileObject externalRecord = JavaFileObjects.forSourceString("test.ApiConfig",
                    """
                            package test;
                            
                            public record ApiConfig(String baseUrl, String apiKey, int timeout) {}
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(value = ApiConfig.class, named = true)
                            public class Defaults {
                                public static final String DEFAULT_BASE_URL = "https://api.example.com";
                                public static final int DEFAULT_TIMEOUT = 30;
                                // apiKey has no default - required
                            }
                            """);

            Compilation compilation = compile(externalRecord, defaults);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ApiConfigDefaults");
        }
    }

    @Nested
    @DisplayName("Multiple Includes")
    class MultipleIncludes {

        @Test
        @DisplayName("Include multiple external classes")
        void multipleClasses() {
            JavaFileObject record1 = JavaFileObjects.forSourceString("test.ConfigA",
                    """
                            package test;
                            
                            public record ConfigA(String name) {}
                            """);

            JavaFileObject record2 = JavaFileObjects.forSourceString("test.ConfigB",
                    """
                            package test;
                            
                            public record ConfigB(int value) {}
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.SharedDefaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults({ConfigA.class, ConfigB.class})
                            public class SharedDefaults {
                                // Defaults for ConfigA
                                public static final String DEFAULT_NAME = "default";
                            
                                // Defaults for ConfigB
                                public static final int DEFAULT_VALUE = 100;
                            }
                            """);

            Compilation compilation = compile(record1, record2, defaults);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ConfigADefaults");
            assertThat(compilation).generatedSourceFile("test.ConfigBDefaults");
        }
    }

    @Nested
    @DisplayName("Custom Method Name")
    class CustomMethodName {

        @Test
        @DisplayName("Custom factory method name")
        void customMethodName() {
            JavaFileObject externalRecord = JavaFileObjects.forSourceString("test.Settings",
                    """
                            package test;
                            
                            public record Settings(String name, int value) {}
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(value = Settings.class, methodName = "of")
                            public class Defaults {
                                public static final String DEFAULT_NAME = "test";
                                public static final int DEFAULT_VALUE = 42;
                            }
                            """);

            Compilation compilation = compile(externalRecord, defaults);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.SettingsDefaults");
        }
    }

    @Nested
    @DisplayName("Case Insensitive Matching")
    class CaseInsensitiveMatching {

        @Test
        @DisplayName("Match camelCase to SCREAMING_SNAKE_CASE")
        void caseInsensitiveMatch() {
            JavaFileObject externalRecord = JavaFileObjects.forSourceString("test.UserProfile",
                    """
                            package test;
                            
                            public record UserProfile(String firstName, String lastName, int ageInYears) {}
                            """);

            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.IncludeDefaults;
                            
                            @IncludeDefaults(UserProfile.class)
                            public class Defaults {
                                public static final String DEFAULT_FIRST_NAME = "John";
                                public static final String DEFAULT_LAST_NAME = "Doe";
                                public static final int DEFAULT_AGE_IN_YEARS = 25;
                            }
                            """);

            Compilation compilation = compile(externalRecord, defaults);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.UserProfileDefaults");
        }
    }
}
