package org.ubunifu.resourcexmlloader;

import org.ubunifu.resourcexmlloader.annotations.XMLDataPath;
import org.ubunifu.resourcexmlloader.annotations.XMLExcludeField;
import org.ubunifu.resourcexmlloader.embeddedcompilers.EnumHandler;
import org.ubunifu.resourcexmlloader.embeddedcompilers.PrimitiveHandler;
import org.ubunifu.resourcexmlloader.interfaces.XMLFieldHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class XMLTemplateGenerator {
    private final Set<XMLFieldHandler> xmlFieldHandlers = new HashSet<>();
    private XMLTemplateGenerator(Builder builder)
    {
        xmlFieldHandlers.addAll(builder.fieldHandlers);
    }
    public static class Builder
    {
        private final Set<XMLFieldHandler> fieldHandlers = new HashSet<>();
        public Builder()
        {
            this.fieldHandlers.addAll(List.of(
                    new PrimitiveHandler(),
                    new EnumHandler()
            ));
        }
        public Builder addFieldHandlers(XMLFieldHandler... handlers)
        {
            fieldHandlers.addAll(Arrays.asList(handlers));
            return this;
        }
        public XMLTemplateGenerator build()
        {
            return new XMLTemplateGenerator(this);
        }
    }
    public void generateTemplate(Class<?> clazz, Path path) throws IOException, ParserConfigurationException, TransformerException {
        Path resourcePath = getResourcePath(clazz).toAbsolutePath().normalize();
        Path outputPath = path.isAbsolute() ? path.toAbsolutePath().normalize() : resourcePath.resolve(path).normalize();

        if (!outputPath.startsWith(resourcePath)) {
            throw new IllegalArgumentException("Template output path must be within resource directory: " + resourcePath);
        }

        String fileName = outputPath.getFileName().toString();
        if (!fileName.endsWith(".xml")) fileName = fileName + ".xml";
        Path parent = outputPath.getParent();
        String dataPath = parent == null ? "" : resourcePath.relativize(parent).toString();
        generateTemplate(clazz, dataPath, fileName);
    }


    private static boolean isCompiled(Class<?> clazz)
    {
        try
        {
            String location = clazz
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath();

            return location.endsWith(".jar");
        }
        catch (Exception ignored) {}
        return false;
    }
    private static Path getResourcePath(Class<?> clazz)
    {
        if (isCompiled(clazz))
            return Path.of("resources");
        else
            return Path.of("src/main/resources");
    }
    public void generateTemplate(Class<?> clazz) throws IOException, ParserConfigurationException, TransformerException {
        String dataPath;
        if (clazz.isAnnotationPresent(XMLDataPath.class))
            dataPath = clazz.getAnnotation(XMLDataPath.class).value();
        else
            dataPath = "";
        generateTemplate(clazz,dataPath,"template.xml");
    }
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void generateTemplate(Class<?> clazz, String path, String fileName) throws IOException, ParserConfigurationException, TransformerException {
        if (!fileName.endsWith(".xml")) fileName = fileName + ".xml";
        Path resourcePath = getResourcePath(clazz);
        Path targetDirectory = path == null || path.isBlank() ? resourcePath : resourcePath.resolve(path);
        Path fullPath = targetDirectory.resolve(fileName).normalize();
        fullPath.getParent().toFile().mkdirs();
        fullPath.toFile().createNewFile();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Element rootElement = document.createElement("root");
        document.appendChild(rootElement);
        writeTemplate(clazz, fullPath, document, rootElement);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        try (OutputStream outputStream = Files.newOutputStream(fullPath)) {
            transformer.transform(new DOMSource(document), new StreamResult(outputStream));
        }
    }

    private void writeTemplate(Class<?> clazz, Path path, Document document, Element rootElement)
    {
        Field[] fields = Arrays.stream(getFields(clazz))
                .filter(f-> !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()) && !f.isAnnotationPresent(XMLExcludeField.class))
                .toArray(Field[]::new);
        for (Field field : fields) {
            field.setAccessible(true);
            Optional<XMLFieldHandler> fieldHandler = xmlFieldHandlers.stream()
                    .filter(h->h.accepts(field.getType()))
                    .findFirst();
            if (fieldHandler.isEmpty())
                throw new IllegalStateException(String.format("No XMLFieldHandler found for field %s", field.getName()));
            Element fieldElement = document.createElement(field.getName());
            fieldHandler.get().handleTemplateField(clazz, document, rootElement, fieldElement, field.getType(), field);
            rootElement.appendChild(fieldElement);
        }
    }
    private static Field[] getFields(Class<?> clazz)
    {
        Set<Field> fields = new HashSet<>(List.of(clazz.getDeclaredFields()));
        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> anInterface : interfaces) { fields.addAll(List.of(getFields(anInterface))); }
        if (clazz.getSuperclass() == null) return fields.toArray(Field[]::new);
        fields.addAll(List.of(getFields(clazz.getSuperclass())));
        return fields.toArray(Field[]::new);
    }
}
