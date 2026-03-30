package org.ubunifu.resourcexmlloader;

import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.parsers.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ubunifu.resourcexmlloader.annotations.XMLDataPath;
import org.ubunifu.resourcexmlloader.annotations.XMLExcludeField;
import org.ubunifu.resourcexmlloader.embeddedcompilers.EnumHandler;
import org.ubunifu.resourcexmlloader.embeddedcompilers.PrimitiveHandler;
import org.ubunifu.resourcexmlloader.interfaces.XMLFieldHandler;
import org.w3c.dom.*;

public class XMLLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(XMLLoader.class);
    private final List<XMLFieldHandler> xmlFieldHandlers = new ArrayList<>();

    // Single cache: (class + file key) -> entry(metadata + identity weak object reference).
    private final Map<String, List<CacheEntry>> cache = new HashMap<>();
    final boolean enabledLogging;

    private static final class CacheEntry
    {
        private final Class<?> clazz;
        private final String fileKey;
        private final XMLMetadata metadata;
        private WeakIdentityHashMap.IdentityWeakReference<Object> instanceRef;

        private CacheEntry(Class<?> clazz, String fileKey, XMLMetadata metadata, Object instance) {
            this.clazz = clazz;
            this.fileKey = fileKey;
            this.metadata = metadata;
            this.instanceRef = new WeakIdentityHashMap.IdentityWeakReference<>(instance);
        }
    }

    private XMLLoader(Builder builder) {
        xmlFieldHandlers.addAll(builder.fieldHandlers);
        this.enabledLogging = builder.enabledLogging;
        xmlFieldHandlers.forEach(h -> h.setLoggingEnabled(this.enabledLogging));
        logInfo("XMLLoader initialized with {} handler(s). Logging enabled={}", xmlFieldHandlers.size(), enabledLogging);
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

    private void logWarn(String message, Object... args) {
        if (enabledLogging) {
            LOGGER.warn(message, args);
        }
    }
    private void logError(String message, Object... args) {
        if (enabledLogging) {
            LOGGER.error(message, args);
        }
    }

    public static class Builder {
        private final Set<XMLFieldHandler> fieldHandlers = new HashSet<>();
        private boolean enabledLogging;
        public Builder()
        {
        }

        public Builder includeEmbeddedHandlers()
        {
            this.fieldHandlers.addAll(List.of(
                    new PrimitiveHandler(),
                    new EnumHandler()
            ));
            return this;
        }
        
        public Builder addFieldHandlers(XMLFieldHandler... handlers) {
            fieldHandlers.addAll(Arrays.asList(handlers));
            return this;
        }
        public Builder useLogging()
        {
            this.enabledLogging = true;
            return this;
        }

        public XMLLoader build() {
            return new XMLLoader(this);
        }
    }

    // -------------------- Public API --------------------

    /**
     * Flush the cache, it does not reload the cache requiring {@link XMLLoader#reload()} to be called before any entries can be accessed again, useful when the program is expected to undergo a long downtime and the system resources is better used somewhere else.
     */
    public void flush()
    {
        this.cache.clear();
    }
    /**
     * Reloads the loader cache, typically unneeded but can be useful in some niche cases.
     */
    public void reload()
    {
        logInfo("Reload requested. Current cache size={}", this.cache.size());
        Class<?>[] allClasses = this.cache.keySet()
                .stream()
                .distinct()
                .map(f->{
                    try{return Class.forName(f);}catch(Exception e){ logError("An error occured while reloading.",e); return null;}
                })
                .filter(Objects::nonNull)
                .toArray(Class[]::new);
        this.cache.clear();
        logDebug("Cache cleared. Recompiling {} class(es)", allClasses.length);
        for (Class<?> clazz : allClasses) {
            List<CacheEntry> entries = decompileAllEntries(clazz);
            for (CacheEntry entry : entries) {
                this.cache
                    .computeIfAbsent(entry.clazz.getName(), k -> new ArrayList<>())
                     .add(entry);
            }
        }
        logInfo("Reload completed. Cache size now={}", this.cache.size());
    }

    /**
     * Gets the path to the object with the specified class
     * @param clazz The class.
     * @param obj The object to find the path for.
     * @return The relative path to the item
     */
    public String getPath(Class<?> clazz, Object obj)
    {
        logDebug("Resolving resource path for class={} instance={}", clazz.getName(), obj);
        if (!clazz.isInstance(obj)) {
            throw new IllegalArgumentException("Object is not an instance of class: " + clazz.getName());
        }
        CacheEntry entry = getAllEntries(clazz)
                .stream()
                .filter(e -> {
                    Object loaded = resolveInstance(clazz, e);
                    return matchesInstance(loaded, obj);
                })
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Object not found in metadata for class: " + clazz.getName()));
        return toRelativeResourcePath(clazz, entry.metadata.path());
    }

    /**
     * Gets the path to the specified object
     * @param obj The object to get the path for
     * @return The relative path to it.
     * @param <T> THe type to use.
     */
    public <T> String getPath(T obj)
    {
        return getPath(obj.getClass(), obj);
    }

    /**
     * Get the object instance for a class + filename
     * @param clazz The class.
     * @param fieldName The relative file name
     * @return The object instance.
     */
    public Object get(Class<?> clazz, String fieldName) {
        logDebug("Fetching entry for class={} file={}", clazz.getName(), fieldName);
        return resolveInstance(clazz, getOrLoadEntry(clazz, fieldName));
    }

    /**
     * Gets an array of object instances that match the class
     * @param clazz The class to get the instances of
     * @return An array of instances that are of the specified class.
     */
    public Object[] get(Class<?> clazz)
    {
        logDebug("Fetching all entries for class={}", clazz.getName());
        return getAllEntries(clazz)
                .stream()
                .map(e -> resolveInstance(clazz, e))
                .toArray(Object[]::new);
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
                    }catch(Exception e){logError("An error occured while getting "+clazz.getSimpleName(),e);}
                    return false;
                })
                .map(clazz::cast)
                .toList();
    }

    /** Get metadata for a class + filename */
    public XMLMetadata getMetadata(Class<?> clazz, String filename) {
        return getOrLoadEntry(clazz, filename).metadata;
    }

    /** Get all metadata for a class (all files) */
    public List<XMLMetadata> getMetadata(Class<?> clazz) {
        return getAllEntries(clazz)
                .stream()
                .map(e -> e.metadata)
                .toList();
    }
    public XMLMetadata getMetadata(Class<?> clazz,Object instance) {
        if (!clazz.isInstance(instance)) {
            return null;
        }
        return getAllEntries(clazz)
                .stream()
                .filter(e -> {
                    Object loaded = resolveInstance(clazz, e);
                    return matchesInstance(loaded, instance);
                })
                .map(e -> e.metadata)
                .findFirst()
                .orElse(null);
    }

    // -------------------- Decompilation --------------------

    /** Decompile a single file for a class */
    private CacheEntry decompileMetadata(Class<?> clazz, String filename) {
        File file = getFileForClass(clazz, filename);
        logDebug("Decompiling metadata for class={} file={}", clazz.getName(), file.getAbsolutePath());
        Object instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logError("Failed to instantiate class={} for file={} reason={}", clazz.getName(), file.getAbsolutePath(), e.getMessage());
            throw new RuntimeException("Cannot instantiate class: " + clazz, e);
        }
        XMLMetadata metadata = readXMLMetadata(clazz, file, instance);
        logDebug("Decompiled class={} file={} fileKey={}", clazz.getName(), file.getName(), toCacheKey(file.toPath()));
        return new CacheEntry(clazz, toCacheKey(file.toPath()), metadata, instance);
    }

    /** Decompile all files in class resource directory */
    private List<CacheEntry> decompileAllEntries(Class<?> clazz) {
        File dir = getResourceDir(clazz);
        logDebug("Scanning class={} resource directory={}", clazz.getName(), dir.getAbsolutePath());
        File[] files = Arrays.stream(traverseFiles(dir))
                .filter(f -> f.getName().endsWith(".xml"))
                .filter(f -> !f.getName().equalsIgnoreCase("template.xml"))
                .toArray(File[]::new);
        if (files.length == 0) throw new IllegalArgumentException("No files found for class: " + clazz.getName());

        return Arrays.stream(files)
                .map(f -> {
                    try {
                        logDebug("Decompiling discovered file={} for class={}", f.getAbsolutePath(), clazz.getName());
                        Object instance = clazz.getDeclaredConstructor().newInstance();
                        XMLMetadata metadata = readXMLMetadata(clazz, f, instance);
                        return new CacheEntry(clazz, toCacheKey(f.toPath()), metadata, instance);
                    } catch (Exception e) {
                        logError("Failed to decompile file={} for class={} reason={}", f.getAbsolutePath(), clazz.getName(), e.getMessage());
                        throw new RuntimeException("Failed to decompile file: " + f, e);
                    }
                })
                .collect(Collectors.toList());
    }

    /** Read XML into metadata */
    private XMLMetadata readXMLMetadata(Class<?> clazz, File file, Object instance) {
        try {
            logDebug("Reading XML metadata class={} file={}", clazz.getName(), file.getAbsolutePath());
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);

            Element root = document.getElementsByTagName("root").item(0) instanceof Element e ? e : document.getDocumentElement();
            String typeAttr = root.getAttribute("type");
            if (typeAttr.isEmpty()) return null;
            if (!clazz.getName().equals(typeAttr)) return null;

            Field[] fields = Arrays.stream(getFields(clazz))
                    .filter(f -> !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()))
                    .filter(f -> !f.isAnnotationPresent(XMLExcludeField.class))
                    .toArray(Field[]::new);

            for (Field field : fields) {
                field.setAccessible(true);
                Element fieldElement = root.getElementsByTagName(field.getName()).item(0) instanceof Element e ? e : null;
                if (fieldElement == null) {
                    logDebug("Skipping field={} because XML element was not found", field.getName());
                    continue;
                }

                Class<?> type = field.getType();
                Optional<XMLFieldHandler> handler = xmlFieldHandlers.stream()
                        .filter(h -> h.accepts(type))
                        .findFirst();
                if (handler.isEmpty())
                    throw new IllegalStateException(String.format("No XMLFieldHandler found for field %s of type %s", field.getName(), type));
                logDebug("Using handler={} for field={} type={} class={}", handler.get().getClass().getSimpleName(), field.getName(), type.getName(), clazz.getName());
                Object value = handler.get().decompileField(clazz, document, root, fieldElement, type, field);
                field.set(instance, value);
                logDebug("Hydrated field={} with valueType={}", field.getName(), value == null ? "null" : value.getClass().getName());
            }

            return new XMLMetadata(root, file.toPath());
        } catch (Exception e) {
            logError("Failed to read XML metadata class={} file={} reason={}", clazz.getName(), file.getAbsolutePath(), e.getMessage());
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
    private static File getResourceDir(Class<?> clazz) {
        Path base = getResourcePath(clazz);
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

    private static String toCacheKey(String filename) {
        return filename.endsWith(".xml") ? filename.substring(0, filename.length() - 4) : filename;
    }

    private static String fromCacheKeyToFilename(String cacheKey) {
        return cacheKey.endsWith(".xml") ? cacheKey : cacheKey + ".xml";
    }

    private static String toCacheKey(Path path) {
        return path.getFileName().toString().replace(".xml", "");
    }

    private CacheEntry getOrLoadEntry(Class<?> clazz, String filename) {
        String fileKey = toCacheKey(filename);
        CacheEntry cached = cache.get(clazz.getName())
                .stream()
                .filter(e -> e.fileKey.equalsIgnoreCase(fileKey))
                .findFirst()
                .orElse(null);
        if (cached != null) {
            logDebug("Cache hit for class={} requestedFile={} storedFileKey={}", clazz.getName(), filename, cached.fileKey);
            return cached;
        }

        logDebug("Cache miss for class={} file={}. Loading metadata.", clazz.getName(), filename);

        CacheEntry loaded = decompileMetadata(clazz, fromCacheKeyToFilename(fileKey));
        this.cache
                .computeIfAbsent(clazz.getName(), k -> new ArrayList<>())
                .add(loaded);
        return loaded;
    }

    private List<CacheEntry> getAllEntries(Class<?> clazz) {
        List<CacheEntry> entries = getEntries(clazz);
        if (!entries.isEmpty()) {
            logDebug("Returning {} cached entries for class={}", entries.size(), clazz.getName());
            return entries;
        }

        List<CacheEntry> loaded = decompileAllEntries(clazz);
        for (CacheEntry entry : loaded) {
            this.cache
                    .computeIfAbsent(entry.clazz.getName(), k -> new ArrayList<>())
                    .add(entry);
        }
        logDebug("Loaded {} entries from disk for class={}", loaded.size(), clazz.getName());
        return loaded;
    }

    private List<CacheEntry> getEntries(Class<?> clazz) {
        return cache.entrySet()
                .stream()
                .filter(e -> e.getKey().equalsIgnoreCase(clazz.getName()))
                .map(Map.Entry::getValue)
                .flatMap(List::stream)
                .toList();
    }

    private Object resolveInstance(Class<?> clazz, CacheEntry entry) {
        if (entry.clazz != clazz) {
            throw new IllegalStateException("Cache entry class mismatch. Expected: " + clazz.getName() + ", entry: " + entry.clazz.getName());
        }
        Object instance = entry.instanceRef.get();
        if (instance != null && entry.clazz.isInstance(instance)) {
            logDebug("Resolved live weak-reference instance for class={} fileKey={}", clazz.getName(), entry.fileKey);
            return instance;
        }

        try {
            logDebug("Weak reference collected, rehydrating class={} from {}", clazz.getName(), entry.metadata.path());
            Object recreated = entry.clazz.getDeclaredConstructor().newInstance();
            // Rehydrate fields from XML file without storing strong references in metadata.
            readXMLMetadata(entry.clazz, entry.metadata.path().toFile(), recreated);
            entry.instanceRef = new WeakIdentityHashMap.IdentityWeakReference<>(recreated);
            return recreated;
        } catch (Exception e) {
            logError("Failed rehydration for class={} path={} reason={}", clazz.getName(), entry.metadata.path(), e.getMessage());
            throw new RuntimeException("Cannot rehydrate instance for metadata path: " + entry.metadata.path(), e);
        }
    }

    private static boolean matchesInstance(Object loaded, Object target) {
        if (loaded == target) {
            return true;
        }
        if (loaded == null || target == null) {
            return false;
        }
        if (loaded.getClass() != target.getClass()) {
            return false;
        }
        return loaded.equals(target);
    }


    private static String toRelativeResourcePath(Class<?> clazz, Path absolutePath) {
        return absolutePath
                .toAbsolutePath()
                .toString()
                .replace(getResourceDir(clazz).getAbsolutePath(), "");
    }
}

