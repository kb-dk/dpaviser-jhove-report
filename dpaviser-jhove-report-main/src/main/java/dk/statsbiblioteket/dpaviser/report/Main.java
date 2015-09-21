package dk.statsbiblioteket.dpaviser.report;

import dk.statsbiblioteket.dpaviser.report.helpers.POIHelpers;
import dk.statsbiblioteket.dpaviser.report.helpers.XPathHelpers;
import dk.statsbiblioteket.dpaviser.report.jhove.JHoveHelpers;
import dk.statsbiblioteket.util.xml.DOM;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
                        .filter(Files::isDirectory)
                        .filter(isSingleEditionDir) // .../infomed/NOR/2015/06/03
                        .peek(System.out::println)  // see dir names on system out.
                        .map(JHoveHelpers.getInternalJHoveInvoker(Main.class.getResourceAsStream("/jhove.config.xml"), tmpDir))
                        .map(DOM::streamToDOM)
                        .flatMap(dom -> XPathHelpers.getNodesFor(dom, XPathHelpers.xpathCompile("/j:jhove/j:repInfo")))
                        .flatMap(new ExtractRowsFromRepInfoNodes())
                        .collect(toList());

        try (OutputStream out = new FileOutputStream(args[0])) {
            POIHelpers.workbookFor(cellRows).write(out);
        }
        // TODO: clean up temp dir.
        System.out.println(System.currentTimeMillis() - start + " ms.");
    }
}
