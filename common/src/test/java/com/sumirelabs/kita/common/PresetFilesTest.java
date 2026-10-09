package com.sumirelabs.kita.common;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetFilesTest {
    @TempDir Path directory;
    @Test void readsHesuviBomAndKeepsFileIdentityStable() throws Exception {
        var wav = Files.write(directory.resolve("atmos.wav"), new byte[]{0});
        var original = PresetFiles.scan(directory).getFirst();
        Files.writeString(directory.resolve("info.csv"), "\uFEFFatmos;Dolby Atmos 7.1 virtual surround sound for headphones\n");
        var described = PresetFiles.scan(directory).getFirst();
        assertEquals("Dolby Atmos", described.name());
        assertEquals("Dolby Atmos 7.1 virtual surround sound for headphones", described.description());
        assertEquals(original.id(), described.id());
        assertEquals("atmos.wav", described.fileName());
        assertEquals(wav.toRealPath(), described.path());
    }
    @Test void missingRowsFallBackAndWildcardDoesNotRenameUnknownFiles() throws Exception {
        Files.write(directory.resolve("custom.wav"), new byte[]{0});
        Files.writeString(directory.resolve("info.csv"), "*;Unknown file!\nbroken row\ncustom;\n");
        var preset = PresetFiles.scan(directory).getFirst();
        assertEquals("custom.wav", preset.name());
        assertTrue(preset.description().isEmpty());
    }
    @Test void inheritsParentMetadataAndPrefersLocalRows() throws Exception {
        var child = Files.createDirectories(directory.resolve("44"));
        Files.write(child.resolve("ATMOS-.WAV"), new byte[]{0});
        Files.writeString(directory.resolve("info.csv"), "atmos-;Dolby Atmos 7.1 virtual surround sound for headphones without reverb\n");
        assertEquals("Dolby Atmos (No reverb)", PresetFiles.scan(directory).getFirst().name());
        Files.writeString(child.resolve("info.csv"), "atmos-;Local profile recorded by Author\n");
        assertEquals("Local profile", PresetFiles.scan(directory).getFirst().name());
    }
    @Test void preservesQuotedTextSemicolonsUnicodeAndLegacyNewlines() throws Exception {
        Files.write(directory.resolve("ssc_hù.wav"), new byte[]{0});
        Files.writeString(directory.resolve("info.csv"), "ssc_hù;Spatial Sound Card \"Shanghai\"; extra/n/nRoom description\n");
        var preset = PresetFiles.scan(directory).getFirst();
        assertEquals("Spatial Sound Card \"Shanghai\"; extra", preset.name());
        assertEquals("Spatial Sound Card \"Shanghai\"; extra\n\nRoom description", preset.description());
    }
}
