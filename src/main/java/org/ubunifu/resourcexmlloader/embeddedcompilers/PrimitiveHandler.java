package org.ubunifu.resourcexmlloader.embeddedcompilers;

import org.ubunifu.resourcexmlloader.interfaces.XMLFieldHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

public class PrimitiveHandler implements XMLFieldHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PrimitiveHandler.class);
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

    private void logWarn(String message, Object... args) {
        if (loggingEnabled) {
            LOGGER.warn(message, args);
        }
    }

    @Override
    public boolean accepts(Class<?> clazz) {
        if (clazz.isArray())
            return accepts(clazz.getComponentType());
        return clazz.isPrimitive()
                || clazz.equals(String.class)
                || clazz.equals(Integer.class)
                || clazz.equals(Double.class)
                || clazz.equals(Float.class)
                || clazz.equals(Long.class)
                || clazz.equals(Short.class)
                || clazz.equals(Byte.class)
                || clazz.equals(Boolean.class)
                || clazz.equals(Character.class)
                || clazz.equals(int.class)
                || clazz.equals(double.class)
                || clazz.equals(float.class)
                || clazz.equals(long.class)
                || clazz.equals(short.class)
                || clazz.equals(byte.class)
                || clazz.equals(boolean.class)
                || clazz.equals(char.class);
    }

    @Override
    public void handleTemplateField(Class<?> clazz, Document document, Element rootElement, Element fieldElement, Class<?> fieldClass, Field field)
    {
        logDebug("PrimitiveHandler template generation class={} field={} type={}", clazz.getName(), field.getName(), fieldClass.getName());
        if (fieldClass.isArray())
        {
            int randomLength = Math.max(new Random().nextInt(10),1);
            logDebug("Generating array template with {} item(s) for field={}", randomLength, field.getName());
            for (int i = 0; i < randomLength; i++) {
                Element itemElement = document.createElement("item");
                handleTemplateField(clazz, document, rootElement, itemElement, fieldClass.getComponentType(), field);
                fieldElement.appendChild(itemElement);
            }
        }
        else {
            Object defaultValue = getDefaultValue(fieldClass);
            fieldElement.setAttribute("value", defaultValue.toString());
            logDebug("Assigned template default value for field={} value={}", field.getName(), defaultValue);
        }
    }

    @Override
    public Object decompileField(Class<?> clazz, Document document, Element root, Element fieldElement, Class<?> fieldClass, Field field) {
        logDebug("PrimitiveHandler decompile class={} field={} type={}", clazz.getName(), field.getName(), fieldClass.getName());
        if (fieldClass.isArray())
        {
            Element[] children = getChildren(fieldElement);
            logDebug("Decompiling primitive array with {} element(s) for field={}", children.length, field.getName());
            Object array = Array.newInstance(fieldClass.getComponentType(), children.length);
            for (int i = 0; i < children.length; i++) {
                Element child = children[i];
                Array.set(array, i, decompField(child, fieldClass.getComponentType()));
            }
            return array;
        }
        return decompField(fieldElement, fieldClass);
    }

    private Element[] getChildren(Element element)
    {
        NodeList children = element.getChildNodes();
        List<Element> childElements = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element e) {
                childElements.add(e);
            }
        }
        return childElements.toArray(Element[]::new);
    }

    private Object getDefaultValue(Class<?> fieldClass)
    {
        if (fieldClass.equals(String.class)) {
            return "example_string";
        } else if (fieldClass.equals(Integer.class) || fieldClass.equals(int.class)) {
            return 0;
        } else if (fieldClass.equals(Double.class) || fieldClass.equals(double.class)) {
            return 0.0;
        } else if (fieldClass.equals(Float.class) || fieldClass.equals(float.class)) {
            return 0f;
        } else if (fieldClass.equals(Long.class) || fieldClass.equals(long.class)) {
            return 0L;
        } else if (fieldClass.equals(Short.class) || fieldClass.equals(short.class)) {
            return (short) 0;
        } else if (fieldClass.equals(Byte.class) || fieldClass.equals(byte.class)) {
            return (byte) 0;
        } else if (fieldClass.equals(Boolean.class) || fieldClass.equals(boolean.class)) {
            return false;
        } else if (fieldClass.equals(Character.class) || fieldClass.equals(char.class)) {
            return 'c';
        }
        throw new IllegalArgumentException("Unsupported field type: " + fieldClass.getName());
    }
    private Object decompField(Element fieldElement, Class<?> fieldClass)
    {
        String value = fieldElement.getAttribute("value");
        logDebug("Decompiling primitive value type={} rawValue={}", fieldClass.getName(), value);
        if (fieldClass.equals(String.class)) {
            return value;
        } else if (fieldClass.equals(Integer.class) || fieldClass.equals(int.class)) {
            return Integer.parseInt(value);
        } else if (fieldClass.equals(Double.class) || fieldClass.equals(double.class)) {
            return Double.parseDouble(value);
        } else if (fieldClass.equals(Float.class) || fieldClass.equals(float.class)) {
            return Float.parseFloat(value);
        } else if (fieldClass.equals(Long.class) || fieldClass.equals(long.class)) {
            return Long.parseLong(value);
        } else if (fieldClass.equals(Short.class) || fieldClass.equals(short.class)) {
            return Short.parseShort(value);
        } else if (fieldClass.equals(Byte.class) || fieldClass.equals(byte.class)) {
            return Byte.parseByte(value);
        } else if (fieldClass.equals(Boolean.class) || fieldClass.equals(boolean.class)) {
            return Boolean.parseBoolean(value);
        } else if (fieldClass.equals(Character.class) || fieldClass.equals(char.class)) {
            if (value.length() != 1) {
                logWarn("Invalid character value encountered: {}", value);
                throw new IllegalArgumentException("Invalid character value: " + value);
            }
            return value.charAt(0);
        }
        throw new IllegalArgumentException("Unsupported field type: " + fieldClass.getName());
    }
}
