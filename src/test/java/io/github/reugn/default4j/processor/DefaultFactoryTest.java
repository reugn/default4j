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
 * Tests for @DefaultFactory annotation.
 */
@DisplayName("@DefaultFactory")
class DefaultFactoryTest {

    @Nested
    @DisplayName("Same Class Factory")
    class SameClassFactory {

        @Test
        @DisplayName("Factory method in same class")
        void factoryInSameClass() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.util.List;
                            
                            public class Service {
                                static List<String> defaultTags() {
                                    return List.of("default", "service");
                                }
                            
                                @WithDefaults
                                public void process(
                                        String name,
                                        @DefaultFactory("defaultTags") List<String> tags) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ServiceDefaults");
        }

        @Test
        @DisplayName("Private factory method")
        void privateFactoryMethod() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.ConfigService",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class ConfigService {
                                static Config defaultConfig() {
                                    return new Config("localhost", 8080);
                                }
                            
                                @WithDefaults
                                public void configure(@DefaultFactory("defaultConfig") Config config) {
                                }
                            
                                public record Config(String host, int port) {}
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ConfigServiceDefaults");
        }
    }

    @Nested
    @DisplayName("External Class Factory")
    class ExternalClassFactory {

        @Test
        @DisplayName("Factory method in external class")
        void factoryInExternalClass() {
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import java.time.LocalDateTime;
                            
                            public class Defaults {
                                public static LocalDateTime timestamp() {
                                    return LocalDateTime.now();
                                }
                            
                                public static String defaultName() {
                                    return "default";
                                }
                            }
                            """);

            JavaFileObject service = JavaFileObjects.forSourceString("test.LogService",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.time.LocalDateTime;
                            
                            public class LogService {
                                @WithDefaults
                                public void log(
                                        String message,
                                        @DefaultFactory("Defaults.timestamp") LocalDateTime time,
                                        @DefaultFactory("Defaults.defaultName") String name) {
                                }
                            }
                            """);

            Compilation compilation = compile(defaults, service);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.LogServiceDefaults");
        }
    }

    @Nested
    @DisplayName("Constructor Factory")
    class ConstructorFactory {

        @Test
        @DisplayName("Factory in constructor parameter")
        void factoryInConstructor() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Application",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.util.concurrent.ExecutorService;
                            import java.util.concurrent.Executors;
                            
                            public class Application {
                                static ExecutorService defaultExecutor() {
                                    return Executors.newCachedThreadPool();
                                }
                            
                                private final ExecutorService executor;
                            
                                @WithDefaults
                                public Application(
                                        String name,
                                        @DefaultFactory("defaultExecutor") ExecutorService executor) {
                                    this.executor = executor;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ApplicationDefaults");
        }
    }

    @Nested
    @DisplayName("Named Mode with Factory")
    class NamedModeFactory {

        @Test
        @DisplayName("Named mode method with factory")
        void namedModeWithFactory() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Database",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.util.Properties;
                            
                            public class Database {
                                static Properties defaultProperties() {
                                    Properties props = new Properties();
                                    props.setProperty("driver", "com.mysql.jdbc.Driver");
                                    return props;
                                }
                            
                                @WithDefaults(named = true)
                                public void connect(
                                        @DefaultValue("localhost") String host,
                                        @DefaultValue("3306") int port,
                                        @DefaultFactory("defaultProperties") Properties props) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.DatabaseDefaults");
        }

        @Test
        @DisplayName("Named mode constructor with factory")
        void namedModeConstructorWithFactory() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Client",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.time.Duration;
                            
                            public class Client {
                                static Duration defaultTimeout() {
                                    return Duration.ofSeconds(30);
                                }
                            
                                @WithDefaults(named = true)
                                public Client(
                                        String url,
                                        @DefaultFactory("defaultTimeout") Duration timeout,
                                        @DefaultValue("3") int retries) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ClientDefaults");
        }
    }

    @Nested
    @DisplayName("Mixed Defaults")
    class MixedDefaults {

        @Test
        @DisplayName("Mix of @DefaultValue and @DefaultFactory")
        void mixedDefaultValueAndFactory() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.HttpClient",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.time.Duration;
                            import java.util.Map;
                            
                            public class HttpClient {
                                static Duration defaultTimeout() {
                                    return Duration.ofSeconds(30);
                                }
                            
                                static Map<String, String> defaultHeaders() {
                                    return Map.of("User-Agent", "default4j/1.0");
                                }
                            
                                @WithDefaults
                                public void request(
                                        String url,
                                        @DefaultValue("GET") String method,
                                        @DefaultFactory("defaultTimeout") Duration timeout,
                                        @DefaultFactory("defaultHeaders") Map<String, String> headers) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.HttpClientDefaults");
        }
    }

    @Nested
    @DisplayName("Record Factory")
    class RecordFactory {

        @Test
        @DisplayName("Factory on record component")
        void factoryOnRecordComponent() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.ServerConfig",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.time.Duration;
                            
                            @WithDefaults
                            public record ServerConfig(
                                    @DefaultValue("localhost") String host,
                                    @DefaultValue("8080") int port,
                                    @DefaultFactory("ServerConfig.defaultTimeout") Duration timeout) {
                            
                                static Duration defaultTimeout() {
                                    return Duration.ofSeconds(60);
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ServerConfigDefaults");
        }
    }

    @Nested
    @DisplayName("Static Field")
    class StaticField {

        @Test
        @DisplayName("Field in same class")
        void fieldInSameClass() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Service",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.time.Duration;
                            
                            public class Service {
                                static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
                                static final String DEFAULT_HOST = "localhost";
                            
                                @WithDefaults
                                public void connect(
                                        @DefaultValue(field = "DEFAULT_HOST") String host,
                                        @DefaultValue(field = "DEFAULT_TIMEOUT") Duration timeout) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ServiceDefaults");
        }

        @Test
        @DisplayName("Field in external class")
        void fieldInExternalClass() {
            JavaFileObject defaults = JavaFileObjects.forSourceString("test.Defaults",
                    """
                            package test;
                            
                            import java.time.Duration;
                            
                            public class Defaults {
                                public static final Duration TIMEOUT = Duration.ofSeconds(60);
                                public static final String HOST = "api.example.com";
                                public static final int PORT = 8080;
                            }
                            """);

            JavaFileObject service = JavaFileObjects.forSourceString("test.Client",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.time.Duration;
                            
                            public class Client {
                                @WithDefaults
                                public void connect(
                                        @DefaultValue(field = "Defaults.HOST") String host,
                                        @DefaultValue(field = "Defaults.PORT") int port,
                                        @DefaultValue(field = "Defaults.TIMEOUT") Duration timeout) {
                                }
                            }
                            """);

            Compilation compilation = compile(defaults, service);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ClientDefaults");
        }

        @Test
        @DisplayName("Mix of field and method")
        void mixFieldAndMethod() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.MixedService",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultFactory;
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            import java.time.Duration;
                            
                            public class MixedService {
                                static final String DEFAULT_HOST = "localhost";
                            
                                static Duration computeTimeout() {
                                    return Duration.ofSeconds(30);
                                }
                            
                                @WithDefaults
                                public void connect(
                                        @DefaultValue(field = "DEFAULT_HOST") String host,
                                        @DefaultValue("8080") int port,
                                        @DefaultFactory("computeTimeout") Duration timeout) {
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.MixedServiceDefaults");
        }

        @Test
        @DisplayName("Field in constructor")
        void fieldInConstructor() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Config",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            public class Config {
                                static final String DEFAULT_NAME = "default";
                                static final int DEFAULT_VALUE = 100;
                            
                                private final String name;
                                private final int value;
                            
                                @WithDefaults
                                public Config(
                                        @DefaultValue(field = "DEFAULT_NAME") String name,
                                        @DefaultValue(field = "DEFAULT_VALUE") int value) {
                                    this.name = name;
                                    this.value = value;
                                }
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.ConfigDefaults");
        }

        @Test
        @DisplayName("Field on record component")
        void fieldOnRecordComponent() {
            JavaFileObject source = JavaFileObjects.forSourceString("test.Settings",
                    """
                            package test;
                            
                            import io.github.reugn.default4j.annotation.DefaultValue;
                            import io.github.reugn.default4j.annotation.WithDefaults;
                            
                            @WithDefaults
                            public record Settings(
                                    @DefaultValue("localhost") String host,
                                    @DefaultValue(field = "Settings.DEFAULT_PORT") int port) {
                            
                                static final int DEFAULT_PORT = 3000;
                            }
                            """);

            Compilation compilation = compile(source);
            assertThat(compilation).succeeded();
            assertThat(compilation).generatedSourceFile("test.SettingsDefaults");
        }
    }
}
