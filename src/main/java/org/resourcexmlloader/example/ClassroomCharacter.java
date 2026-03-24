package org.resourcexmlloader.example;

import org.resourcexmlloader.annotations.XmlDataPath;

@XmlDataPath("data")
public class ClassroomCharacter
{
    public int ClassroomID = 43;
    public String ClassroomName = "Samsung";
    public PersonaCharacter[] persona = new PersonaCharacter[4];
}

