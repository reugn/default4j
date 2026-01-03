package io.github.reugn.default4j.util;

import com.google.testing.compile.Compilation;
import io.github.reugn.default4j.processor.DefaultValueProcessor;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.testing.compile.Compiler.javac;

/**
 * Helper for compiling and executing generated code at runtime.
 * Enables true E2E testing by actually running the generated methods.
 *
 * <p>Usage:
 * <pre>{@code
 * RuntimeTestHelper helper = RuntimeTestHelper.compile(source);
 * Object instance = helper.newInstance("example.Greeter");
 * Object result = helper.invoke("example.GreeterDefaults", "greet", instance);
 * assertEquals("Hello, World!", result);
 * }</pre>
 */
public final class RuntimeTestHelper {

    private final Compilation compilation;
    private final ClassLoader classLoader;
    private final Map<String, Class<?>> loadedClasses;

    private RuntimeTestHelper(Compilation compilation) {
        this.compilation = compilation;
        this.classLoader = new CompiledClassLoader();
        this.loadedClasses = new HashMap<>();
    }

    /**
     * Compiles the given sources with the DefaultValueProcessor.
     *
     * @param sources the source files to compile
     * @return a helper for executing the compiled code
     * @throws AssertionError if compilation fails
     */
    public static RuntimeTestHelper compile(JavaFileObject... sources) {
        Compilation compilation = javac()
                .withProcessors(new DefaultValueProcessor())
                .compile(sources);

        if (compilation.status() != Compilation.Status.SUCCESS) {
            throw new AssertionError("Compilation failed: " + compilation.diagnostics());
        }

        return new RuntimeTestHelper(compilation);
    }

    private static String formatTypes(Class<?>[] types) {
        return Arrays.stream(types)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Creates a new instance of the specified class using its no-arg constructor.
     *
     * @param className fully qualified class name
     * @return new instance
     */
    public Object newInstance(String className) {
        try {
            Class<?> clazz = loadClass(className);
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create instance of " + className, e);
        }
    }

    /**
     * Creates a new instance using a constructor with the specified parameter types.
     *
     * @param className  fully qualified class name
     * @param paramTypes constructor parameter types
     * @param args       constructor arguments
     * @return new instance
     */
    public Object newInstance(String className, Class<?>[] paramTypes, Object... args) {
        try {
            Class<?> clazz = loadClass(className);
            Constructor<?> constructor = clazz.getDeclaredConstructor(paramTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create instance of " + className, e);
        }
    }

    /**
     * Invokes a static method on the specified class.
     *
     * @param className  fully qualified class name
     * @param methodName method to invoke
     * @param args       method arguments
     * @return the method's return value (null for void methods)
     */
    public Object invoke(String className, String methodName, Object... args) {
        try {
            Class<?> clazz = loadClass(className);
            Class<?>[] paramTypes = getParamTypes(args);
            Method method = findMethod(clazz, methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke " + className + "." + methodName, e);
        }
    }

    /**
     * Invokes an instance method.
     *
     * @param instance   the object to invoke the method on
     * @param methodName method to invoke
     * @param args       method arguments
     * @return the method's return value
     */
    public Object invokeOn(Object instance, String methodName, Object... args) {
        try {
            Class<?>[] paramTypes = getParamTypes(args);
            Method method = findMethod(instance.getClass(), methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(instance, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke " + methodName, e);
        }
    }

    /**
     * Gets a field value from an instance.
     *
     * @param instance  the object
     * @param fieldName field name
     * @return the field value
     */
    public Object getField(Object instance, String fieldName) {
        try {
            var field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to get field " + fieldName, e);
        }
    }

    /**
     * Loads a class from the compilation output.
     *
     * @param className fully qualified class name
     * @return the loaded class
     */
    public Class<?> loadClass(String className) {
        return loadedClasses.computeIfAbsent(className, name -> {
            try {
                return classLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Class not found: " + name, e);
            }
        });
    }

    /**
     * Returns the underlying compilation for additional assertions.
     */
    public Compilation getCompilation() {
        return compilation;
    }

    private Class<?>[] getParamTypes(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return types;
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>[] paramTypes) throws NoSuchMethodException {
        // Try exact match first
        try {
            return clazz.getDeclaredMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            // Fall through to fuzzy matching
        }

        // Fuzzy match - handle primitive/wrapper type differences
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.getName().equals(name)) continue;
            if (method.getParameterCount() != paramTypes.length) continue;

            Class<?>[] methodParamTypes = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!isAssignable(methodParamTypes[i], paramTypes[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }

        throw new NoSuchMethodException(clazz.getName() + "." + name + "(" + formatTypes(paramTypes) + ")");
    }

    private boolean isAssignable(Class<?> target, Class<?> source) {
        if (target.isAssignableFrom(source)) return true;

        // Handle primitive/wrapper conversions
        if (target == int.class && source == Integer.class) return true;
        if (target == long.class && source == Long.class) return true;
        if (target == double.class && source == Double.class) return true;
        if (target == float.class && source == Float.class) return true;
        if (target == boolean.class && source == Boolean.class) return true;
        if (target == byte.class && source == Byte.class) return true;
        if (target == short.class && source == Short.class) return true;
        if (target == char.class && source == Character.class) return true;

        return false;
    }

    /**
     * ClassLoader that loads classes from compilation output.
     */
    private class CompiledClassLoader extends ClassLoader {
        CompiledClassLoader() {
            super(RuntimeTestHelper.class.getClassLoader());
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";

            for (JavaFileObject file : compilation.generatedFiles()) {
                if (file.getKind() == JavaFileObject.Kind.CLASS) {
                    String filePath = file.toUri().getPath();
                    if (filePath.endsWith(path)) {
                        try (InputStream is = file.openInputStream()) {
                            byte[] bytes = is.readAllBytes();
                            return defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException e) {
                            throw new ClassNotFoundException("Failed to load " + name, e);
                        }
                    }
                }
            }

            throw new ClassNotFoundException(name);
        }
    }
}
