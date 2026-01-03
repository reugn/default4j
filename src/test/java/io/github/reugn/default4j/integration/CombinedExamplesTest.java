package io.github.reugn.default4j.integration;

import com.google.testing.compile.JavaFileObjects;
import io.github.reugn.default4j.util.RuntimeTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration tests combining multiple @WithDefaults features.
 * These tests actually execute the generated code and verify runtime behavior.
 */
@DisplayName("Combined Feature Examples (E2E)")
class CombinedExamplesTest {

    @Test
    @DisplayName("Class with both methods and constructors")
    void methodsAndConstructors() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Repository",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Repository {
                            private final String database;
                            private final int timeout;
                            private String lastQuery;
                            private int lastLimit;
                        
                            @WithDefaults
                            public Repository(
                                    @DefaultValue("default-db") String database,
                                    @DefaultValue("30000") int timeout) {
                                this.database = database;
                                this.timeout = timeout;
                            }
                        
                            @WithDefaults
                            public String query(
                                    String sql,
                                    @DefaultValue("50") int limit,
                                    @DefaultValue("0") int offset) {
                                this.lastQuery = sql;
                                this.lastLimit = limit;
                                return sql + " LIMIT " + limit + " OFFSET " + offset;
                            }
                        
                            public String getDatabase() { return database; }
                            public int getTimeout() { return timeout; }
                            public String getLastQuery() { return lastQuery; }
                            public int getLastLimit() { return lastLimit; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);

        // Create with constructor defaults
        Object repo1 = helper.invoke("example.RepositoryDefaults", "create");
        assertThat(helper.invokeOn(repo1, "getDatabase")).isEqualTo("default-db");
        assertThat(helper.invokeOn(repo1, "getTimeout")).isEqualTo(30000);

        // Create with custom database
        Object repo2 = helper.invoke("example.RepositoryDefaults", "create", "custom-db");
        assertThat(helper.invokeOn(repo2, "getDatabase")).isEqualTo("custom-db");
        assertThat(helper.invokeOn(repo2, "getTimeout")).isEqualTo(30000);

        // Query with method defaults
        Object result = helper.invoke("example.RepositoryDefaults", "query", repo1, "SELECT *");
        assertThat(result).isEqualTo("SELECT * LIMIT 50 OFFSET 0");
        assertThat(helper.invokeOn(repo1, "getLastLimit")).isEqualTo(50);

        // Query with custom limit
        Object result2 = helper.invoke("example.RepositoryDefaults", "query", repo1, "SELECT *", 100);
        assertThat(result2).isEqualTo("SELECT * LIMIT 100 OFFSET 0");
    }

    @Test
    @DisplayName("Multiple methods with different modes")
    void multipleMethods() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Api",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Api {
                            @WithDefaults
                            public String get(
                                    String path,
                                    @DefaultValue("GET") String method,
                                    @DefaultValue("5000") int timeout) {
                                return method + " " + path + " (" + timeout + "ms)";
                            }
                        
                            @WithDefaults(named = true)
                            public String post(
                                    @DefaultValue("/") String path,
                                    @DefaultValue("{}") String body,
                                    @DefaultValue("application/json") String contentType) {
                                return "POST " + path + " [" + contentType + "] " + body;
                            }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object api = helper.newInstance("example.Api");

        // Static overloads - uses defaults
        assertThat(helper.invoke("example.ApiDefaults", "get", api, "/users"))
                .isEqualTo("GET /users (5000ms)");

        // Named builder - skip middle parameter
        Object postBuilder = helper.invoke("example.ApiDefaults", "post", api);
        helper.invokeOn(postBuilder, "path", "/users");
        helper.invokeOn(postBuilder, "body", "{\"name\":\"test\"}");
        assertThat(helper.invokeOn(postBuilder, "call"))
                .isEqualTo("POST /users [application/json] {\"name\":\"test\"}");
    }

    @Test
    @DisplayName("Mixed static and instance methods")
    void mixedStaticAndInstance() {
        JavaFileObject source = JavaFileObjects.forSourceString("example.Utils",
                """
                        package example;
                        
                        import io.github.reugn.default4j.annotation.DefaultValue;
                        import io.github.reugn.default4j.annotation.WithDefaults;
                        
                        public class Utils {
                            private int callCount = 0;
                        
                            @WithDefaults
                            public static String format(
                                    String value,
                                    @DefaultValue("%-20s") String pattern) {
                                return String.format(pattern, value);
                            }
                        
                            @WithDefaults
                            public String process(
                                    String input,
                                    @DefaultValue("upper") String transform) {
                                callCount++;
                                return transform.equals("upper") ? input.toUpperCase() : input.toLowerCase();
                            }
                        
                            public int getCallCount() { return callCount; }
                        }
                        """);

        RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
        Object utils = helper.newInstance("example.Utils");

        // Static method with default
        String formatted = (String) helper.invoke("example.UtilsDefaults", "format", utils, "test");
        assertThat(formatted).hasSize(20).startsWith("test");

        // Instance method with default - modifies state
        assertThat(helper.invoke("example.UtilsDefaults", "process", utils, "hello"))
                .isEqualTo("HELLO");
        assertThat(helper.invokeOn(utils, "getCallCount")).isEqualTo(1);

        // Call again - state accumulates
        helper.invoke("example.UtilsDefaults", "process", utils, "world");
        assertThat(helper.invokeOn(utils, "getCallCount")).isEqualTo(2);
    }
}
