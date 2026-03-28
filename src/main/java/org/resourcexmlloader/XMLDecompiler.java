package org.resourcexmlloader;

import org.resourcexmlloader.annotations.ExcludeField;
import org.resourcexmlloader.annotations.XmlDataPath;
import org.resourcexmlloader.interfaces.XMLClassCompiler;
import org.resourcexmlloader.interfaces.XMLFieldCompiler;
import org.resourcexmlloader.interfaces.XMLFieldCompiler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.print.Doc;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;

public class XMLDecompiler
{
    Path resourcePath;
    OutputStream outputStream;
    XMLFieldCompiler[] fieldCompilers;
    XMLClassCompiler[] classCompilers;
    XMLCache xmlCache;
    XMLDecompiler(Path resourcePath, OutputStream outputStream, XMLFieldCompiler[] fieldCompilers, XMLClassCompiler[] classCompilers, XMLCache xmlCache)
    {
        this.resourcePath = resourcePath;
        this.outputStream = outputStream;
        this.fieldCompilers = fieldCompilers;
        this.classCompilers = classCompilers;
        this.xmlCache = xmlCache;
    }


    public Object[] loadXmlByClass(Class<?> clazz) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException, InstantiationException {
        try { var ignored = clazz.getDeclaredConstructor(); } catch (Exception e){ System.out.println("Failed to decompile class "+clazz.getSimpleName()+" no parameterless constructor could be located."); e.printStackTrace(); throw e;}
        Path path = resourcePath;
        if (clazz.isAnnotationPresent(XmlDataPath.class))
        {
            String annotationPath = clazz.getAnnotation(XmlDataPath.class).value();
            path = resourcePath.resolve(annotationPath);
        }
        File file = path.toFile();
        if (!file.isDirectory()) throw new IllegalAccessException("Path: "+path.toAbsolutePath()+" is not a directory and therefore cannot load files from");
        List<File> files = gatherFiles(path)
                .stream()
                .filter(f->!f.getName().equalsIgnoreCase("template.xml"))
                .toList();
        List<Object> objects = new ArrayList<>();
        for (File f : files)
        {
            Path absPath = f.toPath();
            if (xmlCache.hasEntry(clazz,f))
            {
                objects.add(xmlCache.getEntry(clazz,f));
                continue;
            }
            Object value = decompileFile(f,absPath,clazz);
            objects.add(value);
            xmlCache.addEntry(clazz,f,value);
        }
        return objects.toArray(Object[]::new);
    }
    private Object decompileFile(File file, Path absPath, Class<?> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (xmlCache.hasEntry(clazz,file))
            return xmlCache.getEntry(clazz,file).cachedValue();
        Object classInstance = clazz.getDeclaredConstructor().newInstance();
        Field[] fields = Arrays.stream(XmlLoaderExtensions.getAllFields(clazz))
                .filter(f -> !f.isAnnotationPresent(ExcludeField.class))
                .filter(f-> !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()))
                .toArray(Field[]::new);
        Document dom;
        // Make an  instance of the DocumentBuilderFactory
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            dom = db.parse(file);
            Element doc = dom.getDocumentElement();
            Element rootElement = "root".equals(doc.getTagName()) ? doc : getElementByTagName(doc,"root");
            if (rootElement == null) throw new IllegalStateException("File "+file.getName()+" is not a valid XML file for decompilation. Root element is missing.");

            XMLClassCompiler classCompiler = Arrays.stream(this.classCompilers).filter(
                    c -> c.accepts(clazz)
            )
                    .max(Comparator.comparingDouble(XMLClassCompiler::getPriority))
                    .orElse(null);
            if (classCompiler != null)
            {
                classCompiler.decompile(this,dom,rootElement,fields,clazz,classInstance);
            } else {
                for (Field field : fields) {
                    field.setAccessible(true);
                    String fieldName = field.getName();
                    Class<?> fieldType = field.getType();
                    Element fieldElement = getElementByTagName(rootElement,fieldName);
                    if (fieldElement == null) continue;
                    Object decompiledValue = decompileValue(rootElement,fieldElement,fieldType);
                    try{
                        field.set(classInstance,decompiledValue);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }catch (Exception ignored){}
        xmlCache.addEntry(clazz,file,classInstance);
        return classInstance;
    }

    public Object decompileValue(Element rootElement, Element fieldElement, Class<?> clazz)
    {
        if (fieldElement == null) return null;
        Document doc = fieldElement.getOwnerDocument();
        Optional<XMLFieldCompiler> compiler = Arrays.stream(this.fieldCompilers).filter(
                c -> c.doesCompile(clazz)
        ).max(Comparator.comparingDouble(XMLFieldCompiler::getPriority));
        if (compiler.isPresent() && compiler.get().alwaysCompileUsing(clazz))
            return compiler.get().decompile(this, doc, rootElement, fieldElement, clazz);

        if (clazz.isArray())
        {
            Class<?> componentType = clazz.getComponentType();
            Element[] elements = getChildElements(fieldElement);
            int length = elements.length;
            Object array = Array.newInstance(componentType, length);
            for (int i = 0; i < length; i++) {
                Element element = elements[i];
                Object decompiledValue = decompileValue(rootElement,element,componentType);
                Array.set(array,i,decompiledValue);
            }
            return array;
        }
        else if (XmlLoaderExtensions.isKnownDataType(clazz))
        {
            return XmlLoaderExtensions.decompileKnownTypes(clazz,fieldElement);
        }
        else
        {
            if (compiler.isEmpty()) throw new IllegalStateException("No compiler found for type " + clazz.getSimpleName());
            return compiler.get().decompile(this, fieldElement.getOwnerDocument(), rootElement, fieldElement, clazz);
        }
    }

    private static Element getElementByTagName(Element element, String tagName){
        for (Element child : getChildElements(element)) {
            if (tagName.equals(child.getTagName())) return child;
        }
        return null;
    }

    private static Element[] getChildElements(Element element) {
        NodeList childNodes = element.getChildNodes();
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element childElement) {
                elements.add(childElement);
            }
        }
        return elements.toArray(Element[]::new);
    }

    private List<File> gatherFiles(Path path)
    {
        File file = path.toFile();
        if (!file.isDirectory()) return List.of(file);
        List<File> files = new ArrayList<>();
        try{
            Arrays.stream(Objects.requireNonNull(file.listFiles()))
                    .filter(e->!e.isDirectory())
                    .forEach(files::add);
        }catch (Exception ignored){}
        try{
            Arrays.stream(Objects.requireNonNull(file.listFiles()))
                    .filter(File::isDirectory)
                    .forEach(e->files.addAll(gatherFiles(e.toPath())));
        }catch (Exception ignored){}
        return files;
    }

    public Object loadXmlByName(Class<?> clazz, String fileName) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        try { var ignored = clazz.getDeclaredConstructor(); } catch (Exception e){ System.out.println("Failed to decompile class "+clazz.getSimpleName()+" no parameterless constructor could be located."); e.printStackTrace(); throw e;}
        Path path = resourcePath;
        if (clazz.isAnnotationPresent(XmlDataPath.class))
        {
            String annotationPath = clazz.getAnnotation(XmlDataPath.class).value();
            path = resourcePath.resolve(annotationPath);
        }
        File file = path.toFile();
        if (!file.isDirectory()) throw new IllegalAccessException("Path: "+path.toAbsolutePath()+" is not a directory and therefore cannot load files from");
        List<File> files = gatherFiles(path)
                .stream()
                .filter(f->!f.getName().equalsIgnoreCase("template.xml"))
                .filter(f->f.getName().equalsIgnoreCase(fileName) || f.getAbsolutePath().endsWith(fileName))
                .toList();
        List<Object> objects = new ArrayList<>();
        for (File f : files)
        {
            Path absPath = f.toPath();
            objects.add(decompileFile(f,absPath,clazz));
        }
        return objects.toArray(Object[]::new);
    }
}
