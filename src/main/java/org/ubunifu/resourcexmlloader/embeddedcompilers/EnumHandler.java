package org.ubunifu.resourcexmlloader.embeddedcompilers;

import org.ubunifu.resourcexmlloader.interfaces.XMLFieldHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.lang.reflect.Field;
import java.util.Arrays;

public class EnumHandler implements XMLFieldHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EnumHandler.class);
    private boolean loggingEnabled;

    @Override
    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
    }

    private void logDebug(String message, Object... args) {
        if (loggingEnabled) {
            LOGGER.debug(message, args);
        }
    }

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
        logDebug("EnumHandler template generation class={} field={} enumType={} enumCount={} isArray={}", clazz.getName(), field.getName(), enumType.getName(), enums == null ? 0 : enums.length, fieldClass.isArray());
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
}
