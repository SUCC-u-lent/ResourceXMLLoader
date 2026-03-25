package org.resourcexmlloader.interfaces;

import org.resourcexmlloader.XMLDecompiler;
import org.resourcexmlloader.XMLGenerator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Instructs how to compile a field into and from XML.
 * <br/><br/>Must be added to the {@link org.resourcexmlloader.ResourceXML.Builder} to be used.
 * <br/><br/>Used in the {@link org.resourcexmlloader.ResourceXML.Builder#useFieldCompilers(XMLFieldCompiler...)} method to add as a compiler.
 */
public interface XMLFieldCompiler
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
     * Overrides base compiling and decompile to always use this compiler, if you're getting an output that you dont want, just override this and set it to true
     * @param clazz The class the field is declared as and/or the class the field value is.
     * @return True if the base compiling should be ignored in favour of this compiler, false to use the base compiling and decompiling.
     */
    default boolean alwaysCompileUsing(Class<?> clazz) { return false; }

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

    Object getExampleValue(XMLGenerator xmlGenerator, Document doc, Element rootElement, Element fieldElement, Class<?> clazz, Class<?> valueClazz);
}
