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

    public <T> void generateXML(String fileName,T object) throws ParserConfigurationException, IllegalAccessException, TransformerConfigurationException {

        Field[] fields = Arrays.stream(XmlLoaderExtensions.getAllFields(object.getClass()))
                .filter(f -> !f.isAnnotationPresent(ExcludeField.class))
                .filter(f-> !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()))
                .toArray(Field[]::new);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        Element classElement = document.createElement("classSource");
        classElement.setAttribute("value",object.getClass().getName());
        Element rootElement = document.createElement("root");
        rootElement.appendChild(classElement);
        document.appendChild(rootElement);
        for (Field field : fields)
        {
            field.setAccessible(true); // Always make accessible.
            Object fieldValue = field.get(object);
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();
            Element fieldElement = document.createElement(fieldName);

            // Now when generating we do something unique, if its a primative or a known type then we compile using that
            // Compiling is done using attributes not text content as it looks neater.
            compileXMLClass(rootElement, fieldElement, fieldType, fieldValue);
            rootElement.appendChild(fieldElement);
        }
        Transformer tr = TransformerFactory.newInstance().newTransformer();
        tr.setOutputProperty(OutputKeys.INDENT, "yes");
        tr.setOutputProperty(OutputKeys.METHOD, "xml");
        tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        // send DOM to file
        try {
            File file = new File(resourcePath.resolve(fileName).toString());
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            tr.transform(new DOMSource(document),
                    new StreamResult(new FileOutputStream(file)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void compileXMLClass(Element rootElement, Element fieldElement, Class<?> clazz, Object fieldValue)
    {
        if (fieldValue == null)
            try{fieldValue = clazz.getDeclaredConstructor().newInstance();}catch (Exception ignored){}
        if (fieldValue == null) return;
        if (clazz.isArray())
        {
            Class<?> componentType = clazz.getComponentType();
            int length = Array.getLength(fieldValue);
            // We don't set the length to the attribute because just getting the children will tell us the length of the end array.
            for (int i = 0; i < length; i++)
            {
                Object elementValue = Array.get(fieldValue, i);
                Element element = fieldElement.getOwnerDocument().createElement("element");
                compileXMLClass(rootElement, element, componentType, elementValue);
                fieldElement.appendChild(element);
            }
        }
        else if (XmlLoaderExtensions.isKnownDataType(clazz))
        {
            fieldElement.setAttribute("value", fieldValue.toString());
        }
        else
        {
            Class<?> valueClazz = fieldValue.getClass();
            Optional<XMLCompiler> compiler = Arrays.stream(this.compilers).filter(
                    c -> c.doesCompile(clazz) || c.doesCompile(valueClazz)
            ).max(Comparator.comparingDouble(XMLCompiler::getPriority));
            if (compiler.isEmpty()) throw new IllegalStateException("No compiler found for type " + clazz.getSimpleName());
            compiler.get().compile(this, fieldElement.getOwnerDocument(), rootElement, fieldElement, clazz, valueClazz, fieldValue);
        }
    }
}
