package dk.statsbiblioteket.dpaviser.report;

import dk.statsbiblioteket.dpaviser.report.helpers.POIHelpers;
import dk.statsbiblioteket.dpaviser.report.helpers.XPathHelpers;
import dk.statsbiblioteket.dpaviser.report.jhove.JHoveHelpers;
import dk.statsbiblioteket.util.xml.DOM;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

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

        List<List<String>> cellRows =
                Files.walk(Paths.get(args[1]))
                        .filter(path -> Files.isDirectory(path) == false)
                                //.peek(System.out::println)  // see dir names on system out.
                        .map(JHoveHelpers.getInternalJHoveInvoker(Main.class.getResourceAsStream("/jhove.config.xml"), tmpDir))
                        .map(inputStream -> DOM.streamToDOM(inputStream, true))
                        .flatMap(dom -> XPathHelpers.getNodesFor(dom, "/j:jhove/j:repInfo"))
                        .flatMap(new ExtractRowsFromRepInfoNodes())
                        .collect(toList());

        System.out.println(cellRows);
        System.out.println(cellRows.size());
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
                        e.printStackTrace();
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
