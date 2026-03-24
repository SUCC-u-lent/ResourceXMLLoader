package org.resourcexmlloader.interfaces;

import org.resourcexmlloader.XMLGenerator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public interface XMLCompiler {
    boolean doesCompile(Class<?> clazz);

    void compile(XMLGenerator generator, Document ownerDocument, Element rootElement, Element fieldElement, Class<?> clazz, Class<?> valueClazz, Object fieldValue);
}
