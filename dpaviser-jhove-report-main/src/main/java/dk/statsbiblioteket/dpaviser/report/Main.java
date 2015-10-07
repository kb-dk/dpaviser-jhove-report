package dk.statsbiblioteket.dpaviser.report;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import dk.statsbiblioteket.dpaviser.report.helpers.CellRowComparator;
import dk.statsbiblioteket.dpaviser.report.helpers.JSoupHelpers;
import dk.statsbiblioteket.dpaviser.report.helpers.POIHelpers;
import dk.statsbiblioteket.dpaviser.report.helpers.PathHelpers;
import dk.statsbiblioteket.dpaviser.report.jhove.JHoveHelpers;
import dk.statsbiblioteket.dpaviser.report.sax.ExceptionThrowingErrorHandler;
import dk.statsbiblioteket.util.xml.XSLT;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.FileOutputStream;
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

    protected static Stream<List<String>> getRowsForPath(Path path, File tmpDir) {
        List<List<String>> result = new ArrayList<>();

        String pathString = path.toString();

        if (pathString.endsWith(".pdf")) { // PDF: run jhove and extract information
            InputStream configStream = Main.class.getResourceAsStream("/jhove.config.xml");
            Function<Path, InputStream> jHoveFunction = JHoveHelpers.getJHoveFunction(configStream, tmpDir);

            InputStream is = jHoveFunction.apply(path);

            try {
                URL pdfXSLT = Thread.currentThread().getContextClassLoader().getResource("pdf-process-jhove-output.xsl");

                String tableXML = XSLT.transform(pdfXSLT, is, null).toString(); // sbutil caches compiled stylesheet

                List<List<String>> firstTableFromHTML = JSoupHelpers.getFirstTableFromHTML(tableXML);

                List<String> firstRow = firstTableFromHTML.get(0);
                firstRow.set(0, new URI(firstRow.get(0)).getPath()); // URLDecode. Hard in XSLT 1.0.

                result.addAll(firstTableFromHTML);
            } catch (TransformerException e) {
                throw new RuntimeException("could not transform jhove output", e);
            } catch (URISyntaxException e) {
                throw new RuntimeException("bad URI returned from XSLT", e);
            }
        } else if (pathString.endsWith(".xml")) { // XML:  Validate.
            try {
                if (documentBuilderFactory == null) {
                    documentBuilderFactory = DocumentBuilderFactory.newInstance();
                    documentBuilderFactory.setNamespaceAware(true);
                    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                    schemaFactory.setErrorHandler(new ExceptionThrowingErrorHandler());
                    Schema schema = schemaFactory.newSchema(Main.class.getResource("/NewsML_1.2-infomedia.xsd"));

                    documentBuilderFactory.setSchema(schema);
                }
                DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();
                db.setErrorHandler(new ExceptionThrowingErrorHandler());
                Document document = db.parse(path.toUri().toString());
                result.add(asList(pathString, "XML", "XML valid"));

                // TODO:  Add more XML information from KFC.
            } catch (Exception e) {
                result.add(asList(pathString, "XML", "XML not valid", e.getMessage()));
            }
        } else if (pathString.endsWith(".md5")) {
            // compare value in this file with value calculated.
            final List<String> row;
            try {
                String md5FileLine = Files.readAllLines(path).get(0);
                if (md5FileLine.length() != 32) {
                    row = asList(pathString, "MD5", "MD5 first line not 32 characters.");
                } else {
                    String originalFileName = pathString.substring(0, pathString.length() - ".md5".length());
                    HashCode md5calculated = com.google.common.io.Files.hash(new File(originalFileName), Hashing.md5());
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
     * main looks at all the files in a given part of an infomedia delivery and analyze each file, collecting
     * the result in a Excel file.
     * */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java " + Main.class.getName() + " spreadsheet.xsl infomedia-dump-dir");
        }

        File tmpDir = com.google.common.io.Files.createTempDir();

        /* Find all files in the given directory tree, and extract
         one or more row of cells for each file.  Create a spreadsheet corresponding to the rows.
         */

        List<List<String>> cellRows =
                Files.walk(Paths.get(args[1]))
                        .filter(Files::isRegularFile)
                        .flatMap(path -> Main.getRowsForPath(path, tmpDir))
                        .collect(toList());

        Collections.sort(cellRows, new CellRowComparator());
        cellRows.add(0, HEADERS); // How to mark rows as headers in workbook sheet

        try (OutputStream out = new FileOutputStream(args[0])) {
            POIHelpers.workbookFor(cellRows).write(out);
        }

        PathHelpers.deleteTreeBelowPath(tmpDir.toPath());
    }
}
