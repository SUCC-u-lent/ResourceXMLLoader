package org.resourcexmlloader.example;

import org.resourcexmlloader.annotations.XmlDataPath;
import org.resourcexmlloader.annotations.XmlFileName;

@XmlDataPath("data")
@XmlFileName("GameWorld")
public class GameWorld
{
    public String worldName = "Eldenmoor";
    public int width = 2048;
    public int height = 2048;
    public float gravity = 9.8f;
    public float[] spawnPoint = new float[]{ 512.0f, 128.0f };
    public String[] biomes = new String[]{ "Forest", "Desert", "Tundra", "Ocean" };
}

