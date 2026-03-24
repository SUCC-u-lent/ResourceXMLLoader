package org.resourcexmlloader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class XmlLoaderExtensions {
    public static Field[] getAllFields(Class<?> clazz)
    {
        List<Field> fields = new ArrayList<>(List.of(clazz.getDeclaredFields()));
        Class<?> superclass = clazz.getSuperclass();
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
}
