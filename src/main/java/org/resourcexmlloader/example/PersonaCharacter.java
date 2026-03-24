package org.resourcexmlloader.example;

import org.resourcexmlloader.annotations.XmlDataPath;

import java.util.Random;

@XmlDataPath("data")
public class PersonaCharacter
{
    public int ID = new Random().nextInt(300);
    public String name = "John";
    public String surname = "Doe";
    public String email = "john.doe@hotmail.com";
    public String password = "password";
}

