/**
 * Annotations for default parameter values in Java.
 * <p>
 * This package provides:
 * <ul>
 *   <li>{@link io.github.reugn.default4j.annotation.DefaultValue} - Specify default values for parameters or record components</li>
 *   <li>{@link io.github.reugn.default4j.annotation.DefaultFactory} - Specify factory methods for complex default values</li>
 *   <li>{@link io.github.reugn.default4j.annotation.WithDefaults} - Generate helper methods/factories for methods, constructors, classes, or records</li>
 *   <li>{@link io.github.reugn.default4j.annotation.IncludeDefaults} - Generate defaults for external classes you cannot modify</li>
 * </ul>
 * <p>
 * All annotations are processed by {@link io.github.reugn.default4j.processor.DefaultValueProcessor},
 * generating a unified {@code {ClassName}Defaults} class per source class.
 * <p>
 * Java records are fully supported - place {@code @DefaultValue} directly on record components.
 *
 * @see io.github.reugn.default4j.processor.DefaultValueProcessor
 */
package io.github.reugn.default4j.annotation;
