package org.ubunifu.resourcexmlloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.ubunifu.resourcexmlloader.interfaces.XMLFieldHandler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ResourceXML
{
    static final Logger LOGGER = LoggerFactory.getLogger(ResourceXML.class);
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
    public static Path getResourcePath(Class<?> clazz)
    {
        if (isCompiled(clazz))
            return Path.of("resources");
        else
            return Path.of("src/main/resources");
    }
    public record CacheEntry(
            XMLMetadata metadata,
            WeakReference<Object> weakData,
            int hc
    )
    {
        public boolean isObject(Object obj) {
            return obj.hashCode() == this.hc();
        }
    }
    private void writeLog(Level level, String text, Object... args)
    {
        if (loaderSettings.level() == null) return;
        if (level.ordinal() < loaderSettings.level().ordinal()) return;
        switch (loaderSettings.level()) {
            case ERROR -> LOGGER.error(text, args);
            case WARN -> LOGGER.warn(text, args);
            case DEBUG -> LOGGER.debug(text, args);
            case TRACE -> LOGGER.trace(text, args);
            default -> LOGGER.info(text, args);
        }
    }
    XMLReader.LoaderSettings loaderSettings;
    Set<Class<?>> classes;
    Set<XMLFieldHandler> handlers;
    Map<String,Set<CacheEntry>> cache = new HashMap<>();
    private boolean isInitializing, hasLoaded;
    ResourceXML(Builder builder)
    {
        this.handlers = builder.handlers;
        if (builder.useDefaultHandlers)
            this.handlers.addAll(List.of(
                    new org.ubunifu.resourcexmlloader.embeddedcompilers.EnumHandler(),
                    new org.ubunifu.resourcexmlloader.embeddedcompilers.PrimitiveHandler()
            ));
        this.classes = builder.classes;

        String rootName = builder.settings == null ? "root" : builder.settings.rootName();
        this.loaderSettings = new XMLReader.LoaderSettings(
                rootName,
                this.handlers.toArray(XMLFieldHandler[]::new),
                builder.settings == null ? null : builder.settings.level()
        );
    }

    /**
     * Flushes the internal cache then re-registers everything.
     */
    public void reload()
    {
        if (this.isInitializing) return; // Prevent recursive reload during initialization
        this.isInitializing = true;
        ResourceConstants.ON_CACHE_RELOAD_START.invokeEvent();
        ResourceConstants.ON_CACHE_RELOAD.invokeEvent();
        try {
            this.flush();
            this.register();
        } finally {
            this.isInitializing = false;
            ResourceConstants.ON_CACHE_RELOAD_END.invokeEvent();
        }
    }

    /**
     * Flushes the internal cache to free up resources.
     */
    public void flush()
    {
        this.cache.clear();
        this.hasLoaded = false;
        ResourceConstants.ON_CACHE_FLUSH.invokeEvent();
    }

    /**
     * Adds a new class to the loader, this will trigger a {@link ResourceXML#reload()}
     * @param clazz The class to add, wont be added if it already exists.
     */
    public void addHandledClass(Class<?> clazz)
    { this.classes.add(clazz); this.reload(); }
    private void register()
    {
        if (hasLoaded) return;
        hasLoaded = true;
        ResourceConstants.ON_CACHE_REGISTER_START.invokeEvent();
        Class<?> searchClass = classes.stream().findFirst().orElseThrow();
        Path path = getResourcePath(searchClass);
        if (!path.toFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            path.toFile().mkdirs();
        }
        Set<File> files = getFiles(searchClass, f -> f.isFile() && f.getName().endsWith(".xml"));
        for (File file : files) {
            Class<?> fileClazz;
            try {
                fileClazz = XMLReader.getClassOfFile(file);
            } catch (Exception e) {
                ResourceConstants.ON_RESOURCE_REGISTER_FAILED.invokeEvent(
                        new ResourceConstants.RegisterFailureEvent(null, file.getName(), e)
                );
                if (loaderSettings.level() != null)
                    writeLog(Level.ERROR,"Failed to read file %s, skipping. Error: %s%n", file.getName(), e.getMessage());
                throw new RuntimeException(e);
            }
            if (classes.stream().noneMatch(c->c.getName().equalsIgnoreCase(fileClazz.getName()))) continue; // If this file is not to be registered ignore it.
            this.cache.computeIfAbsent(fileClazz.getName(), k-> new HashSet<>());
            XMLMetadata metadata = XMLMetadata.of(fileClazz, file);
            Object data;
            try {
                data = XMLReader.readXML(fileClazz, file, loaderSettings);
            } catch (Exception e) {
                ResourceConstants.ON_RESOURCE_REGISTER_FAILED.invokeEvent(
                        new ResourceConstants.RegisterFailureEvent(fileClazz, file.getName(), e)
                );
                writeLog(Level.ERROR,"Failed to read file %s, skipping. Error: %s%n", file.getName(), e.getMessage());
                throw new RuntimeException(e);
            }
            if (data == null) throw new IllegalArgumentException("Failed to read file "+file.getName()+", the data was null");
            CacheEntry entry = new CacheEntry(metadata, new WeakReference<>(data),data.hashCode());
            Set<CacheEntry> existingEntries = this.cache.getOrDefault(fileClazz.getName(), new HashSet<>());
            existingEntries.add(entry);
            ResourceConstants.ON_RESOURCE_LOADED.invokeEvent(entry);
            ResourceConstants.ON_RESOURCE_REGISTERED.invokeEvent(
                    new ResourceConstants.RegisterFileEvent(fileClazz, metadata, entry)
            );
            this.cache.put(fileClazz.getName(), existingEntries);
        }
        ResourceConstants.ON_CACHE_LOADED.invokeEvent(new HashMap<>(this.cache)); // Uses a copy to avoid modification.
        ResourceConstants.ON_CACHE_REGISTER_END.invokeEvent();
    }

    public Object get(Class<?> clazz, String name)
    {
        ResourceConstants.ON_RESOURCE_REQUESTED.invokeEvent(new ResourceConstants.ClassNameEvent(clazz, name));
        String fileName = name.endsWith(".xml") ? name : name+".xml";
        Predicate<CacheEntry> filter = e->
        {
            String eFileName = e.metadata.absPath().getFileName().toString();
            if (!eFileName.endsWith(".xml")) eFileName=eFileName+".xml";
            return eFileName.equalsIgnoreCase(fileName);
        };
        try {
            CacheEntry entry = getEntry(clazz, filter);
            ResourceConstants.ON_RESOURCE_FOUND.invokeEvent(new ResourceConstants.LookupEvent(clazz, fileName, entry));
            return entry.weakData.get();
        } catch (IllegalArgumentException e) {
            ResourceConstants.ON_RESOURCE_NOT_FOUND.invokeEvent(new ResourceConstants.ClassNameEvent(clazz, fileName));
            throw e;
        }
    }
    public Collection<Object> get(Class<?> clazz)
    {
        return getEntries(clazz, (e) -> true).stream()
                .map(e->e.weakData.get())
                .filter(Objects::nonNull)
                .toList();
    }
    public XMLMetadata getMetadata(Class<?> clazz, String name)
    {
        ResourceConstants.ON_METADATA_REQUESTED.invokeEvent(new ResourceConstants.ClassNameEvent(clazz, name));
        String fileName = name.endsWith(".xml") ? name : name+".xml";
        Predicate<CacheEntry> filter = e->
        {
            String eFileName = e.metadata.absPath().getFileName().toString();
            if (!eFileName.endsWith(".xml")) eFileName=eFileName+".xml";
            return eFileName.equalsIgnoreCase(fileName);
        };
        try {
            CacheEntry entry = getEntry(clazz, filter);
            ResourceConstants.ON_METADATA_FOUND.invokeEvent(new ResourceConstants.LookupEvent(clazz, fileName, entry));
            return entry.metadata;
        } catch (IllegalArgumentException e) {
            ResourceConstants.ON_METADATA_NOT_FOUND.invokeEvent(new ResourceConstants.ClassNameEvent(clazz, fileName));
            throw e;
        }
    }
    public Collection<XMLMetadata> getMetadata(Class<?> clazz)
    {
        return getEntries(clazz, (e) -> true).stream()
                .map(e->e.metadata)
                .toList();
    }
    public <T> T getAs(Class<?> clazz, String name)
    {
        Object data = get(clazz,name);
        try {
            //noinspection unchecked
            return (T) data;
        }catch (ClassCastException e)
        {
            writeLog(Level.ERROR,"Failed to cast data of file %s to the provided type.%n", name, e);
            throw new RuntimeException(e);
        }
    }
    public <T> Collection<T> getAs(Class<?> clazz)
    {
        return this.get(clazz)
                .stream()
                .filter(o->{
                    try{
                        //noinspection unchecked
                        T ignored = (T)o;
                        return true;
                    }catch (Exception e)
                    {
                        writeLog(Level.WARN,"Failed to cast data to the provided type, skipping.%n", e);
                        return false;
                    }
                })
                .map(o->{
                    try{
                        //noinspection unchecked
                        return (T)o;
                    }catch (Exception e)
                    {
                        writeLog(Level.WARN,"Failed to cast data to the provided type, skipping.%n", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public String reference(Class<?> clazz, Object obj)
    {
        ResourceConstants.ON_REFERENCE_REQUESTED.invokeEvent(new ResourceConstants.ClassEvent(clazz));
        CacheEntry entry = getEntry(clazz, e->e.isObject(obj));
        String fileName = entry.metadata.absPath().getFileName().toString();
        ResourceConstants.ON_REFERENCE_RESOLVED.invokeEvent(new ResourceConstants.ReferenceEvent(clazz, obj, fileName));
        return fileName;
    }



    private CacheEntry getEntry(Class<?> clazz, Predicate<CacheEntry> filter)
    {
        Set<CacheEntry> entries = getEntries(clazz, filter);
        if (entries.isEmpty()) throw new IllegalArgumentException("No entries found for class "+clazz.getName()+" with the provided filter");
        if (entries.size() > 1) writeLog(Level.WARN,"Multiple entries found for class %s the first one will be selected");
        return entries.stream().findFirst().orElseThrow();
    }
    private Set<CacheEntry> getEntries(Class<?> clazz, Predicate<CacheEntry> filter)
    {
        if (this.cache.isEmpty()) throw new IllegalStateException("Cache is in an invalid state! Run `reload` to regenerate the cache");
        Set<CacheEntry> entries = this.cache.get(clazz.getName());
        if (entries == null) throw new IllegalArgumentException("No entries found for class "+clazz.getName());
        return entries.stream().filter(filter).collect(Collectors.toSet());
    }

    private static Set<File> getFiles(Class<?> clazz, Predicate<File> filter)
    {
        Path path = getResourcePath(clazz);
        if (!path.toFile().exists()) return Set.of();
        File file = path.toFile();
        return getFiles(file, filter);
    }
    private static Set<File> getFiles(File file, Predicate<File> filter)
    {
        Set<File> files = new HashSet<>();
        if (!file.isDirectory())
        { if(filter.test(file)) files.add(file); return files; }
        File[] fileList =  file.listFiles();
        if (fileList == null) return files;
        for (File testFile : fileList) {
            if (filter.test(testFile))
                files.add(testFile);
            files.addAll(getFiles(testFile, filter));
        }
        return files;
    }

    public static class Builder
    {
        boolean useDefaultHandlers;
        XMLReader.LoaderSettings settings;
        Set<XMLFieldHandler> handlers = new HashSet<>();
        Set<Class<?>> classes = new HashSet<>();
        public Builder setSettings(XMLReader.LoaderSettings settings)
        { this.settings = settings; return this; }
        public Builder useDefaultHandlers()
        { this.useDefaultHandlers =  true; return this; }
        public Builder addHandler(XMLFieldHandler handler)
        { this.handlers.add(handler); return this; }
        public Builder addHandlers(XMLFieldHandler... handlers)
        { this.handlers.addAll(Arrays.stream(handlers).toList()); return this; }
        public Builder addHandlers(Provider<XMLFieldHandler[]> handlers)
        { return this.addHandlers(handlers.get()); }
        public Builder addClass(Class<?> clazz)
        { this.classes.add(clazz); return this; }
        public Builder addClasses(Class<?>... classes)
        { this.classes.addAll(Arrays.stream(classes).toList()); return this;  }
        public Builder addClasses(Provider<Class<?>[]> provider)
        { return this.addClasses(provider.get()); }
        public ResourceXML build()
        { return new ResourceXML(this); }
    }
    @FunctionalInterface
    public interface Provider<T>
    {
        T get();
    }
}