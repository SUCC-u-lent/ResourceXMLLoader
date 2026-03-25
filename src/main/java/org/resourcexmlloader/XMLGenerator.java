package org.resourcexmlloader;

import org.resourcexmlloader.annotations.ExcludeField;
import org.resourcexmlloader.annotations.XmlDataPath;
import org.resourcexmlloader.annotations.XmlFileName;
import org.resourcexmlloader.interfaces.XMLCompiler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

public class XMLGenerator
{
    Path resourcePath;
    OutputStream outputStream;
    XMLCompiler[] compilers;
    XMLGenerator(Path resourcePath, OutputStream outputStream, XMLCompiler[] compilers)
    {
        this.resourcePath = resourcePath;
        this.outputStream = outputStream;
        this.compilers = compilers;
    }
    public <T> void generateXML(String fileName, T object) throws ParserConfigurationException, IllegalAccessException, TransformerException, IOException {

        if (object == null) throw new IllegalArgumentException("Object cannot be null");

        Field[] fields = Arrays.stream(XmlLoaderExtensions.getAllFields(object.getClass()))
                .filter(f -> !f.isAnnotationPresent(ExcludeField.class))
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()))
                .toArray(Field[]::new);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        Element rootElement = document.createElement("root");

        Element classElement = document.createElement("classSource");
        classElement.setAttribute("value", object.getClass().getName());
        document.appendChild(rootElement);
        rootElement.appendChild(classElement);

        for (Field field : fields) {
            field.setAccessible(true);
            Object fieldValue = field.get(object);
            Element fieldElement = document.createElement(field.getName());

            compileXMLClass(rootElement, fieldElement, field.getType(), fieldValue);
            rootElement.appendChild(fieldElement);
        }

        // Create file
        File file = resourcePath.resolve(fileName).toFile();
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new IOException("Could not create directories for " + file.getAbsolutePath());
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        transformer.transform(new DOMSource(document), new StreamResult(file));

        System.out.println("XML generated successfully at: " + file.getAbsolutePath());
    }

    public void compileXMLClass(Element rootElement, Element fieldElement, Class<?> clazz, Object fieldValue) {
        if (fieldValue == null) {
            try {
                fieldValue = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception ignored) {}
            if (fieldValue == null) {
                fieldElement.setAttribute("value", "null");
                return;
            }
        }

        Document doc = fieldElement.getOwnerDocument();

        if (clazz.isArray()) {
            int length = Array.getLength(fieldValue);
            for (int i = 0; i < length; i++) {
                Object elementValue = Array.get(fieldValue, i);
                Element element = doc.createElement("element");
                compileXMLClass(rootElement, element, clazz.getComponentType(), elementValue);
                fieldElement.appendChild(element);
            }
        } else if (XmlLoaderExtensions.isKnownDataType(clazz)) {
            fieldElement.setAttribute("value", fieldValue.toString());
        } else {
            Class<?> valueClazz = fieldValue.getClass();
            Optional<XMLCompiler> compiler = Arrays.stream(this.compilers)
                    .filter(c -> c.doesCompile(clazz) || c.doesCompile(valueClazz))
                    .max(Comparator.comparingDouble(XMLCompiler::getPriority));

            if (compiler.isPresent()) {
                compiler.get().compile(this, doc, rootElement, fieldElement, clazz, valueClazz, fieldValue);
            } else {
                // Fallback: just set to string
                fieldElement.setAttribute("value", fieldValue.toString());
            }
        }
    }
}
