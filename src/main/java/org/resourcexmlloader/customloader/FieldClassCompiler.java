package org.resourcexmlloader.customloader;

import org.resourcexmlloader.XMLGenerator;
import org.resourcexmlloader.XmlLoaderExtensions;
import org.resourcexmlloader.annotations.ExcludeField;
import org.resourcexmlloader.interfaces.XMLCompiler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.lang.reflect.Field;
import java.util.Arrays;

public class FieldClassCompiler implements XMLCompiler {
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
                generator.compileXMLClass(rootElement, fieldElement2, fieldType, fieldValue2);
                fieldElement.appendChild(fieldElement2);
            }catch(Exception ignored){}
        }
    }
}
