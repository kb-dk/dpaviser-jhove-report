package dk.statsbiblioteket.dpaviser.report;

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
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static dk.statsbiblioteket.dpaviser.report.helpers.UtilException.rethrowFunction;
import static java.util.stream.Collectors.toList;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java " + Main.class.getName() + " spreadsheet.xml infomedia-dump-dir");
        }
        long start = System.currentTimeMillis();

        File tmpDir = com.google.common.io.Files.createTempDir();

        Pattern singleEditionDirPattern = Pattern.compile("^.*/[A-Z0-9]+/[0-9]{4}/(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])$");

        Predicate<Path> isSingleEditionDir = path -> singleEditionDirPattern.matcher(path.toString()).matches();

        // Locate all dirs corresponding to a single edition, run jhove on them, and extract
        // one or more row of cells for each file.  Create a spreadsheet corresponding to the rows.


        Stream<Path> dataFiles = Files.walk(Paths.get(args[1]))
                .filter(path -> !Files.isDirectory(path));

        InputStream config = Main.class.getResourceAsStream("/jhove.config.xml");
        Function<Path, InputStream> jHoveInvoker = JHoveHelpers.getInternalJHoveInvoker(config, tmpDir);
        Stream<Document> jhoveResults = dataFiles
                .map(jHoveInvoker)
                .map(inputStream -> DOM.streamToDOM(inputStream, true));

        Function<Document, Stream<Node>> getRepInfoMapper = rethrowFunction(dom -> XPathHelpers.getNodesFor(dom, "/j:jhove/j:repInfo"));
        Function<Node, Stream<List<String>>> extractInfoMapper = rethrowFunction(new ExtractRowsFromRepInfoNodes());
        List<List<String>> cellRows = jhoveResults.flatMap(getRepInfoMapper).flatMap(extractInfoMapper).collect(toList());

        System.out.println(cellRows.size() + " rows.");

        Collections.sort(cellRows, (left, right) -> {
            for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
                int comparison = left.get(i).compareTo(right.get(i));
                if (comparison != 0) {
                    return comparison;
                }
            }
            // one list exhausted without a difference - the shortest is "before" the longest.
            return right.size() - left.size();
        });

        try (OutputStream out = new FileOutputStream(args[0])) {
            POIHelpers.workbookFor(cellRows).write(out);
        }

        // if we get this far, clean up temp dir.  http://stackoverflow.com/a/5039900/53897
        Path dirPath = tmpDir.toPath();
        Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .forEach((path) -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        //ignore
                    }
                });
        // For now do not delete subdirectories (need to delete the tree from the bottom).
        try {
            Files.delete(dirPath);
        } catch (DirectoryNotEmptyException e) {
            System.out.println("Directory not empty - " + e);
        }

        System.out.println(System.currentTimeMillis() - start + " ms.");
    }

}
