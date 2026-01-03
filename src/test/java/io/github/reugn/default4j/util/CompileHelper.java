package io.github.reugn.default4j.util;

import com.google.testing.compile.Compilation;
import io.github.reugn.default4j.processor.DefaultValueProcessor;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.Compiler.javac;

/**
 * Shared compilation helper for integration tests.
 */
public final class CompileHelper {

    private CompileHelper() {
    }

    public static Compilation compile(JavaFileObject... sources) {
        return javac()
                .withProcessors(new DefaultValueProcessor())
                .compile(sources);
    }
}
