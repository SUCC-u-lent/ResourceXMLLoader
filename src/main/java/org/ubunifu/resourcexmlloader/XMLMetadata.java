package org.ubunifu.resourcexmlloader;

import org.w3c.dom.Element;

import java.io.File;
import java.nio.file.Path;

public record XMLMetadata(Path absPath, Path resourcePath, File file)
{
    public static XMLMetadata of(Class<?> clazz, File file)
    {
        Path absPath = file.toPath().toAbsolutePath();
        String absPathString = absPath.toString();
        Path resourcePath = new File(absPathString
                .replace(
                        ResourceXML.getResourcePath(clazz).toAbsolutePath().toString(),
                        ""
                )).toPath();
        return new XMLMetadata(absPath, resourcePath, file);
    }
}