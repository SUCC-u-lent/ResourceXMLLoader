package org.resourcexmlloader.customloader;

import org.resourcexmlloader.XMLDecompiler;
import org.resourcexmlloader.XMLGenerator;
import org.resourcexmlloader.XmlLoaderExtensions;
import org.resourcexmlloader.annotations.ExcludeField;
import org.resourcexmlloader.interfaces.XMLFieldCompiler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class FieldClassCompiler implements XMLFieldCompiler {
    private final ThreadLocal<Set<Object>> visitedObjects = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));
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
    public void compile(XMLGenerator generator, Document ownerDocument, Element rootElement,
                        Element fieldElement, Class<?> clazz, Class<?> valueClazz, Object fieldValue) {

        // local visited map for this call stack
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        compileInternal(generator, ownerDocument, rootElement, fieldElement, valueClazz, fieldValue, visited);
    }
    private void compileInternal(XMLGenerator generator, Document ownerDocument, Element rootElement,
                                  Element fieldElement, Class<?> valueClazz, Object fieldValue,
                                  Set<Object> visited) {

        if (fieldValue == null) {
            try {
                fieldValue = valueClazz.getDeclaredConstructor().newInstance();
            } catch (Exception ignored) {}
            if (fieldValue == null) return;
        }

        if (visited.contains(fieldValue)) {
            fieldElement.setAttribute("cycle", "true");
            return;
        }
        visited.add(fieldValue);

        Field[] fields = Arrays.stream(XmlLoaderExtensions.getAllFields(valueClazz))
                .filter(f -> !f.isAnnotationPresent(ExcludeField.class))
                .toArray(Field[]::new);

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object fieldValue2 = field.get(fieldValue);
                Element fieldElement2 = ownerDocument.createElement(field.getName());

                // recursive call with visited map
                compileInternal(generator, ownerDocument, rootElement, fieldElement2, field.getType(), fieldValue2, visited);

                fieldElement.appendChild(fieldElement2);
            } catch (Exception ignored) {}
        }

        visited.remove(fieldValue);
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
