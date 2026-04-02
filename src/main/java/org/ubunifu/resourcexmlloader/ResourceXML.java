package org.ubunifu.resourcexmlloader;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.ubunifu.resourcexmlloader.interfaces.XMLFieldHandler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final WeakIdentityHashMap<Class<?>, Set<CacheEntry>> CACHE = new WeakIdentityHashMap<>();

    LoaderSettings loaderSettings;
    Set<Class<?>> registeredClasses = new HashSet<>();
    public static class Builder{
        Set<Class<?>> registeredClasses = new HashSet<>();
        LoaderSettings settings = new LoaderSettings(Level.INFO);
        public Builder withLoaderSettings(LoaderSettings settings)
        { this.settings = settings; return this; }
        public Builder registerClass(Class<?> clazz)
        { this.registeredClasses.add(clazz); return this; }
        public Builder registerClasses(Class<?>... classes)
        { this.registeredClasses.addAll(Arrays.asList(classes)); return this; }
        public Builder registerClasses(ClassProvider provider)
        { return registerClasses(provider.get().toArray(Class<?>[]::new)); }
        public ResourceXML build()
        { return new ResourceXML(this); }
    }
    ResourceXML(Builder builder)
    {
        this.loaderSettings = builder.settings;
    }
    File[] getFiles(Class<?> clazz, Predicate<File> filter)
    {
        Path path = getResourcePath(clazz);
        File file = path.toFile();
        return Arrays.stream(getFilesWithin(file))
                .filter(File::isFile)
                .filter(f->f.getName().endsWith(".xml"))
                .filter(filter)
                .toArray(File[]::new);
    }
    File[] getFilesWithin(File file)
    {
        if (file.isFile()) return new File[]{file};
        File[] files = file.listFiles();
        if (files == null) return new File[0];
        Set<File> fileSet = new HashSet<>(Arrays.stream(files).toList());
        fileSet.addAll(Arrays.stream(files)
                .filter(File::isDirectory)
                .flatMap(f-> Arrays.stream(getFilesWithin(f)))
                .toList());
        return fileSet.toArray(File[]::new);
    }
    public void flush()
    { this.CACHE.clear(); }
    public void register()
    {
        if (registeredClasses.isEmpty()) return;
        Class<?> firstClass = registeredClasses.stream().findFirst().orElseThrow();
        File[] files = getFiles(firstClass, f->true);
        for (File file : files) {
            for (Class<?> clazz : registeredClasses)
            {
                Object obj = tryUnmarshal(clazz,file);
                if (obj == null) continue;
                CacheEntry entry = new CacheEntry(XMLMetadata.of(clazz,file), new WeakReference<>(obj), obj.hashCode());
                this.CACHE.computeIfAbsent(clazz, c -> new HashSet<>()).add(entry);
            }
        }
    }
    public @NotNull Object unmarshal(Class<?> clazz, File file) {
        try{
            JAXBContext context = JAXBContext.newInstance(clazz);
            return context.createUnmarshaller()
                    .unmarshal(new FileReader(file));
        }catch (Exception e)
        {
            writeLog(Level.ERROR, "Failed to unmarshal file {} for class {}.", file.getName(), clazz.getName(), e);
            throw new RuntimeException(e);
        }
    }
    public @Nullable Object tryUnmarshal(Class<?> clazz, File file)
    {
        try{
            JAXBContext context = JAXBContext.newInstance(clazz);
            return context.createUnmarshaller()
                    .unmarshal(new FileReader(file));
        }catch (Exception ignored)
        { return null; }
    }
    @FunctionalInterface
    public interface ClassProvider
    {
        Collection<Class<?>> get();
    }
}