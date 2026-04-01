package org.ubunifu.resourcexmlloader;

import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;
import org.ubunifu.resourcexmlloader.embeddedcompilers.EnumHandler;
import org.ubunifu.resourcexmlloader.embeddedcompilers.PrimitiveHandler;
import org.ubunifu.resourcexmlloader.interfaces.XMLFieldHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.print.Doc;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class XMLReader
{
    public record LoaderSettings(String rootName,XMLFieldHandler[] handlers, @Nullable Level level){}
    public static Class<?> getClassOfFile(File file) throws ParserConfigurationException, IOException, SAXException, ClassNotFoundException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);
        Element root = document.getElementsByTagName("root").item(0) instanceof Element e ? e : document.getDocumentElement();
        String type = root.getAttribute("type");
        return Class.forName(type);
    }
    public static Object readXML(Class<?> clazz, File file) throws ParserConfigurationException, IOException, InvocationTargetException, SAXException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return readXML(clazz,file,new LoaderSettings(
                "root",
                new XMLFieldHandler[]
                {
                        new EnumHandler(),
                        new PrimitiveHandler()
                },null));
    }
    public static Object readXML(Class<?> clazz,File file, LoaderSettings settings) throws ParserConfigurationException, IOException, SAXException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);

        Element root = document.getElementsByTagName(settings.rootName()).item(0) instanceof Element e ? e : document.getDocumentElement();
        String fileType = root.getAttribute("type");
        if (!clazz.getName().equalsIgnoreCase(fileType)) return null;
        Field[] fields = getAllFields(clazz);
        Object instance = clazz.getDeclaredConstructor().newInstance();

        for (Field field : fields)
        {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Optional<XMLFieldHandler> handlerOpt = Arrays.stream(settings.handlers())
                    .filter(h->h.accepts(field.getType()))
                    .findFirst();
            if (handlerOpt.isEmpty())
            {
                if (settings.level() != null)
                    System.out.println("["+settings.level().name()+"] No handler found for field "+field.getName()+" of type "+field.getType().getName());
                throw new RuntimeException(
                        String.format(
                                "No handler could be identified to read field: %s %s; The class %s thus cannot be deserialized",
                                field.getType().getSimpleName(),
                                field.getName(),
                                clazz.getSimpleName()
                        )
                );
            }
            NodeList n = document.getElementsByTagName(field.getName());
            if (n.getLength() != 1)
                throw new RuntimeException(
                        String.format(
                                "Expected exactly one XML element with the name %s within %s, but found %d",
                                field.getName(),
                                file.getName()+".xml",
                                n.getLength()
                        )
                );
            if (!(n.item(0) instanceof Element e))
                throw new RuntimeException(
                        String.format(
                                "Expected item to be of Element, but was not. Cannot parse %s",
                                clazz.getSimpleName()
                        )
                );
            Object val = handlerOpt.get()
                    .decompileField(
                            clazz,
                            document,
                            root,
                            e,
                            field.getType(),
                            field
                    );
            field.set(instance,val);
        }
        return instance;
    }
    private static Field[] getAllFields(Class<?> clazz)
    {
        Set<Field> fieldSet = new HashSet<>();
        Field[] fields = clazz.getDeclaredFields();
        Class<?>[] interfaces = clazz.getInterfaces();
        fieldSet.addAll(Set.of(fields));
        fieldSet.addAll(Arrays.stream(interfaces).flatMap(l-> Arrays.stream(l.getDeclaredFields())).toList());
        if (clazz.getSuperclass() == null) return fieldSet.toArray(Field[]::new);
        fieldSet.addAll(Set.of(getAllFields(clazz.getSuperclass())));
        return fieldSet.toArray(Field[]::new);
    }
}
