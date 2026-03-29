package org.ubunifu.resourcexmlloader;

import org.w3c.dom.Element;

import java.nio.file.Path;

public record XMLMetadata(Element rootElement, Path path)
{
}