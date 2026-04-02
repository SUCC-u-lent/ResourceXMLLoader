package org.ubunifu.resourcexmlloader.interfaces;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.lang.reflect.Field;

public interface XMLFieldHandler
{
    boolean accepts(Class<?> clazz);

    /**
     * Optional hook for loaders/generators to propagate runtime logging preferences.
     */
    default void setLoggingEnabled(boolean enabled) {
        // No-op by default for backwards compatibility.
    }

    void handleTemplateField(Class<?> clazz, Document document, Element rootElement, Element fieldElement, Class<?> fieldClass, Field field);
}
