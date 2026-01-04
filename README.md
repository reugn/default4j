# default4j

[![Build](https://github.com/reugn/default4j/actions/workflows/build.yml/badge.svg)](https://github.com/reugn/default4j/actions/workflows/build.yml)

Default parameter values for Java via annotation processing.

Java doesn't natively support default parameter values like Scala, Kotlin, or Python.
This library fills that gap by generating helper code at compile time.

## Table of Contents

- [Features](#features)
- [Comparison with Other Libraries](#comparison-with-other-libraries)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Usage Guide](#usage-guide)
- [Supported Types](#supported-types)
- [Annotation Reference](#annotation-reference)
- [Compile-Time Validation](#compile-time-validation)
- [How It Works](#how-it-works)
- [Requirements](#requirements)
- [Building from Source](#building-from-source)
- [License](#license)

## Features

- **Default parameters for methods** — call `greet()` instead of `greet("World", "Hello")`
- **Named parameters** — set only what you need: `.host("prod").timeout(30).call()`
- **Constructor & record factories** — `UserDefaults.create("Alice")` with sensible defaults
- **Works with external types** — generate defaults for third-party classes you can't modify
- **Extensive compile-time validation** — catch errors early with helpful messages and typo suggestions
- **Compile-time only** — no runtime dependencies, no reflection, just plain Java

## Comparison with Other Libraries

### Feature Comparison

| Feature | default4j | Lombok | Immutables | AutoValue | record-builder |
|---------|-----------|--------|------------|-----------|----------------|
| **Primary Focus** | Default values | Boilerplate reduction | Immutable objects | Value types | Record builders |
| **Method Defaults** | ✅ Full support | ❌ | ❌ | ❌ | ❌ |
| **Constructor Defaults** | ✅ Full support | ⚠️ Limited | ⚠️ Via builder | ⚠️ Via builder | ⚠️ Via builder |
| **Named Parameters** | ✅ Built-in | ❌ | ✅ Via builder | ✅ Via builder | ✅ Via builder |
| **Record Support** | ✅ Native | ⚠️ Partial | ✅ | ❌ | ✅ Native |
| **External Types** | ✅ `@IncludeDefaults` | ❌ | ❌ | ❌ | ✅ |
| **Factory Methods** | ✅ `@DefaultFactory` | ❌ | ❌ | ❌ | ❌ |
| **Field References** | ✅ `@DefaultValue(field=...)` | ❌ | ❌ | ❌ | ❌ |

### Technical Comparison

| Aspect | default4j | Lombok | Immutables | AutoValue | record-builder |
|--------|-----------|--------|------------|-----------|----------------|
| **Approach** | Annotation Processing | Bytecode Modification | Annotation Processing | Annotation Processing | Annotation Processing |
| **Runtime Dependency** | None | None | Optional | None | None |
| **IDE Plugin Required** | No | Yes | No | No | No |
| **Compile-time Validation** | ✅ Extensive | ⚠️ Limited | ✅ | ✅ | ✅ |
| **Debuggable Output** | ✅ Plain Java | ⚠️ Complex | ✅ Plain Java | ✅ Plain Java | ✅ Plain Java |
| **Java Version** | 17+ | 8+ | 8+ | 8+ | 16+ |

### When to Use Each

| Library            | Best For                                                                                 |
|--------------------|------------------------------------------------------------------------------------------|
| **default4j**      | Adding default parameters to existing code, method defaults, Python/Kotlin-like defaults |
| **Lombok**         | Reducing boilerplate (getters, setters, equals, toString), quick prototyping             |
| **Immutables**     | Complex immutable objects with many optional fields, serialization support               |
| **AutoValue**      | Simple value types, Google ecosystem integration                                         |
| **record-builder** | Adding builders to records, withers for records                                          |

### Complementary Usage

default4j can work alongside other libraries:

```java
// With Lombok - use Lombok for boilerplate, default4j for defaults
@Getter @Setter
public class Config {
    @WithDefaults
    public Config(@DefaultValue("localhost") String host) { ... }
}

// With record-builder - use record-builder for withers, default4j for factory defaults
@RecordBuilder  // Generates withHost(), withPort() etc.
@WithDefaults   // Generates ConfigDefaults.create() with defaults
public record Config(
    @DefaultValue("localhost") String host,
    @DefaultValue("8080") int port) {}
```

### Key Differentiators

**default4j is unique in providing:**

1. **Method-level defaults** — No other library supports default values for regular method parameters
2. **Unified syntax** — Same `@DefaultValue` works for methods, constructors, and records
3. **True default values** — Unlike builders, you get actual default parameters (omit trailing args)
4. **Factory method defaults** — `@DefaultFactory` for computed/lazy default values
5. **Field reference defaults** — `@DefaultValue(field="CONSTANT")` for static constants
6. **External type defaults** — `@IncludeDefaults` for third-party classes you can't modify

### When to Use Constructor Defaults

Java allows inline field initialization, but constructor defaults solve problems inline initialization can't:

| Use Case | Inline Defaults | default4j |
|----------|----------------|-----------|
| **Records & Immutables** | ❌ Not possible | ✅ Full support |
| **Factory Methods** | ❌ Manual boilerplate | ✅ Auto-generated |
| **Third-Party Classes** | ❌ Can't modify source | ✅ `@IncludeDefaults` |
| **Computed Defaults** | ❌ No method calls | ✅ `@DefaultFactory` |
| **Builder Pattern** | ❌ Manual implementation | ✅ `named=true` |
| **Self-Documenting API** | ❌ Defaults hidden in code | ✅ Visible in annotations |

**Best for:** Records, immutable classes, factory patterns, external types, computed/dynamic values.

**When inline is simpler:** Mutable classes with simple literal defaults that don't need factory methods.

---

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.reugn</groupId>
    <artifactId>default4j</artifactId>
    <version>${version}</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.reugn:default4j:${version}'
annotationProcessor 'io.github.reugn:default4j:${version}'
```

## Quick Start

```java
import io.github.reugn.default4j.annotation.*;

@WithDefaults
public class Config {
    public Config(
            @DefaultValue("localhost") String host,
            @DefaultValue("8080") int port) {
        // ...
    }
}

// Usage - generated ConfigDefaults class:
Config c1 = ConfigDefaults.create();              // host="localhost", port=8080
Config c2 = ConfigDefaults.create("example.com"); // host="example.com", port=8080
```

---

## Usage Guide

### 1. Method Defaults

Generate overloaded static methods that omit trailing parameters with defaults.

```java
public class Greeter {
    @WithDefaults
    public String greet(
            @DefaultValue("World") String name,
            @DefaultValue("Hello") String greeting) {
        return greeting + ", " + name + "!";
    }
}
```

**Generated usage:**
```java
Greeter g = new Greeter();
GreeterDefaults.greet(g);                  // "Hello, World!"
GreeterDefaults.greet(g, "Alice");         // "Hello, Alice!"
GreeterDefaults.greet(g, "Alice", "Hi");   // "Hi, Alice!"
```

### 2. Named Parameters

Use `named = true` to generate a fluent builder that allows **skipping any parameter**, not just trailing ones.

```java
public class Database {
    @WithDefaults(named = true)
    public Connection connect(
            @DefaultValue("localhost") String host,
            @DefaultValue("5432") int port,
            @DefaultValue("postgres") String user) {
        return createConnection(host, port, user);
    }
}
```

**Generated usage:**
```java
Database db = new Database();

// Skip port, set only host and user
DatabaseDefaults.connect(db)
    .host("prod.example.com")
    .user("admin")
    .call();

// Use all defaults
DatabaseDefaults.connect(db).call();
```

### 3. Constructor Defaults

Generate factory methods for constructors with default parameters.

```java
public class User {
    @WithDefaults
    public User(
            String name,
            @DefaultValue("user@example.com") String email,
            @DefaultValue("USER") String role) {
        // ...
    }
}
```

**Generated usage:**
```java
User u1 = UserDefaults.create("Alice");                     // Default email & role
User u2 = UserDefaults.create("Bob", "bob@test.com");       // Default role
User u3 = UserDefaults.create("Carol", "c@x.com", "ADMIN"); // All specified
```

**Named mode for constructors:**
```java
public class User {
    @WithDefaults(named = true)
    public User(String name, @DefaultValue("USER") String role) {
        // ...
    }
}

// Skip to any parameter
User u = UserDefaults.create()
    .name("Alice")
    .build();  // role uses default
```

### 4. Class-Level Annotation

Apply `@WithDefaults` to a class to generate helpers for all constructors and methods that have `@DefaultValue` or
`@DefaultFactory` parameters.

```java
@WithDefaults
public class Service {
    // Constructor with defaults -> factory methods generated
    public Service(
            @DefaultValue("default") String name,
            @DefaultValue("100") int value) {
        // ...
    }
    
    // Method with defaults -> helper methods generated
    public void process(@DefaultValue("INFO") String level) {
        // ...
    }
}
```

**Generated usage:**
```java
Service s = ServiceDefaults.create();   // All constructor defaults
ServiceDefaults.process(s);             // Method with default level
```

**With options:**
```java
@WithDefaults(named = true, methodName = "builder")
public class AppConfig {
    public AppConfig(
            @DefaultValue("localhost") String host,
            @DefaultValue("8080") int port) {
        // ...
    }
}

// Usage:
AppConfigDefaults.builder().port(3000).build();
```

**Annotation Precedence:**

Method/constructor-level `@WithDefaults` takes precedence over class-level settings:

```java
@WithDefaults(named = true)  // Class uses builder mode
public class Api {
    // Uses class-level named=true -> builder
    public void fetch(@DefaultValue("/api") String path) { }
    
    // Overrides with named=false -> static overloads
    @WithDefaults(named = false)
    public void send(@DefaultValue("POST") String method) { }
}
```

### 5. Java Records

Works with Java records — place `@DefaultValue` directly on record components.

```java
@WithDefaults
public record ServerConfig(
        @DefaultValue("localhost") String host,
        @DefaultValue("8080") int port,
        @DefaultValue("false") boolean ssl) {}
```

**Generated usage:**
```java
ServerConfig c1 = ServerConfigDefaults.create();                // All defaults
ServerConfig c2 = ServerConfigDefaults.create("api.com");       // Custom host
ServerConfig c3 = ServerConfigDefaults.create("api.com", 443);  // Custom host + port
```

**Named mode with records:**
```java
@WithDefaults(named = true)
public record DatabaseConfig(
        @DefaultValue("localhost") String host,
        @DefaultValue("5432") int port,
        @DefaultValue("postgres") String database) {}

// Skip any component:
DatabaseConfig cfg = DatabaseConfigDefaults.create()
    .host("prod.example.com")
    .database("myapp")
    .build();  // port uses default
```

### 6. Static Field References

Use `@DefaultValue(field = ...)` to reference static constants:

```java
public class Service {
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    static final String DEFAULT_HOST = "localhost";
    
    @WithDefaults
    public void connect(
            @DefaultValue(field = "DEFAULT_HOST") String host,
            @DefaultValue(field = "DEFAULT_TIMEOUT") Duration timeout) {
        // ...
    }
}
```

**External class reference:**
```java
public class Defaults {
    public static final Duration TIMEOUT = Duration.ofSeconds(30);
    public static final String HOST = "localhost";
}

public class Client {
    @WithDefaults
    public void connect(
            @DefaultValue(field = "Defaults.HOST") String host,
            @DefaultValue(field = "Defaults.TIMEOUT") Duration timeout) {
        // ...
    }
}
```

> [!NOTE]
> For classes outside the current package, use the fully qualified class name (e.g., `com.example.Defaults.TIMEOUT`).

### 7. Factory Methods

Use `@DefaultFactory` for computed values:

```java
public class Logger {
    static LocalDateTime timestamp() {
        return LocalDateTime.now();
    }
    
    @WithDefaults
    public void log(
            String message,
            @DefaultFactory("timestamp") LocalDateTime time) {
        // ...
    }
}
```

> [!NOTE]
> **Evaluation Timing:**
> - **Non-named mode**: Factory called on each helper method invocation
> - **Named mode (builder)**: Factory called once at builder creation, not at `call()`/`build()`

**External class factory:**
```java
// Same package - simple class name works
@DefaultFactory("Factories.defaultConfig") Config config

// Different package - use fully qualified name
@DefaultFactory("java.util.UUID.randomUUID") UUID id
```

> [!NOTE]
> The annotation processor cannot access import statements.
> For classes outside the current package, use the fully qualified class name.

**Mix all annotation types:**
```java
@WithDefaults
public void request(
        String url,
        @DefaultValue("GET") String method,
        @DefaultValue(field = "DEFAULT_TIMEOUT") Duration timeout,
        @DefaultFactory("createHeaders") Map<String, String> headers) {
    // ...
}
```

---

## Supported Types

The following types can be used with `@DefaultValue` string literals:

| Type                  | Example                  |
|-----------------------|--------------------------|
| `String`              | `@DefaultValue("hello")` |
| `int` / `Integer`     | `@DefaultValue("42")`    |
| `long` / `Long`       | `@DefaultValue("100")`   |
| `double` / `Double`   | `@DefaultValue("3.14")`  |
| `float` / `Float`     | `@DefaultValue("2.5")`   |
| `boolean` / `Boolean` | `@DefaultValue("true")`  |
| `byte` / `Byte`       | `@DefaultValue("10")`    |
| `short` / `Short`     | `@DefaultValue("100")`   |
| `char` / `Character`  | `@DefaultValue("x")`     |
| `null` (for objects)  | `@DefaultValue("null")`  |

For other types, use `@DefaultFactory` or `@DefaultValue(field=...)`.

---

## Annotation Reference

### `@WithDefaults`

Marks a method, constructor, or class for code generation.

| Option       | Type      | Default    | Applies To           | Description                                  |
|--------------|-----------|------------|----------------------|----------------------------------------------|
| `named`      | `boolean` | `false`    | All                  | Generate fluent builder instead of overloads |
| `methodName` | `String`  | `"create"` | Constructors/Classes | Custom factory method name                   |

### `@DefaultValue`

Specifies the default value for a parameter or record component.

| Option  | Type     | Default | Description                 |
|---------|----------|---------|-----------------------------|
| `value` | `String` | `""`    | Default as a string literal |
| `field` | `String` | `""`    | Reference to a static field |

```java
@DefaultValue("hello")               // String literal
@DefaultValue("42")                  // int literal
@DefaultValue("null")                // null reference
@DefaultValue(field = "TIMEOUT")     // Same-class field
@DefaultValue(field = "Cfg.TIMEOUT") // External class field
```

**Note:** Use `value` or `field`, not both.

### `@DefaultFactory`

Specifies a factory method for computed default values.

```java
@DefaultFactory("createConfig")            // Same-class method
@DefaultFactory("Factories.defaultConfig") // External class method
```

**Requirements:**
- Method must be `static` with no parameters
- Must be accessible (package-private or public)
- Return type must be assignable to the parameter type

### `@IncludeDefaults`

Generates default helpers for external classes you cannot modify (third-party libraries, generated code, etc.).

| Option       | Type         | Default    | Description                      |
|--------------|--------------|------------|----------------------------------|
| `value`      | `Class<?>[]` | required   | Classes to generate defaults for |
| `named`      | `boolean`    | `false`    | Generate fluent builder          |
| `methodName` | `String`     | `"create"` | Factory method name              |

**Use case:** External records/immutables with many parameters that you construct repeatedly with similar values
(e.g., test fixtures, configuration objects).

```java
// External record from a library (you can't modify this)
public record ExternalConfig(String host, int port, Duration timeout) {}

// Your code - define defaults via convention
@IncludeDefaults(ExternalConfig.class)
public class Defaults {
    // Convention: DEFAULT_{COMPONENT_NAME} for fields
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8080;
    
    // Convention: default{ComponentName}() for methods
    public static Duration defaultTimeout() {
        return Duration.ofSeconds(30);
    }
}

// Generated: ExternalConfigDefaults with factory methods
ExternalConfig cfg = ExternalConfigDefaults.create();            // All defaults
ExternalConfig cfg2 = ExternalConfigDefaults.create("prod.com"); // Custom host
```

**Matching conventions:**
- `DEFAULT_HOST` matches `host` (case-insensitive, underscores ignored)
- `DEFAULT_FIRST_NAME` matches `firstName`
- `defaultTimeout()` matches `timeout`

> [!NOTE]
> **Constructor Selection:** When a class has multiple public constructors, the processor
> selects the one that best matches your defined defaults. For example, if a class has both
> `Foo(int port)` and `Foo(String host, int port)` constructors, and you define `DEFAULT_HOST`
> and `DEFAULT_PORT`, the two-parameter constructor is chosen. A warning is issued for any
> defined defaults that don't match parameters in the selected constructor.

---

## Compile-Time Validation

default4j performs extensive compile-time validation to catch errors early, before your code runs.
All errors include helpful messages with suggestions when possible.

### Parameter Annotations

| Validation                                                   | Error Message                                          |
|--------------------------------------------------------------|--------------------------------------------------------|
| Both `@DefaultValue` and `@DefaultFactory` on same parameter | "Cannot have both @DefaultValue and @DefaultFactory"   |
| Both `value` and `field` in `@DefaultValue`                  | "Cannot specify both 'value' and 'field'"              |
| Empty `@DefaultValue("")` on non-String type                 | "Empty value is only valid for String type, not int"   |
| Unparseable literal value                                    | "'abc' is not a valid int"                             |
| `null` for primitive types                                   | "'null' is not a valid default for primitive type int" |

### Factory Method (`@DefaultFactory`)

| Validation               | Error Message                                                                |
|--------------------------|------------------------------------------------------------------------------|
| Method not found         | "Factory method 'foo' not found. Did you mean 'fooBar()'?"                   |
| Method not static        | "Factory method 'create' must be static"                                     |
| Method has parameters    | "Factory method 'create' must have no parameters"                            |
| Method returns void      | "Factory method 'create' cannot return void"                                 |
| Incompatible return type | "Factory method 'create' returns String which is not assignable to Duration" |

### Field Reference (`@DefaultValue(field=...)`)

| Validation              | Error Message                                                  |
|-------------------------|----------------------------------------------------------------|
| Field not found         | "Field 'TMEOUT' not found. Did you mean 'TIMEOUT'?"            |
| Field not static        | "Field 'timeout' must be static"                               |
| Incompatible field type | "Field 'COUNT' has type int which is not assignable to String" |

### Structural Validations

| Validation                                | Error Message                                                                               |
|-------------------------------------------|---------------------------------------------------------------------------------------------|
| Non-consecutive defaults (non-named mode) | "Parameter without default found after parameter with default"                              |
| Private method/constructor                | "Elements annotated with @WithDefaults cannot be private"                                   |
| @WithDefaults on interface                | "Can only be applied to methods, constructors, classes, or records"                         |
| Builder name conflict                     | "Builder name conflict: 'PersonBuilder' would be generated for both constructor and method" |

### `@IncludeDefaults` Validations

| Validation               | Error Message                                                                |
|--------------------------|------------------------------------------------------------------------------|
| Including an interface   | "Cannot include interface 'MyInterface'"                                     |
| Including abstract class | "Cannot include abstract class 'BaseConfig'"                                 |
| No public constructor    | "ExternalClass has no public constructor"                                    |
| No defaults defined      | Warning: "No defaults found for ExternalConfig"                              |
| Unused defaults          | Warning: "Default for 'hostname' does not match any parameter in selected constructor" |
| Non-consecutive defaults | "Non-consecutive defaults for ExternalConfig: 'database' has no default"    |

### Helpful Suggestions

When a field or method reference contains a typo, the error message suggests similar names:

```
// Your code
@DefaultValue(field = "TMEOUT")  // typo

// Error message
Field 'TMEOUT' not found in Service. Did you mean 'TIMEOUT'?
```

If no similar name is found, available options are listed:

```
// Error message
Factory method 'foo' not found in Service. 
Available static no-arg methods: createConfig(), defaultHost().
```

---

## How It Works

1. **Compile time**: The annotation processor scans for `@WithDefaults` annotations
2. **Code generation**: For each annotated element, it generates a `{ClassName}Defaults` class
3. **Zero runtime cost**: Generated code is plain Java with no reflection

**Example generated code:**
```java
// For: @WithDefaults public void greet(@DefaultValue("World") String name)

public final class GreeterDefaults {
    public static void greet(Greeter instance) {
        instance.greet("World");
    }
    
    public static void greet(Greeter instance, String name) {
        instance.greet(name);
    }
}
```

---

## Requirements

- Java 17 or higher
- Maven 3.6+

## Building from Source

```bash
git clone https://github.com/reugn/default4j.git
cd default4j
mvn clean install
```

## License

Licensed under the [Apache License 2.0](LICENSE).
