package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioSourceDialogStateTest {

    @Test
    public void deletingEveryRuleKeepsTheExplicitEmptySelection() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AudioSourceDialog.java")));

        assertTrue("Unconfigured audio settings should materialize the built-in defaults into the editable list",
                source.contains("config.isConfigured() ? config.getEnabledSites() : defaultRules()"));
        assertFalse("An empty editable list is a valid selection and must not restore built-in defaults",
                source.contains("tempEnabledRules.isEmpty()"));
        assertTrue("Chip rendering should use the current editable list directly",
                source.contains("for (String rule : tempEnabledRules)"));
    }

    @Test
    public void resetButtonExplicitlyRestoresBuiltInDefaults() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "AudioSourceDialog.java")));

        assertTrue("Reset should repopulate the editable list instead of using empty as a default sentinel",
                source.contains("tempEnabledRules.addAll(defaultRules())"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
