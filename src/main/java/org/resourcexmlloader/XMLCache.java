package org.resourcexmlloader;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

class XMLCache {

    public record CacheEntry(Class<?> clazz, String fileNameWithoutExtension, String fileName, String absolutePath, String relativePath, Object cachedValue)
    {
        public boolean isValidPath(String value)
        {
            return fileNameWithoutExtension.toLowerCase().contains(value.toLowerCase()) || fileName.toLowerCase().contains(value.toLowerCase()) || absolutePath.toLowerCase().contains(value.toLowerCase()) || relativePath.toLowerCase().contains(value.toLowerCase());
        }
        public boolean isValidValue(Object value)
        {
            if (cachedValue == null) return false;
            return cachedValue.equals(value);
        }
    }
    private final Path resourcePath;
    public XMLCache(Path path){
        this.resourcePath = path;
    }

    private final Set<CacheEntry> cacheEntries = new HashSet<>();
    public void addEntry(Class<?> clazz, File file, Object value)
    {
        String fileNameWOExtension = file.getName().contains(".") ? file.getName().substring(0, file.getName().lastIndexOf('.')) : file.getName();
        String absolutePath = file.getAbsolutePath();
        String relativePath = absolutePath.replace(resourcePath.toAbsolutePath().toString(), "");
        cacheEntries.add(new CacheEntry(clazz, fileNameWOExtension, file.getName(), absolutePath, relativePath, value));
    }
    public boolean hasEntry(Class<?> clazz, Predicate<CacheEntry> additionalPredicate) {
        return cacheEntries.stream().anyMatch(e -> e.clazz.equals(clazz) && additionalPredicate.test(e));
    }
    public boolean hasEntry(Class<?> clazz, File file) {
        return cacheEntries.stream().anyMatch(e -> e.clazz.equals(clazz) && e.absolutePath.equals(file.getAbsolutePath()));
    }
    public CacheEntry getEntry(Class<?> clazz, Predicate<CacheEntry> additionalPredicate)
    {
        return cacheEntries.stream().filter(e->e.clazz.equals(clazz) && additionalPredicate.test(e)).findFirst().orElse(null);
    }
    public CacheEntry getEntry(Class<?> clazz, File file)
    {
        return cacheEntries.stream().filter(e->e.clazz.equals(clazz) && e.absolutePath.equals(file.getAbsolutePath())).findFirst().orElse(null);
    }
}
