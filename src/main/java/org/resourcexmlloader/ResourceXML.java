package org.resourcexmlloader;

import org.resourcexmlloader.annotations.XmlDataPath;
import org.resourcexmlloader.annotations.XmlFileName;
import org.resourcexmlloader.customloader.FieldClassCompiler;
import org.resourcexmlloader.interfaces.XMLClassCompiler;
import org.resourcexmlloader.interfaces.XMLFieldCompiler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The main accessor
 */
public class ResourceXML
{
    private ResourceXML(Builder builder)
    {
        this.resourcePath = builder.resourcePath;
        this.outputStream = builder.outputStream;
        this.cache = new XMLCache(this.resourcePath);
        this.generator = null;
        XMLFieldCompiler[] fieldCompilers = builder.fieldCompilers == null ? new XMLFieldCompiler[0] : builder.fieldCompilers;
        XMLClassCompiler[] classCompilers = builder.classCompilers == null ? new XMLClassCompiler[0] : builder.classCompilers;

        if (fieldCompilers.length > 0 || classCompilers.length > 0)
        {
            this.generator = new XMLGenerator(this.resourcePath,this.outputStream,fieldCompilers,classCompilers,cache);
            this.decompiler = new XMLDecompiler(this.resourcePath,this.outputStream,fieldCompilers,classCompilers,cache);
        }
    }
    OutputStream outputStream;
    Path resourcePath;
    XMLGenerator generator;
    XMLDecompiler decompiler;
    XMLCache cache;

    public static class Builder
    {
        OutputStream outputStream;
        Path resourcePath;

        XMLFieldCompiler[] fieldCompilers;
        boolean useDefaultFieldCompilers = true;

        XMLClassCompiler[] classCompilers;
        public Builder(){}
        private boolean isCompiledEnviroment(Class<?> clazz)
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
            catch (Exception e)
            {
                try{
                    outputStream.write("Failed to determine if environment is compiled. Defaulting to non-compiled environment.".getBytes());
                }catch (Exception ignored){}
                return false;
            }
        }

        /**
         * Sets the output stream to the default system output stream.
         * @return The builder for chaining.
         */
        public Builder setDefaultOutputStream()
        {
            return this.setOutputStream(System.out);
        }
        public Builder setOutputStream(OutputStream outputStream)
        {
            this.outputStream = outputStream;
            return this;
        }
        public Builder setResourcePath(Path resourcePath)
        {
            this.resourcePath = resourcePath;
            return this;
        }
        public Builder useDefaultResourcePath(Class<?> clazz)
        {
            if (isCompiledEnviroment(clazz))
                this.resourcePath = Path.of("resources");
            else
                this.resourcePath = Path.of("src/main/resources");
            return this;
        }

        public Builder useFieldCompilers(XMLFieldCompiler... compilers)
        {
            this.fieldCompilers = compilers;
            return this;
        }
        public Builder disableDefaultFieldCompilers()
        {
            this.useDefaultFieldCompilers = false;
            return this;
        }

        public Builder useClassCompilers(XMLClassCompiler... compilers)
        {
            this.classCompilers = compilers;
            return this;
        }

        public ResourceXML build()
        {
            if (this.fieldCompilers != null)
            {
                List<XMLFieldCompiler> compilerList = Arrays.asList(this.fieldCompilers);
                List<XMLFieldCompiler> assembledCompiles = new ArrayList<>(compilerList);
                if (this.useDefaultFieldCompilers)
                {
                    assembledCompiles.add(new FieldClassCompiler());
                }
                this.fieldCompilers = assembledCompiles.toArray(XMLFieldCompiler[]::new);
            }
            return new ResourceXML(this);
        }
    }

    public <T> String getPathFor(T obj)
    {
        if (cache.hasEntry(obj.getClass(), e->e.isValidValue(obj)))
            return cache.getEntry(obj.getClass(), e->e.isValidValue(obj)).relativePath();
        throw new IllegalStateException("Object of type "+obj.getClass().getName()+" with value "+obj.toString()+" is not cached. Generate the object to XML first to cache it.");
    }
    public String getPathFor(Class<?> clazz)
    {
        Path filePath = resourcePath;
        if (clazz.isAnnotationPresent(XmlDataPath.class)) {
            filePath = filePath.resolve(clazz.getAnnotation(XmlDataPath.class).value());
        }

        String fileName = XmlLoaderExtensions.getIdentifier(clazz) != null
                ? XmlLoaderExtensions.getIdentifierValue(clazz)
                : clazz.isAnnotationPresent(XmlFileName.class)
                ? clazz.getAnnotation(XmlFileName.class).value()
                : clazz.getSimpleName();

        String absPath = filePath.resolve(fileName + ".xml").toAbsolutePath().toString();
        int indexOf = absPath.indexOf(resourcePath.toString());
        return absPath.substring(indexOf);
    }
    public <T> void generateXML(T obj) throws IllegalAccessException, ParserConfigurationException, TransformerException, IOException {
        if (obj == null) throw new IllegalArgumentException("Object to generate XML from cannot be null");

        try {
            obj.getClass().getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Object of type " + obj.getClass().getName() + " cannot be generated to XML because it does not have a default constructor");
        }

        if (generator == null) throw new IllegalAccessException("Generator not loaded. Add 'useXMLGenerator' in the builder.");

        if (resourcePath == null) throw new IllegalStateException("Resource path is not set.");

        Path filePath = resourcePath;
        if (obj.getClass().isAnnotationPresent(XmlDataPath.class)) {
            filePath = filePath.resolve(obj.getClass().getAnnotation(XmlDataPath.class).value());
        }

        String fileName = obj.getClass().isAnnotationPresent(XmlFileName.class) ?
                obj.getClass().getAnnotation(XmlFileName.class).value() :
                obj.getClass().getSimpleName();

        filePath = filePath.resolve(fileName + ".xml");

        Path finalFilePath = filePath;
        if (cache.hasEntry(obj.getClass(), e->e.isValidPath(finalFilePath.toString()))) return;
        generator.generateXML(filePath.toString(), obj);
        cache.addEntry(obj.getClass(), filePath.toFile(), obj);
    }
    public <T> void generateTemplate(T obj) throws IllegalAccessException, ParserConfigurationException, TransformerException, IOException {
        if (obj == null) throw new IllegalArgumentException("Object to generate XML from cannot be null");
        // If object is not of a type that can be constructed, it cannot be generated to XML
        try {
            Constructor<?> ignored = obj.getClass().getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Object of type " + obj.getClass().getName() + " cannot be generated to XML because it does not have a default constructor");
        }
        if (generator == null) throw new IllegalAccessException("Generator not loaded. Add 'useXMLGenerator' in the builder to use this module");
        StringBuilder stringBuilder = new StringBuilder();
        if (obj.getClass().isAnnotationPresent(XmlDataPath.class))
        {
            String dataPath = obj.getClass().getAnnotation(XmlDataPath.class).value();
            stringBuilder.append(dataPath);
        }
        stringBuilder.append(stringBuilder.isEmpty() ? "" : "\\").append("template.xml");
        if (cache.hasEntry(obj.getClass(), e->e.isValidPath(stringBuilder.toString()))) return;
        generator.generateXML(stringBuilder.toString(),obj,true);
        cache.addEntry(obj.getClass(), new File(stringBuilder.toString()), obj);
    }
    public void generateTemplate(String path, String fileName, Class<?> clazz) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException, InstantiationException, TransformerException, ParserConfigurationException, IOException {
        // If object is not of a type that can be constructed, it cannot be generated to XML
        try {
            Constructor<?> ignored = clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Object of type " + clazz.getName() + " cannot be generated to XML because it does not have a default constructor");
        }
        if (generator == null) throw new IllegalAccessException("Generator not loaded. Add 'useXMLGenerator' in the builder to use this module");
        String stringBuilder = path + "\\" +
                fileName;
        if (cache.hasEntry(clazz, e->e.isValidPath(stringBuilder))) return;
        generator.generateXML(stringBuilder,clazz.getDeclaredConstructor().newInstance(),true);
        cache.addEntry(clazz, new File(stringBuilder), null);
    }

    public Object[] loadXMLByClass(Class<?> clazz) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        if (clazz == null) throw new IllegalArgumentException("Object to generate XML from cannot be null");
        // If object is not of a type that can be constructed, it cannot be generated to XML
        try {
            Constructor<?> ignored = clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Object of type " + clazz.getName() + " cannot be generated to XML because it does not have a default constructor");
        }
        if (decompiler == null) throw new IllegalAccessException("Generator not loaded. Add 'useXMLGenerator' in the builder to use this module");
        return decompiler.loadXmlByClass(clazz);
    }
    public Object loadXmlByName(Class<?> clazz, String name) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        if (clazz == null) throw new IllegalArgumentException("Object to generate XML from cannot be null");
        // If object is not of a type that can be constructed, it cannot be generated to XML
        try {
            Constructor<?> ignored = clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Object of type " + clazz.getName() + " cannot be generated to XML because it does not have a default constructor");
        }
        if (decompiler == null) throw new IllegalAccessException("Generator not loaded. Add 'useXMLGenerator' in the builder to use this module");
        return decompiler.loadXmlByName(clazz,name);
    }
}
