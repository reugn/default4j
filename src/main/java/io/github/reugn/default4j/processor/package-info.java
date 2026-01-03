/**
 * Annotation processor implementation for default4j.
 * <p>
 * This package contains the compile-time processor that generates
 * helper classes for {@link io.github.reugn.default4j.annotation.WithDefaults}
 * and {@link io.github.reugn.default4j.annotation.IncludeDefaults}.
 * <p>
 * <b>Internal implementation</b> - not part of the public API.
 *
 * <p><b>Architecture:</b>
 * <pre>
 * DefaultValueProcessor (entry point)
 *     ├── MethodGenerator      ──┐
 *     ├── ConstructorGenerator ──┼── BuilderGenerator (shared)
 *     └── IncludeGenerator     ──┘
 *
 * Support utilities:
 *     ├── CodeGenUtils    - Parameter info, default expressions, string escaping
 *     ├── ValidationUtils - Compile-time validation checks
 *     └── ErrorReporter   - Error reporting interface
 * </pre>
 *
 * <p><b>Components:</b>
 * <ul>
 *   <li><b>DefaultValueProcessor</b> - Main annotation processor entry point</li>
 *   <li><b>MethodGenerator</b> - Generates helpers for method-level @WithDefaults</li>
 *   <li><b>ConstructorGenerator</b> - Generates helpers for constructor/record @WithDefaults</li>
 *   <li><b>IncludeGenerator</b> - Generates helpers for @IncludeDefaults (external types)</li>
 *   <li><b>BuilderGenerator</b> - Shared builder pattern generation utilities</li>
 *   <li><b>CodeGenUtils</b> - Shared code generation utilities</li>
 *   <li><b>ValidationUtils</b> - Compile-time validation for annotations</li>
 * </ul>
 *
 * @see io.github.reugn.default4j.annotation
 */
package io.github.reugn.default4j.processor;
