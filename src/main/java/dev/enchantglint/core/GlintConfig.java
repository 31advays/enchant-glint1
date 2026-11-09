package dev.enchantglint.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** JSON persistence. A corrupt file is backed up and replaced with defaults, never fatal. */
public final class GlintConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private GlintSettings settings = new GlintSettings();
    /** Bumped on every change so the renderer can cheaply detect "needs rebuild". */
    private int revision = 1;

    public GlintConfig(Path file) { this.file = file; }

    public GlintSettings get() { return settings; }
    public int revision() { return revision; }
    public void touch() { settings.sanitize(); revision++; }

    public void load() {
        if (!Files.exists(file)) { settings = new GlintSettings(); revision++; return; }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            GlintSettings loaded = GSON.fromJson(r, GlintSettings.class);
            settings = loaded != null ? loaded : new GlintSettings();
            settings.sanitize();
        } catch (Exception e) {
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".broken"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
            settings = new GlintSettings();
        }
        revision++;
    }

    public void save() {
        settings.sanitize();
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { GSON.toJson(settings, w); }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[EnchantGlint] could not save config: " + e);
        }
    }
}
