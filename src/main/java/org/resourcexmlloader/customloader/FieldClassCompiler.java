package org.resourcexmlloader.customloader;

import org.resourcexmlloader.XMLDecompiler;
import org.resourcexmlloader.XMLGenerator;
import org.resourcexmlloader.XmlLoaderExtensions;
import org.resourcexmlloader.annotations.ExcludeField;
import org.resourcexmlloader.interfaces.XMLCompiler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class FieldClassCompiler implements XMLCompiler {
    @Override
    public double getPriority() {
        return -1; // Lowest priority, we want this to be selected last as it compiles anything with a no-arg constructor and is not an interface, so we want to give other compilers the chance to compile first.
    }

    @Override
    public boolean doesCompile(Class<?> clazz) {
        boolean result = false;
        try{
            var ignored = clazz.getDeclaredConstructor();
            result = true;
        }catch(Exception ignored){}
        return !clazz.isInterface() && result; // literally compiles anything.
    }

    @Override
    public void compile(XMLGenerator generator,Document ownerDocument, Element rootElement, Element fieldElement, Class<?> clazz, Class<?> valueClazz, Object fieldValue)
    {
        Class<?> clazz2 = fieldValue == null ? clazz : fieldValue.getClass();

        // If null, instantiate defaults using the no-arg constructor (doesCompile already guarantees it exists)
        if (fieldValue == null)
        {
            try { fieldValue = clazz2.getDeclaredConstructor().newInstance(); }
            catch (Exception ignored) {}
        }

        final Object resolvedValue = fieldValue;
        Field[] fields = Arrays.stream(XmlLoaderExtensions.getAllFields(clazz2))
                .filter(f -> !f.isAnnotationPresent(ExcludeField.class))
                .toArray(Field[]::new);
        for (Field field : fields)
        {
            field.setAccessible(true); // Always make accessible.
            try
            {
                Object fieldValue2 = field.get(resolvedValue);
                String fieldName = field.getName();
                Class<?> fieldType = field.getType();
                Element fieldElement2 = ownerDocument.createElement(fieldName);

                // Now when generating we do something unique, if its a primative or a known type then we compile using that
                // Compiling is done using attributes not text content as it looks neater.
                generator.compileXMLClass(rootElement, fieldElement2, fieldType, fieldValue2,rootElement.hasAttribute("isTemplate"));
                fieldElement.appendChild(fieldElement2);
            }catch(Exception ignored){}
        }
    }

    @Override
    public Object decompile(XMLDecompiler decompiler, Document ownerDocument, Element rootElement, Element fieldElement, Class<?> clazz) {
        try {
            Object classInstance = clazz.getDeclaredConstructor().newInstance();
            Field[] fields = Arrays.stream(XmlLoaderExtensions.getAllFields(clazz))
                    .filter(f -> !f.isAnnotationPresent(ExcludeField.class))
                    .filter(f -> !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()))
                    .toArray(Field[]::new);

            for (Field field : fields) {
                field.setAccessible(true);
                Element childElement = getChildElementByTagName(fieldElement, field.getName());
                if (childElement == null) continue;

                try {
                    Object decompiledValue = decompiler.decompileValue(rootElement, childElement, field.getType());
                    field.set(classInstance, decompiledValue);
                } catch (Exception ignored) {}
            }

            return classInstance;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decompile class " + clazz.getName(), e);
        }
    }

    @Override
    public Object getExampleValue(XMLGenerator xmlGenerator, Document doc, Element rootElement, Element fieldElement, Class<?> clazz, Class<?> valueClazz) {
        try{
            return valueClazz.getDeclaredConstructor().newInstance();
        }catch (Exception ignored){}
        return null;
    }

    private static Element getChildElementByTagName(Element element, String tagName) {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element childElement && tagName.equals(childElement.getTagName())) {
                return childElement;
            }
        }
        return null;
    }
}
