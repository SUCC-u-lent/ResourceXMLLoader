package org.ubunifu.resourcexmlloader.embeddedcompilers;

import org.ubunifu.resourcexmlloader.interfaces.XMLFieldHandler;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.lang.reflect.Field;
import java.util.Arrays;

public class EnumHandler implements XMLFieldHandler
{
    @Override
    public boolean accepts(Class<?> clazz) {
        if (clazz.isArray())
            return clazz.getComponentType().isEnum();
        return clazz.isEnum();
    }

    @Override
    public void handleTemplateField(Class<?> clazz, Document document, Element rootElement, Element fieldElement, Class<?> fieldClass, Field field) {
        Class<?> enumType = fieldClass.isArray() ? fieldClass.getComponentType() : fieldClass;
        Enum<?>[] enums = (Enum<?>[]) enumType.getEnumConstants();
        if (fieldClass.isArray())
        {
            for (Enum<?> anEnum : enums) {
                Element enumElement = document.createElement("enum");
                enumElement.setAttribute("value", anEnum.name());
                fieldElement.appendChild(enumElement);
            }
        } else if (enums.length > 0)
        {
            Comment comment = document.createComment("The below is an enum, to display the options an invalid enum was set. When using this template, only choose one of the enums and remove the rest.");
            rootElement.appendChild(comment);
            fieldElement.setAttribute("value", String.join(", ", Arrays.stream(enums).map(Enum::name).toArray(String[]::new)));
        }
    }

    @Override
    public Object decompileField(Class<?> clazz, Document document, Element root, Element fieldElement, Class<?> fieldClass, Field field) {
        if (fieldClass.isArray())
        {
            NodeList enumElements = fieldElement.getElementsByTagName("enum");
            @SuppressWarnings("rawtypes") Class<? extends Enum> enumType = fieldClass.getComponentType().asSubclass(Enum.class);
            Enum<?>[] enums = (Enum<?>[]) java.lang.reflect.Array.newInstance(fieldClass.getComponentType(), enumElements.getLength());
            for (int i = 0; i < enumElements.getLength(); i++) {
                Element enumElement = (Element) enumElements.item(i);
                String value = enumElement.getAttribute("value");
                @SuppressWarnings({"rawtypes", "unchecked"}) Enum<?> enumValue = Enum.valueOf((Class) enumType, value);
                enums[i] = enumValue;
            }
            return enums;
        } else
        {
            String value = fieldElement.getAttribute("value");
            return Enum.valueOf(fieldClass.asSubclass(Enum.class), value);
        }
    }
}
