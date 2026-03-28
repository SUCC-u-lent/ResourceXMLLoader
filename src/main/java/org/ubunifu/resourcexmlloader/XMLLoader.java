package org.ubunifu.resourcexmlloader;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.parsers.*;

import org.ubunifu.resourcexmlloader.WeakIdentityHashMap;
import org.ubunifu.resourcexmlloader.XMLMetadata;
import org.ubunifu.resourcexmlloader.annotations.XMLDataPath;
import org.ubunifu.resourcexmlloader.annotations.XMLExcludeField;
import org.ubunifu.resourcexmlloader.embeddedcompilers.EnumHandler;
import org.ubunifu.resourcexmlloader.embeddedcompilers.PrimitiveHandler;
import org.ubunifu.resourcexmlloader.interfaces.XMLFieldHandler;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

public class XMLLoader {

    private final Set<XMLFieldHandler> xmlFieldHandlers = new HashSet<>();

    // Cache: class -> (filename -> metadata)
    private final Map<Class<?>, Map<String, XMLMetadata>> classFileCache = new HashMap<>();

    private XMLLoader(Builder builder) {
        xmlFieldHandlers.addAll(builder.fieldHandlers);
    }

    public static class Builder {
        private final Set<XMLFieldHandler> fieldHandlers = new HashSet<>();
        public Builder()
        {
            this.fieldHandlers.addAll(List.of(
                    new PrimitiveHandler(),
                    new EnumHandler()
            ));
        }

        public Builder addFieldHandlers(XMLFieldHandler... handlers) {
            fieldHandlers.addAll(Arrays.asList(handlers));
            return this;
        }

        public XMLLoader build() {
            return new XMLLoader(this);
        }
    }

    // -------------------- Public API --------------------

    /**
     * Reloads the loader cache, typically unneeded but can be useful in some niche cases.
     */
    public void reload()
    {
        Class<?>[] allClasses = this.classFileCache.keySet().toArray(Class[]::new);
        this.classFileCache.clear();
        for (Class<?> clazz : allClasses) {
            Map<String, XMLMetadata> fileMap = classFileCache.computeIfAbsent(clazz, _ -> new HashMap<>());
            List<XMLMetadata> metadataList = decompileAllMetadata(clazz);
            for (XMLMetadata meta : metadataList) {
                fileMap.put(
                        meta.path()
                                .toFile()
                                .getName()
                                .replace(".xml",""),
                        meta
                );
            }
        }
    }

    /**
     * Gets the path to the object with the specified class
     * @param clazz The class.
     * @param obj The object to find the path for.
     * @return The relative path to the item
     */
    public String getPath(Class<?> clazz, Object obj)
    {
        return getMetadata(clazz)
                .stream()
                .filter(m -> m.instance().equals(obj))
                .findFirst()
                .map(m -> m.path()
                        .toAbsolutePath()
                        .toString()
                        .replace(getResourceDir(clazz).getAbsolutePath(),"")
                )
                .orElseThrow(() -> new IllegalArgumentException("Object not found in metadata for class: " + clazz.getName()));
    }

    /**
     * Gets the path to the specified object
     * @param obj The object to get the path for
     * @return The relative path to it.
     * @param <T> THe type to use.
     */
    public <T> String getPath(T obj)
    {
        return getMetadata(obj.getClass())
                .stream()
                .filter(m -> m.instance().equals(obj))
                .findFirst()
                .map(m -> m.path()
                        .toAbsolutePath()
                        .toString()
                        .replace(getResourceDir(obj.getClass()).getAbsolutePath(),""))
                .orElseThrow(() -> new IllegalArgumentException("Object not found in metadata for class: " + obj.getClass().getName()));
    }

    /**
     * Get the object instance for a class + filename
     * @param clazz The class.
     * @param fieldName The relative file name
     * @return The object instance.
     */
    public Object get(Class<?> clazz, String fieldName) {
        return getMetadata(clazz,fieldName).instance();
    }

    /**
     * Gets an array of object instances that match the class
     * @param clazz The class to get the instances of
     * @return An array of instances that are of the specified class.
     */
    public Object[] get(Class<?> clazz)
    {
        return getMetadata(clazz).toArray(Object[]::new);
    }

    /**
     * Gets the object instance for a class + filename, cast to the specified type. Will throw a ClassCastException if the object cannot be cast to the specified type.
     * @param clazz The class.
     * @param fieldName The relative file name.
     * @return The instance of the {@code <T>} object.
     * @param <T> The class to convert to
     * @throws ClassCastException Thrown when the specified file cannot be converted to the specified type.
     */
    public <T> T getAs(Class<T> clazz, String fieldName) throws ClassCastException
    {
        return clazz.cast(get(clazz, fieldName));
    }

    /**
     * Gets the object instances for a class, cast to the specified type. Will throw a ClassCastException if any of the objects cannot be cast to the specified type.
     * @param clazz The class.
     * @return A collection of instances of the {@code <T>} objects.
     * @param <T> The class to convert to
     */
    public <T> Collection<T> getAs(Class<T> clazz)
    {
        return Arrays.stream(get(clazz))
                .filter(Objects::nonNull)
                .filter(o->{
                    try{
                        clazz.cast(o);
                        return true;
                    }catch(Exception ignored){}
                    return false;
                })
                .map(clazz::cast)
                .toList();
    }

    /** Get metadata for a class + filename */
    public XMLMetadata getMetadata(Class<?> clazz, String filename) {
        Map<String, XMLMetadata> fileMap = classFileCache.computeIfAbsent(clazz, c -> new HashMap<>());

        if (fileMap.containsKey(filename)) {
            return fileMap.get(filename);
        }

        // Not in cache → decompile and store
        XMLMetadata metadata = decompileMetadata(clazz, filename);
        fileMap.put(filename, metadata);
        return metadata;
    }

    /** Get all metadata for a class (all files) */
    public List<XMLMetadata> getMetadata(Class<?> clazz) {
        Map<String, XMLMetadata> fileMap = classFileCache.computeIfAbsent(clazz, c -> new HashMap<>());
        if (!fileMap.isEmpty()) return new ArrayList<>(fileMap.values());

        // Decompile all files in class resource directory
        List<XMLMetadata> metadataList = decompileAllMetadata(clazz);
        for (XMLMetadata meta : metadataList) {
            fileMap.put(
                    meta.path()
                    .toFile()
                    .getName()
                    .replace(".xml",""),
                    meta
            );
        }
        return metadataList;
    }

    // -------------------- Decompilation --------------------

    /** Decompile a single file for a class */
    private XMLMetadata decompileMetadata(Class<?> clazz, String filename) {
        File file = getFileForClass(clazz, filename);
        Object instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate class: " + clazz, e);
        }
        return readXMLMetadata(clazz, file, instance);
    }

    /** Decompile all files in class resource directory */
    private List<XMLMetadata> decompileAllMetadata(Class<?> clazz) {
        File dir = getResourceDir(clazz);
        File[] files = traverseFiles(dir);
        if (files.length == 0) throw new IllegalArgumentException("No files found for class: " + clazz.getName());

        return Arrays.stream(files)
                .map(f -> {
                    try {
                        Object instance = clazz.getDeclaredConstructor().newInstance();
                        return readXMLMetadata(clazz, f, instance);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to decompile file: " + f, e);
                    }
                })
                .collect(Collectors.toList());
    }

    /** Read XML into metadata */
    private XMLMetadata readXMLMetadata(Class<?> clazz, File file, Object instance) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);

            Element root = document.getElementsByTagName("root").item(0) instanceof Element e ? e : document.getDocumentElement();

            Field[] fields = Arrays.stream(getFields(clazz))
                    .filter(f -> !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()))
                    .filter(f -> !f.isAnnotationPresent(XMLExcludeField.class))
                    .toArray(Field[]::new);

            for (Field field : fields) {
                field.setAccessible(true);
                Element fieldElement = document.getElementsByTagName(field.getName()).item(0) instanceof Element e ? e : null;
                if (fieldElement == null) continue;

                Class<?> type = field.getType();
                Optional<XMLFieldHandler> handler = xmlFieldHandlers.stream()
                        .filter(h -> h.accepts(type))
                        .findFirst();
                if (handler.isEmpty())
                    throw new IllegalStateException(String.format("No XMLFieldHandler found for field %s of type %s", field.getName(), type));
                Object value = handler.get().decompileField(clazz, document, root, fieldElement, type, field);
                field.set(instance, value);
            }

            return new XMLMetadata(root, file.toPath(), instance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read XML for file: " + file, e);
        }
    }

    // -------------------- Utilities --------------------

    private static File getFileForClass(Class<?> clazz, String filename) {
        File dir = getResourceDir(clazz);
        String resolvedFilename = filename;
        if (resolvedFilename != null && !resolvedFilename.endsWith(".xml")) {
            resolvedFilename = resolvedFilename + ".xml";
        }
        File file = new File(dir, resolvedFilename);
        if (!file.exists()) throw new IllegalArgumentException("File not found: " + file);
        return file;
    }

    private static File getResourceDir(Class<?> clazz) {
        Path base = Path.of("src/main/resources");
        if (clazz.isAnnotationPresent(XMLDataPath.class)) {
            base = base.resolve(clazz.getAnnotation(XMLDataPath.class).value());
        }
        return base.toFile();
    }

    private static File[] traverseFiles(File file) {
        Set<File> files = new HashSet<>();
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) {
                for (File f : list) {
                    files.add(f);
                    files.addAll(Arrays.asList(traverseFiles(f)));
                }
            }
        }
        return files.stream().filter(File::isFile).toArray(File[]::new);
    }

    private static Field[] getFields(Class<?> clazz) {
        Set<Field> fields = new HashSet<>(List.of(clazz.getDeclaredFields()));
        if (clazz.getSuperclass() != null) fields.addAll(List.of(getFields(clazz.getSuperclass())));
        for (Class<?> iface : clazz.getInterfaces()) fields.addAll(List.of(getFields(iface)));
        return fields.toArray(Field[]::new);
    }
}