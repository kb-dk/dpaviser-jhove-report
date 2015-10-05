package dk.statsbiblioteket.dpaviser.report.helpers;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PathHelpers {
    /**
     * Delete the whole file tree below the given path.  Files first while traversing, and the directories at the end.
     */
    public static void deleteTreeBelowPath(Path startHerePath) throws IOException {

        List<Path> dirPaths = new ArrayList<>();

        // Delete files as we see them.  Save directories for deletion later.

        Files.walk(startHerePath)
                .forEach(path -> {
                    if (Files.isDirectory(path)) {
                        dirPaths.add(path);
                    } else {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            e.printStackTrace(); // so we can continue even if a file is open.
                        }
                    }
                });

        Collections.sort(dirPaths);
        Collections.reverse(dirPaths);

        for (Path path : dirPaths) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                System.out.println("Could not delete " + path + ": " + e); // better suggestions?
            }
        }
    }
}
