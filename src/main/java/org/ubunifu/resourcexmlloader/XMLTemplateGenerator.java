package org.ubunifu.resourcexmlloader;

import org.ubunifu.resourcexmlloader.annotations.XMLComment;
import org.ubunifu.resourcexmlloader.annotations.XMLDataPath;
import org.ubunifu.resourcexmlloader.annotations.XMLExcludeField;
import org.ubunifu.resourcexmlloader.embeddedcompilers.EnumHandler;
import org.ubunifu.resourcexmlloader.embeddedcompilers.PrimitiveHandler;
import org.ubunifu.resourcexmlloader.interfaces.XMLFieldHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(XMLTemplateGenerator.class);
    private final List<XMLFieldHandler> xmlFieldHandlers = new ArrayList<>();
    final boolean enabledLogging;
    private XMLTemplateGenerator(Builder builder)
    {
        xmlFieldHandlers.addAll(builder.fieldHandlers);
        this.enabledLogging = builder.enabledLogging;
        xmlFieldHandlers.forEach(h -> h.setLoggingEnabled(this.enabledLogging));
        logInfo("XMLTemplateGenerator initialized with {} handler(s). Logging enabled={}", xmlFieldHandlers.size(), enabledLogging);
    }

    private void logInfo(String message, Object... args) {
        if (enabledLogging) {
            LOGGER.info(message, args);
        }
    }

    private void logDebug(String message, Object... args) {
        if (enabledLogging) {
            LOGGER.debug(message, args);
        }
    }
    public static class Builder
    {
        private final Set<XMLFieldHandler> fieldHandlers = new HashSet<>();
        boolean enabledLogging = false;
        public Builder()
        {
        }
        public Builder includeDefaultHandlers()
        {
            this.fieldHandlers.addAll(List.of(
                    new PrimitiveHandler(),
                    new EnumHandler()
            ));
            return this;
        }
        public Builder useLogging()
        {
            this.enabledLogging = true;
            return this;
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
        logInfo("Generating template from Path for class={} requestedPath={} resolvedPath={}", clazz.getName(), path, outputPath);

        if (!outputPath.startsWith(resourcePath)) {
            throw new IllegalArgumentException("Template output path must be within resource directory: " + resourcePath);
        }

        String fileName = outputPath.getFileName().toString();
        if (!fileName.endsWith(".xml")) fileName = fileName + ".xml";
        Path parent = outputPath.getParent();
        String dataPath = parent == null ? "" : resourcePath.relativize(parent).toString();
        logDebug("Resolved template generation target dataPath={} fileName={} for class={}", dataPath, fileName, clazz.getName());
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
        logInfo("Generating default template for class={} dataPath={}", clazz.getName(), dataPath);
        generateTemplate(clazz,dataPath,"template.xml");
    }
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void generateTemplate(Class<?> clazz, String path, String fileName) throws IOException, ParserConfigurationException, TransformerException {
        if (!fileName.endsWith(".xml")) fileName = fileName + ".xml";
        Path resourcePath = getResourcePath(clazz);
        Path targetDirectory = path == null || path.isBlank() ? resourcePath : resourcePath.resolve(path);
        Path fullPath = targetDirectory.resolve(fileName).normalize();
        logInfo("Generating template for class={} at {}", clazz.getName(), fullPath.toAbsolutePath());
        fullPath.getParent().toFile().mkdirs();
        fullPath.toFile().createNewFile();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        if (clazz.isAnnotationPresent(XMLComment.class))
            document.appendChild(document.createComment(clazz.getAnnotation(XMLComment.class).value()));

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
        logInfo("Template generation complete for class={} output={}", clazz.getName(), fullPath.toAbsolutePath());
    }

    private void writeTemplate(Class<?> clazz, Path path, Document document, Element rootElement)
    {
        logDebug("Writing template fields for class={} to path={}", clazz.getName(), path.toAbsolutePath());
        Field[] fields = Arrays.stream(getFields(clazz))
                .filter(f-> !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()) && !f.isAnnotationPresent(XMLExcludeField.class))
                .toArray(Field[]::new);
        logDebug("Detected {} writable field(s) for class={}", fields.length, clazz.getName());
        for (Field field : fields) {
            field.setAccessible(true);
            XMLComment comment = null;
            if (field.isAnnotationPresent(XMLComment.class)) { comment = field.getAnnotation(XMLComment.class); }
            else if (field.getType().isAnnotationPresent(XMLComment.class)) { comment = field.getType().getAnnotation(XMLComment.class); }

            if (comment != null && comment.placeBefore())
                rootElement.appendChild(document.createComment(comment.value()));
            Optional<XMLFieldHandler> fieldHandler = xmlFieldHandlers.stream()
                    .filter(h->h.accepts(field.getType()))
                    .findFirst();
            if (fieldHandler.isEmpty())
                throw new IllegalStateException(String.format("No XMLFieldHandler found for field %s", field.getName()));
            logDebug("Using handler={} for template field={} type={} class={}", fieldHandler.get().getClass().getSimpleName(), field.getName(), field.getType().getName(), clazz.getName());
            Element fieldElement = document.createElement(field.getName());
            fieldHandler.get().handleTemplateField(clazz, document, rootElement, fieldElement, field.getType(), field);
            rootElement.appendChild(fieldElement);
            if (comment != null && !comment.placeBefore())
                rootElement.appendChild(document.createComment(comment.value()));
        }
        logDebug("Finished writing template fields for class={}", clazz.getName());
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
