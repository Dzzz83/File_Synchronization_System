package com.filesync.client.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

public final class ProjectStructurePrinter {
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "target", ".git", ".idea", ".mvn", "node_modules", "out", "build", "dist", ".settings", ".classpath", ".project"
    );
    private static final Set<String> EXCLUDED_FILES = Set.of(
            ".DS_Store", "Thumbs.db"
    );

    private ProjectStructurePrinter() {}

    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("").toAbsolutePath().normalize();
        System.out.println("Project Structure: " + projectRoot);
        printTree(projectRoot, "", true);
    }

    private static void printTree(Path path, String prefix, boolean isLast) throws IOException {
        File file = path.toFile();
        if (shouldSkip(file)) return;

        String connector = isLast ? "└── " : "├── ";
        System.out.println(prefix + connector + file.getName());

        if (file.isDirectory()) {
            try (Stream<Path> children = Files.list(path).sorted()) {
                Path[] childArray = children.toArray(Path[]::new);
                for (int i = 0; i < childArray.length; i++) {
                    boolean lastChild = (i == childArray.length - 1);
                    String newPrefix = prefix + (isLast ? "    " : "│   ");
                    printTree(childArray[i], newPrefix, lastChild);
                }
            }
        }
    }

    private static boolean shouldSkip(File file) {
        String name = file.getName();
        if (EXCLUDED_DIRS.contains(name) && file.isDirectory()) return true;
        if (EXCLUDED_FILES.contains(name)) return true;
        // Optionally skip .java~ or other backup files
        if (name.endsWith("~")) return true;
        return false;
    }
}