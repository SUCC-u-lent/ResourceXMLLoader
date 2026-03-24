package org.resourcexmlloader.example;

import org.resourcexmlloader.ResourceXML;

public class Entry {
    static final ResourceXML resourceXML = new ResourceXML.Builder()
            .useDefaultResourcePath()
            .useXMLGenerator()
            .setDefaultOutputStream()
            .build();

    public static void main(String[] args)
    {
        generate(new ClassroomCharacter());
        generate(new PersonaCharacter());
        generate(new Item());
        generate(new Inventory());
        generate(new GameWorld());
    }

    private static void generate(Object obj)
    {
        try {
            resourceXML.generateXML(obj, false);
            System.out.println("Generated XML for " + obj.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("An error occurred while generating XML for " + obj.getClass().getSimpleName());
            e.printStackTrace();
        }
    }
}

