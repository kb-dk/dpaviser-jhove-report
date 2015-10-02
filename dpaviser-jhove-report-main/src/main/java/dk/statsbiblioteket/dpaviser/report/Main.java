package dk.statsbiblioteket.dpaviser.report;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import dk.statsbiblioteket.dpaviser.report.helpers.CellRowComparator;
import dk.statsbiblioteket.dpaviser.report.helpers.JSoupHelpers;
import dk.statsbiblioteket.dpaviser.report.helpers.POIHelpers;
import dk.statsbiblioteket.dpaviser.report.jhove.JHoveHelpers;
import dk.statsbiblioteket.util.xml.XSLT;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public class Main {

    public static final List<String> HEADERS = asList("Name", "Type", "PDF:JPEG", "PDF:UNCOMPRESSED", "PDF:FLATEDECODE",
            "PDF:OTHERIMAGES", "PDF:TYPE0", "PDF:TYPE1", "PDF:TRUETYPE", "PDF:OTHERFONTS");

    protected static DocumentBuilderFactory documentBuilderFactory;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java " + Main.class.getName() + " spreadsheet.xsl infomedia-dump-dir");
        }
        long start = System.currentTimeMillis();

        File tmpDir = com.google.common.io.Files.createTempDir();

        /* Find all files in the given directory tree, and extract
         one or more row of cells for each file.  Create a spreadsheet corresponding to the rows.
         */

        List<List<String>> cellRows =
                Files.walk(Paths.get(args[1]))
                        .filter(Files::isRegularFile)
                        .flatMap(path -> Main.getRowsForPath(path, tmpDir))
                        .collect(toList());

        System.out.println(cellRows.size() + " rows.");

        Collections.sort(cellRows, new CellRowComparator());
        cellRows.add(0, HEADERS); // How to mark rows as headers in workbook sheet

        try (OutputStream out = new FileOutputStream(args[0])) {
            POIHelpers.workbookFor(cellRows).write(out);
        }

        deleteTreeBelowPath(tmpDir.toPath());

        System.out.println(System.currentTimeMillis() - start + " ms.");
    }

    protected static Stream<List<String>> getRowsForPath(Path path, File tmpDir) {
        List<List<String>> result = new ArrayList<>();

        String pathString = path.toString();
        if (pathString.endsWith(".pdf")) {
            // run jhove and extract information
            InputStream configStream = Main.class.getResourceAsStream("/jhove.config.xml");
            Function<Path, InputStream> jHoveFunction = JHoveHelpers.getJHoveFunction(configStream, tmpDir);

            InputStream is = jHoveFunction.apply(path);

            try {
                URL pdfXSLT = Thread.currentThread().getContextClassLoader().getResource("pdf-process-jhove-output.xsl");
                String tableXML = XSLT.transform(pdfXSLT, is, null).toString(); // caches compiled stylesheet

                List<List<String>> firstTableFromHTML = JSoupHelpers.getFirstTableFromHTML(tableXML);

                // URI decode path in first cell before adding. Cannot be done easily inside XSLT 1.0
                List<String> firstRow = firstTableFromHTML.get(0);
                firstRow.set(0, new URI(firstRow.get(0)).getPath());

                result.addAll(firstTableFromHTML);
            } catch (TransformerException e) {
                throw new RuntimeException("could not transform jhove output", e);
            } catch (URISyntaxException e) {
                throw new RuntimeException("bad URI returned from XSLT", e);
            }
        } else if (pathString.endsWith(".xml")) {
            try {
                if (documentBuilderFactory == null) {
                    documentBuilderFactory = DocumentBuilderFactory.newInstance();
                    documentBuilderFactory.setNamespaceAware(true);
                    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                    schemaFactory.setErrorHandler(new MyErrorHandler());
                    Schema schema = schemaFactory.newSchema(Main.class.getResource("/NewsML_1.2-infomedia.xsd"));

                    documentBuilderFactory.setSchema(schema);
                }
                DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();
                db.setErrorHandler(new MyErrorHandler());
                Document document = db.parse(path.toUri().toString());
                result.add(asList(pathString, "XML", "XML valid"));
            } catch (Exception e) {
                result.add(asList(pathString, "XML", "XML not valid", e.getMessage()));
            }
        } else if (pathString.endsWith(".md5")) {
            final List<String> row;
            try {
                String md5FileLine = Files.readAllLines(path).get(0);
                if (md5FileLine.length() != 32) {
                    row = asList(pathString, "MD5", "MD5 first line not 32 characters.");
                } else {
                    File fileToHash = new File(pathString.substring(0, pathString.length() - ".md5".length()));
                    HashCode md5calculated = com.google.common.io.Files.hash(fileToHash, Hashing.md5());
                    if (md5FileLine.equalsIgnoreCase(md5calculated.toString())) {
                        row = asList(pathString, "MD5", "MD5 valid");
                    } else {
                        row = asList(pathString, "MD5", "MD5 " + md5FileLine + " stated in file, calculated " + md5calculated);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("could not process " + path, e);
            }
            result.add(row);
        } else {
            // the rest we ignore for now.
        }
        return result.stream();
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

    static class MyErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }

    ;
}
