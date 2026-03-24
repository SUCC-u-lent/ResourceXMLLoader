package org.resourcexmlloader.example;

import org.resourcexmlloader.annotations.XmlDataPath;

@XmlDataPath("data")
public class Item
{
    public int id = 1;
    public String name = "Iron Sword";
    public String description = "A basic sword forged from iron.";
    public float weight = 3.5f;
    public int value = 120;
    public boolean isEquippable = true;
}

