package com.endiq.turtlelauncher.feature.mod.parser;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class ModInfo {
    private File file;
    private final String id;
    private final String version;
    private final String name;
    private final String description;
    private final String[] authors;
    /** modid -> version requirement string, as declared by the mod's own metadata. Never null. */
    private final Map<String, String> dependencies;
    /** Alternate IDs exposed through Fabric/Quilt metadata's `provides` field. Never null. */
    private final java.util.Set<String> providedIds;

    public ModInfo(String id, String version, String name, String description, String[] authors) {
        this(id, version, name, description, authors, null, null);
    }

    /**
     * @param dependencies modid -> version-requirement-string map parsed from this mod's own
     *                      metadata (fabric.mod.json "depends", quilt.mod.json
     *                      "quilt_loader.depends", or mods.toml "[[dependencies.&lt;id&gt;]]").
     *                      Pass null for "unknown/none parsed" — it will be normalised to an
     *                      empty map, never null, so callers never need a null check.
     */
    public ModInfo(String id, String version, String name, String description, String[] authors,
                    Map<String, String> dependencies) {
        this(id, version, name, description, authors, dependencies, null);
    }

    /**
     * @param providedIds alternate mod IDs this jar advertises via Fabric/Quilt metadata.
     *                    A dependency on one of these IDs is satisfied by this mod and must not
     *                    trigger a duplicate auto-download.
     */
    public ModInfo(String id, String version, String name, String description, String[] authors,
                    Map<String, String> dependencies, java.util.Set<String> providedIds) {
        this.id = id;
        this.version = version;
        this.name = name;
        this.description = description;
        this.authors = authors;
        this.dependencies = dependencies != null ? dependencies : Collections.emptyMap();
        this.providedIds = providedIds != null ? providedIds : Collections.emptySet();
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getAuthors() {
        return authors;
    }

    /** modid -> version requirement string declared as a dependency of this mod. Never null. */
    @NonNull
    public Map<String, String> getDependencies() {
        return dependencies != null ? dependencies : Collections.emptyMap();
    }

    /** Alternate IDs advertised by this mod. Old cache records may not contain this field. */
    @NonNull
    public java.util.Set<String> getProvidedIds() {
        return providedIds != null ? providedIds : Collections.emptySet();
    }

    @NonNull
    @Override
    public String toString() {
        return "ModInfo{" +
                "id='" + id + '\'' +
                ", version='" + version + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", authors=" + Arrays.toString(authors) +
                ", dependencies=" + dependencies +
                ", providedIds=" + getProvidedIds() +
                '}';
    }
}
