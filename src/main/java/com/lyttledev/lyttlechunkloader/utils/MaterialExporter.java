package com.lyttledev.lyttlechunkloader.utils;

import org.bukkit.Material;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MaterialExporter {
    public static void exportMaterials(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            for (Material material : Material.values()) {
                writer.write(material.name());
                writer.write(System.lineSeparator());
            }
        }
    }
}