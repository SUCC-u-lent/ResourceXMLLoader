package org.resourcexmlloader.example;

import org.resourcexmlloader.annotations.ExcludeField;
import org.resourcexmlloader.annotations.XmlDataPath;

@XmlDataPath("data")
public class Inventory
{
    public String owner = "Player1";
    public int capacity = 3;
    public Item[] items = new Item[3];

    @ExcludeField
    public String internalTag = "DO_NOT_SERIALIZE";
}

