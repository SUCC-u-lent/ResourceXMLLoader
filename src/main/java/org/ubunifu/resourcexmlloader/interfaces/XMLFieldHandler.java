package org.ubunifu.resourcexmlloader.interfaces;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.lang.reflect.Field;

public interface XMLFieldHandler
{
    boolean accepts(Class<?> clazz);

    void handleTemplateField(Class<?> clazz, Document document, Element rootElement, Element fieldElement, Class<?> fieldClass, Field field);

    Object decompileField(Class<?> clazz, Document document, Element root, Element fieldElement, Class<?> fieldClass, Field field);
}
