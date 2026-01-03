package io.github.reugn.default4j.integration;

import com.google.testing.compile.JavaFileObjects;
import io.github.reugn.default4j.util.RuntimeTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration tests for supported @DefaultValue types.
 * These tests actually execute the generated code and verify runtime behavior.
 */
@DisplayName("Type Examples (E2E)")
class TypeExamplesTest {

    @Test
    @DisplayName("All primitive types")
    void allPrimitives() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Primitives",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults
                        public record Primitives(
                                @DefaultValue("42") int intVal,
                                @DefaultValue("100") long longVal,
                                @DefaultValue("3.14") double doubleVal,
                                @DefaultValue("2.5") float floatVal,
                                @DefaultValue("true") boolean boolVal,
                                @DefaultValue("10") byte byteVal,
                                @DefaultValue("100") short shortVal,
                                @DefaultValue("x") char charVal) {}
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // All defaults
        Object primitives = helper.invoke("example.PrimitivesDefaults", "create");
        assertThat(helper.invokeOn(primitives, "intVal")).isEqualTo(42);
        assertThat(helper.invokeOn(primitives, "longVal")).isEqualTo(100L);
        assertThat(helper.invokeOn(primitives, "doubleVal")).isEqualTo(3.14);
        assertThat(helper.invokeOn(primitives, "floatVal")).isEqualTo(2.5f);
        assertThat(helper.invokeOn(primitives, "boolVal")).isEqualTo(true);
        assertThat(helper.invokeOn(primitives, "byteVal")).isEqualTo((byte) 10);
        assertThat(helper.invokeOn(primitives, "shortVal")).isEqualTo((short) 100);
        assertThat(helper.invokeOn(primitives, "charVal")).isEqualTo('x');
    }

    @Test
    @DisplayName("Wrapper types with null")
    void wrapperTypesWithNull() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Nullable",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        @WithDefaults
                        public record Nullable(
                                @DefaultValue("null") String text,
                                @DefaultValue("null") Integer number,
                                @DefaultValue("null") Boolean flag) {}
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // All defaults - should be null
        Object nullable = helper.invoke("example.NullableDefaults", "create");
        assertThat(helper.invokeOn(nullable, "text")).isNull();
        assertThat(helper.invokeOn(nullable, "number")).isNull();
        assertThat(helper.invokeOn(nullable, "flag")).isNull();
    }
}
