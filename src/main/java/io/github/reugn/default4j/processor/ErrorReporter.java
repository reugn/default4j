package io.github.reugn.default4j.processor;

import javax.lang.model.element.Element;

/**
 * Interface for reporting compilation errors.
 */
@FunctionalInterface
interface ErrorReporter {
    /**
     * Reports an error on the given element.
     *
     * @param element the element where the error occurred
     * @param message the error message
     */
    void error(Element element, String message);
}
