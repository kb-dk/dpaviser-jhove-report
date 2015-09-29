package dk.statsbiblioteket.dpaviser.report;

import dk.statsbiblioteket.dpaviser.report.helpers.CellRowComparator;
import dk.statsbiblioteket.dpaviser.report.helpers.POIHelpers;
import dk.statsbiblioteket.dpaviser.report.helpers.XPathHelpers;
import dk.statsbiblioteket.dpaviser.report.jhove.JHoveHelpers;
import dk.statsbiblioteket.util.xml.DOM;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java " + Main.class.getName() + " spreadsheet.xsl infomedia-dump-dir");
        }
        long start = System.currentTimeMillis();

        File tmpDir = com.google.common.io.Files.createTempDir();

        // Find all files in the given directory tree, run jhove on them, and extract
        // one or more row of cells for each file.  Create a spreadsheet corresponding to the rows.

        List<List<String>> cellRows =
                Files.walk(Paths.get(args[1]))
                        .filter(path -> Files.isDirectory(path) == false)
                        .map(JHoveHelpers.getJHoveFunction(Main.class.getResourceAsStream("/jhove.config.xml"), tmpDir))
                        .map(Main::streamToDOMWithNamespaces)
                        .flatMap(Main::getRepInfoNodes)
                        .flatMap(new ExtractRowsFromRepInfoNodes())
                        .collect(toList());

        System.out.println(cellRows.size() + " rows.");

        Collections.sort(cellRows, new CellRowComparator());

        try (OutputStream out = new FileOutputStream(args[0])) {
            POIHelpers.workbookFor(cellRows).write(out);
        }

        deleteTreeBelowPath(tmpDir.toPath());

        System.out.println(System.currentTimeMillis() - start + " ms.");
    }

    public static Stream<Node> getRepInfoNodes(Document dom) {
        return XPathHelpers.getNodesFor(dom, "/j:jhove/j:repInfo");
    }

    public static Document streamToDOMWithNamespaces(InputStream inputStream) {
        return DOM.streamToDOM(inputStream, true);
    }

    /**
     * Delete the whole file tree below the given path.  Files first while traversing, and the directories at the end.
     */
    private static void deleteTreeBelowPath(Path startHerePath) throws IOException {
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
                //System.out.println(path);
                Files.delete(path);
            } catch (IOException e) {
                System.out.println("Could not delete " + path + ": " + e); // better suggestions?
            }
        }
    }
}
