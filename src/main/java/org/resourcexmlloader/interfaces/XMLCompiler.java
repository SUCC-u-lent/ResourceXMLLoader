package org.resourcexmlloader.interfaces;

import org.resourcexmlloader.XMLDecompiler;
import org.resourcexmlloader.XMLGenerator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Instructs how to compile a field into and from XML.
 * Must be added to the {@link org.resourcexmlloader.ResourceXML.Builder} to be used.
 * Used in the {@link org.resourcexmlloader.ResourceXML.Builder#useXMLGenerator(XMLCompiler...)} method to add as a compiler.
 */
public interface XMLCompiler
{
    /**
     * Handles what compiler is accepted first. Highest is selected first.
     * @return The priority in which this compiler is selected. Higher is selected first.
     */
    double getPriority();

    /**
     * Does this compiler accept this class? This method is called twice.
     * @param clazz The class the field is declared as and/or the class the field value is.
     * @return True if it should use this compiler.
     */
    boolean doesCompile(Class<?> clazz);

    /**
     * Handles compiling the field, usually only used for generating the template
     * @param generator The generator used, the only way to access this class is through this.
     * @param ownerDocument The Document that all elements are attached to, basically the XML owner
     * @param rootElement The {@code <root>} element
     * @param fieldElement The current fields element, attach children or other fields to this instead of root.
     * @param clazz The class the field is declared as.
     * @param valueClazz The class the field value is. If value is {@code @Nullable} then this will be the same as parameter {@code clazz}
     * @param fieldValue The field value. Can be {@code @Nullable}
     */
    void compile(XMLGenerator generator, Document ownerDocument, Element rootElement, Element fieldElement, Class<?> clazz, Class<?> valueClazz, Object fieldValue);

    Object decompile(XMLDecompiler decompiler, Document ownerDocument, Element rootElement, Element fieldElement, Class<?> clazz);
}
