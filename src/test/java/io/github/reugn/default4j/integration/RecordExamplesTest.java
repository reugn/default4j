package io.github.reugn.default4j.integration;

import com.google.testing.compile.JavaFileObjects;
import io.github.reugn.default4j.util.RuntimeTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration tests for Java records with @WithDefaults.
 * These tests actually execute the generated code and verify runtime behavior.
 *
 * <p>Note: Record helpers generate overloads for trailing defaults only.
 * The full-params signature is not generated (use the canonical constructor).
 */
@DisplayName("Record Examples (E2E)")
class RecordExamplesTest {

    @Test
    @DisplayName("Record factory methods - using defaults")
    void recordFactoryWithDefaults() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.ServerConfig",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults
                        public record ServerConfig(
                                @DefaultValue("localhost") String host,
                                @DefaultValue("8080") int port,
                                @DefaultValue("false") boolean ssl) {}
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // All defaults
        Object config1 = helper.invoke("example.ServerConfigDefaults", "create");
        assertThat(helper.invokeOn(config1, "host")).isEqualTo("localhost");
        assertThat(helper.invokeOn(config1, "port")).isEqualTo(8080);
        assertThat(helper.invokeOn(config1, "ssl")).isEqualTo(false);

        // Custom host only
        Object config2 = helper.invoke("example.ServerConfigDefaults", "create", "api.example.com");
        assertThat(helper.invokeOn(config2, "host")).isEqualTo("api.example.com");
        assertThat(helper.invokeOn(config2, "port")).isEqualTo(8080);
        assertThat(helper.invokeOn(config2, "ssl")).isEqualTo(false);

        // Custom host and port
        Object config3 = helper.invoke("example.ServerConfigDefaults", "create", "api.example.com", 443);
        assertThat(helper.invokeOn(config3, "host")).isEqualTo("api.example.com");
        assertThat(helper.invokeOn(config3, "port")).isEqualTo(443);
        assertThat(helper.invokeOn(config3, "ssl")).isEqualTo(false);

        // All custom - use canonical constructor directly
        Object config4 = helper.newInstance("example.ServerConfig",
                new Class<?>[]{String.class, int.class, boolean.class},
                "api.example.com", 443, true);
        assertThat(helper.invokeOn(config4, "ssl")).isEqualTo(true);
    }

    @Test
    @DisplayName("Record with named builder - skip any component")
    void recordNamed() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.DatabaseConfig",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults(named = true)
                        public record DatabaseConfig(
                                @DefaultValue("localhost") String host,
                                @DefaultValue("5432") int port,
                                @DefaultValue("postgres") String database,
                                @DefaultValue("public") String schema) {}
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // All defaults
        Object builder1 = helper.invoke("example.DatabaseConfigDefaults", "create");
        Object config1 = helper.invokeOn(builder1, "build");
        assertThat(helper.invokeOn(config1, "host")).isEqualTo("localhost");
        assertThat(helper.invokeOn(config1, "port")).isEqualTo(5432);
        assertThat(helper.invokeOn(config1, "database")).isEqualTo("postgres");
        assertThat(helper.invokeOn(config1, "schema")).isEqualTo("public");

        // Override host and database only (skip port and schema)
        Object builder2 = helper.invoke("example.DatabaseConfigDefaults", "create");
        helper.invokeOn(builder2, "host", "prod.example.com");
        helper.invokeOn(builder2, "database", "myapp");
        Object config2 = helper.invokeOn(builder2, "build");
        assertThat(helper.invokeOn(config2, "host")).isEqualTo("prod.example.com");
        assertThat(helper.invokeOn(config2, "port")).isEqualTo(5432); // default
        assertThat(helper.invokeOn(config2, "database")).isEqualTo("myapp");
        assertThat(helper.invokeOn(config2, "schema")).isEqualTo("public"); // default

        // Override only schema
        Object builder3 = helper.invoke("example.DatabaseConfigDefaults", "create");
        helper.invokeOn(builder3, "schema", "app_schema");
        Object config3 = helper.invokeOn(builder3, "build");
        assertThat(helper.invokeOn(config3, "schema")).isEqualTo("app_schema");
    }

    @Test
    @DisplayName("Record with partial defaults - required + optional")
    void recordPartialDefaults() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Person",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults
                        public record Person(
                                String name,
                                @DefaultValue("0") int age,
                                @DefaultValue("Unknown") String country) {}
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Required only - uses defaults for age and country
        Object person1 = helper.invoke("example.PersonDefaults", "create", "Alice");
        assertThat(helper.invokeOn(person1, "name")).isEqualTo("Alice");
        assertThat(helper.invokeOn(person1, "age")).isEqualTo(0);
        assertThat(helper.invokeOn(person1, "country")).isEqualTo("Unknown");

        // Required + age
        Object person2 = helper.invoke("example.PersonDefaults", "create", "Bob", 30);
        assertThat(helper.invokeOn(person2, "name")).isEqualTo("Bob");
        assertThat(helper.invokeOn(person2, "age")).isEqualTo(30);
        assertThat(helper.invokeOn(person2, "country")).isEqualTo("Unknown");

        // All components - use canonical constructor
        Object person3 = helper.newInstance("example.Person",
                new Class<?>[]{String.class, int.class, String.class},
                "Carol", 25, "Canada");
        assertThat(helper.invokeOn(person3, "country")).isEqualTo("Canada");
    }

    @Test
    @DisplayName("Record with custom factory method name")
    void recordCustomMethod() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Coordinate",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults(methodName = "at")
                        public record Coordinate(
                                @DefaultValue("0.0") double x,
                                @DefaultValue("0.0") double y,
                                @DefaultValue("0.0") double z) {}
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Origin - all defaults
        Object origin = helper.invoke("example.CoordinateDefaults", "at");
        assertThat(helper.invokeOn(origin, "x")).isEqualTo(0.0);
        assertThat(helper.invokeOn(origin, "y")).isEqualTo(0.0);
        assertThat(helper.invokeOn(origin, "z")).isEqualTo(0.0);

        // Custom x only
        Object c1 = helper.invoke("example.CoordinateDefaults", "at", 1.0);
        assertThat(helper.invokeOn(c1, "x")).isEqualTo(1.0);
        assertThat(helper.invokeOn(c1, "y")).isEqualTo(0.0);
        assertThat(helper.invokeOn(c1, "z")).isEqualTo(0.0);

        // Custom x and y
        Object c2 = helper.invoke("example.CoordinateDefaults", "at", 1.0, 2.0);
        assertThat(helper.invokeOn(c2, "x")).isEqualTo(1.0);
        assertThat(helper.invokeOn(c2, "y")).isEqualTo(2.0);
        assertThat(helper.invokeOn(c2, "z")).isEqualTo(0.0);

        // All custom - use canonical constructor
        Object c3 = helper.newInstance("example.Coordinate",
                new Class<?>[]{double.class, double.class, double.class},
                1.0, 2.0, 3.0);
        assertThat(helper.invokeOn(c3, "z")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Record toString includes component values")
    void recordToString() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Point",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults
                        public record Point(
                                @DefaultValue("0") int x,
                                @DefaultValue("0") int y) {}
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Create with one default overridden
        Object point = helper.invoke("example.PointDefaults", "create", 5);
        String str = point.toString();

        assertThat(str).contains("5").contains("0"); // x=5, y=0 (default)
    }

    @Test
    @DisplayName("Record equals works correctly with defaults")
    void recordEquals() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Size",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults
                        public record Size(
                                @DefaultValue("100") int width,
                                @DefaultValue("100") int height) {}
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Two records created with same defaults should be equal
        Object size1 = helper.invoke("example.SizeDefaults", "create");
        Object size2 = helper.invoke("example.SizeDefaults", "create");
        assertThat(size1).isEqualTo(size2);

        // Different values should not be equal
        Object size3 = helper.invoke("example.SizeDefaults", "create", 200);
        assertThat(size1).isNotEqualTo(size3);
    }
}
