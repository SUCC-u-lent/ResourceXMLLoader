package org.resourcexmlloader;

import org.jetbrains.annotations.Nullable;
import org.resourcexmlloader.annotations.XmlIdentifier;
import org.w3c.dom.Element;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class XmlLoaderExtensions {
    public static Field getIdentifierField(Class<?> clazz) {
        Field[] fields = getAllFields(clazz);
        for (Field field : fields)
            if (field.isAnnotationPresent(XmlIdentifier.class) || field.getType().isAnnotationPresent(XmlIdentifier.class))
            {
                field.setAccessible(true);
                Field[] fieldTypeFields = field.getType().getDeclaredFields();
                for (Field fieldTypeField : fieldTypeFields)
                    if (fieldTypeField.isAnnotationPresent(XmlIdentifier.class) && isKnownDataType(fieldTypeField.getType()))
                    {
                        fieldTypeField.setAccessible(true);
                        return fieldTypeField;
                    }
                return field;
            }
        return null;
    }
    public static <T> @Nullable XmlIdentifier getIdentifier(T obj)
    {
        Class<?> clazz = obj.getClass();
        Field field = getIdentifierField(clazz);
        if (field == null) return null;
        return field.getAnnotation(XmlIdentifier.class);
    }
    public static <T> @Nullable String getIdentifierValue(T obj)
    {
        Class<?> clazz = obj.getClass();
        Field field = getIdentifierField(clazz);
        if (field == null) return null;
        try {
            return field.get(obj).toString();
        } catch (Exception ignored) { return null; }
    }
    public static @Nullable XmlIdentifier getIdentifier(Class<?> clazz)
    {
        Field field = getIdentifierField(clazz);
        if (field == null) return null;
        return field.getAnnotation(XmlIdentifier.class);
    }
    public static @Nullable String getIdentifierValue(Class<?> clazz)
    {
        Field field = getIdentifierField(clazz);
        if (field == null) return null;
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            return field.get(instance).toString();
        } catch (Exception ignored) { return null; }
    }
    public static Field[] getAllFields(Class<?> clazz)
    {
        List<Field> fields = new ArrayList<>(List.of(clazz.getDeclaredFields()));
        Class<?> superclass = clazz.getSuperclass();
        if (superclass == null) return fields.toArray(Field[]::new);
        Class<?>[] interfaces = superclass.getInterfaces();
        for (Class<?> i : interfaces)
            fields.addAll(List.of(i.getDeclaredFields()));
        while (superclass != null) {
            fields.addAll(List.of(superclass.getDeclaredFields()));
            interfaces = superclass.getInterfaces();
            for (Class<?> i : interfaces)
                fields.addAll(List.of(i.getDeclaredFields()));
            superclass = superclass.getSuperclass();
        }
        return fields.toArray(Field[]::new);
    }
    public static boolean isKnownDataType(Class<?> clazz)
    {
        return clazz.isPrimitive()
                || clazz == String.class
                || clazz == Number.class
                ||  clazz == Boolean.class
                ||  clazz == Character.class
                ||  clazz == Byte.class
                ||  clazz == Short.class
                ||  clazz == Integer.class
                ||  clazz == Long.class
                ||  clazz == Float.class
                ||  clazz == Double.class
                ||  clazz == Date.class;
    }

    public static Object decompileKnownTypes(Class<?> clazz, Element fieldElement)
    {
        String value = fieldElement.getAttribute("value");
        if (clazz == String.class)
            return value;
        else if (clazz == Number.class || clazz == Double.class || clazz == double.class)
            return Double.parseDouble(value);
        else if (clazz == Boolean.class || clazz == boolean.class)
            return Boolean.parseBoolean(value);
        else if (clazz == Character.class || clazz == char.class)
            return value.isEmpty() ? '\0' : value.charAt(0);
        else if (clazz == Byte.class || clazz == byte.class)
            return Byte.parseByte(value);
        else if (clazz == Short.class || clazz == short.class)
            return Short.parseShort(value);
        else if (clazz == Integer.class || clazz == int.class)
            return Integer.parseInt(value);
        else if (clazz == Long.class || clazz == long.class)
            return Long.parseLong(value);
        else if (clazz == Float.class || clazz == float.class)
            return Float.parseFloat(value);
        else if (clazz == Date.class)
            try{
                return java.text.DateFormat.getInstance().parse(value);
            }catch (Exception e){e.printStackTrace(); throw new IllegalArgumentException("Failed to parse date with value "+value+" for field "+fieldElement.getTagName());}
        else
            throw new IllegalArgumentException("Class "+clazz.getSimpleName()+" is not a known data type and cannot be decompiled by this method.");
    }

    public static String getDefaultValue(Class<?> clazz)
    {
        if (clazz == String.class)
            return "ExampleString";
        else if (clazz == Number.class || clazz == Double.class || clazz == double.class)
            return "0.0";
        else if (clazz == Boolean.class || clazz == boolean.class)
            return "false";
        else if (clazz == Character.class || clazz == char.class)
            return "A";
        else if (clazz == Byte.class || clazz == byte.class)
            return "0";
        else if (clazz == Short.class || clazz == short.class)
            return "0";
        else if (clazz == Integer.class || clazz == int.class)
            return "0";
        else if (clazz == Long.class || clazz == long.class)
            return "0";
        else if (clazz == Float.class || clazz == float.class)
            return "0.0";
        else if (clazz == Date.class)
            return java.text.DateFormat.getInstance().format(new Date());
        else
            throw new IllegalArgumentException("Class "+clazz.getSimpleName()+" is not a known data type and cannot be decompiled by this method.");
    }
}
