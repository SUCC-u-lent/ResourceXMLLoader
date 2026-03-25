package org.resourcexmlloader.interfaces;

import org.resourcexmlloader.XMLDecompiler;
import org.resourcexmlloader.XMLGenerator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.lang.reflect.Field;

/**
 * Instructs the compiler to use this function for compiling and decompiling specific classes.
 * <br/><br/>This works on a class-scope not field-scope
 * <br/><br/>Must be added to the {@link org.resourcexmlloader.ResourceXML.Builder} to be used.
 * <br/><br/>Used in the {@link org.resourcexmlloader.ResourceXML.Builder#useClassCompilers(XMLClassCompiler...)} method to add as a compiler.
 */
public interface XMLClassCompiler
{
    /**
     * What class this compiler accepts
     * @param clazz The class that is currently being tested against
     * @return True if this compiler accepts that class and wants to compile it, false if not.
     */
    boolean accepts(Class<?> clazz);

    /**
     * The priority of the compiler. Higher is selected first. If two compilers have the same priority then the one that was added first is selected first.
     * @return The priority, no restrictions.
     */
    double getPriority();

    /**
     * The method called when decompiling the class. The loader will prepare the XML documents and the fields but this method is responsible for actually getting the data from the XML and putting it in a method java can understand.
     * @param xmlDecompiler The decompiler if you want to use base methods.
     * @param dom The document
     * @param rootElement The root element, everything is descends from this.
     * @param fields The class's fields
     * @param clazz The class itself
     * @param classInstance A newly created instance of the class ready for population
     */
    void decompile(XMLDecompiler xmlDecompiler, Document dom, Element rootElement, Field[] fields, Class<?> clazz, Object classInstance);

    /**
     * The method called when compiling the class, the loader will prepare the XML document, fields and handle saving the document but putting data from java into readable XML is this methods responsibility.
     * @param xmlGenerator The generator in case you want to use base methods.
     * @param document The document
     * @param rootElement The root element, everything descends from this.
     * @param fields The class's fields.
     * @param aClass The class itself
     * @param classInstance The class that was passed into the compiler, it most likely has populated fields.
     */
    void compile(XMLGenerator xmlGenerator, Document document, Element rootElement, Field[] fields, Class<?> aClass, Object classInstance);
}
