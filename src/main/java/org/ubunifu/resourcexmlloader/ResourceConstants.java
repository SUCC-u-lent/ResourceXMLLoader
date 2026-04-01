package org.ubunifu.resourcexmlloader;

import java.util.Map;
import java.util.Set;

public class ResourceConstants
{
    public record ClassEvent(Class<?> clazz) {}
    public record ClassNameEvent(Class<?> clazz, String name) {}
    public record RegisterFileEvent(Class<?> clazz, XMLMetadata metadata, ResourceXML.CacheEntry entry) {}
    public record RegisterFailureEvent(Class<?> clazz, String fileName, Throwable cause) {}
    public record LookupEvent(Class<?> clazz, String name, ResourceXML.CacheEntry entry) {}
    public record ReferenceEvent(Class<?> clazz, Object source, String fileName) {}

    public static final Event.ParameterlessEvent ON_CACHE_RELOAD = new Event.ParameterlessEvent();
    public static final Event.ParameterlessEvent ON_CACHE_RELOAD_START = new Event.ParameterlessEvent();
    public static final Event.ParameterlessEvent ON_CACHE_RELOAD_END = new Event.ParameterlessEvent();
    public static final Event.ParameterlessEvent ON_CACHE_FLUSH = new Event.ParameterlessEvent();
    public static final Event.ParameterlessEvent ON_CACHE_REGISTER_START = new Event.ParameterlessEvent();
    public static final Event.ParameterizedEvent<ResourceXML.CacheEntry> ON_RESOURCE_LOADED = new Event.ParameterizedEvent<>();
    public static final Event.ParameterizedEvent<RegisterFileEvent> ON_RESOURCE_REGISTERED = new Event.ParameterizedEvent<>();
    public static final Event.ParameterizedEvent<RegisterFailureEvent> ON_RESOURCE_REGISTER_FAILED = new Event.ParameterizedEvent<>();
    public static final Event.ParameterizedEvent<Map<String, Set<ResourceXML.CacheEntry>>> ON_CACHE_LOADED = new Event.ParameterizedEvent<>();
    public static final Event.ParameterlessEvent ON_CACHE_REGISTER_END = new Event.ParameterlessEvent();

    public static final Event.ParameterizedEvent<ClassNameEvent> ON_RESOURCE_REQUESTED = new Event.ParameterizedEvent<>();
    public static final Event.ParameterizedEvent<LookupEvent> ON_RESOURCE_FOUND = new Event.ParameterizedEvent<>();
    public static final Event.ParameterizedEvent<ClassNameEvent> ON_RESOURCE_NOT_FOUND = new Event.ParameterizedEvent<>();

    public static final Event.ParameterizedEvent<ClassNameEvent> ON_METADATA_REQUESTED = new Event.ParameterizedEvent<>();
    public static final Event.ParameterizedEvent<LookupEvent> ON_METADATA_FOUND = new Event.ParameterizedEvent<>();
    public static final Event.ParameterizedEvent<ClassNameEvent> ON_METADATA_NOT_FOUND = new Event.ParameterizedEvent<>();

    public static final Event.ParameterizedEvent<ClassEvent> ON_REFERENCE_REQUESTED = new Event.ParameterizedEvent<>();
    public static final Event.ParameterizedEvent<ReferenceEvent> ON_REFERENCE_RESOLVED = new Event.ParameterizedEvent<>();
}
