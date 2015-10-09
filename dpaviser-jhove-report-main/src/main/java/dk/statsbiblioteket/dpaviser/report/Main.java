package dk.statsbiblioteket.dpaviser.report;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import dk.statsbiblioteket.dpaviser.report.helpers.CellRowComparator;
import dk.statsbiblioteket.dpaviser.report.helpers.JSoupHelpers;
import dk.statsbiblioteket.dpaviser.report.helpers.POIHelpers;
import dk.statsbiblioteket.dpaviser.report.helpers.PathHelpers;
import dk.statsbiblioteket.dpaviser.report.jhove.JHoveHelpers;
import dk.statsbiblioteket.dpaviser.report.sax.ExceptionThrowingErrorHandler;
import dk.statsbiblioteket.util.xml.DOM;
import dk.statsbiblioteket.util.xml.XSLT;
import org.w3c.dom.Document;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static javax.xml.xpath.XPathConstants.STRING;

public class Main {

    public static final List<String> PDF_HEADERS = asList("Name", "Type", "Version", "PDF:JPEG", "PDF:UNCOMPRESSED", "PDF:FLATEDECODE",
            "PDF:OTHERIMAGES", "PDF:TYPE0", "PDF:TYPE1", "PDF:TRUETYPE", "PDF:OTHERFONTS", "PDF:UNEMBEDDEDFONTS",
            "PDF:Pages");

    protected static DocumentBuilderFactory documentBuilderFactory;

    /**
     * Only return a single row - the multirow unpacking was too tricky for now
     */

    protected static Map<String, List<String>> getRowKeyValueForPath(Path path, File tmpDir) {

        Map<String, List<String>> result = new HashMap<>();

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
                String firstCell = firstRow.get(0);
                firstRow.set(0, URLDecoder.decode(firstCell)); // URLDecode. Hard in XSLT 1.0.

                result.put("PDF", firstRow);
            } catch (TransformerException e) {
                throw new RuntimeException("could not transform jhove output", e);
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
                result.put("XML", asList(pathString, "XML", "XML valid"));

                // TODO:  Add more XML information from KFC.
            } catch (Exception e) {
                result.put("XML", asList(pathString, "XML", "XML not valid", e.getMessage()));
            }
        } else if (pathString.endsWith(".md5")) {
            // compare value in this file with value calculated.
            try {
                String md5FileLine = Files.readAllLines(path).get(0);
                if (md5FileLine.length() != 32) {
                    result.put("MD5", asList(pathString, "MD5", "MD5 first line not 32 characters."));
                } else {
                    String originalFileName = pathString.substring(0, pathString.length() - ".md5".length());
                    HashCode md5calculated = com.google.common.io.Files.hash(new File(originalFileName), Hashing.md5());
                    if (md5FileLine.equalsIgnoreCase(md5calculated.toString())) {
                        // all well, do not report anything
                    } else {
                        result.put("MD5", asList(pathString, "MD5", "MD5 " + md5FileLine + " stated in file, calculated " + md5calculated));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("could not process " + path, e);
            }
        } else {
            // Ignore other files.
        }
        return result;
    }

    public static byte[] workbookBytesForCellRows(List<List<String>> numberOfPagesInSectionCellRows) throws IOException {
        byte[] report1bytes;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        POIHelpers.workbookFor(numberOfPagesInSectionCellRows).write(baos);
        report1bytes = baos.toByteArray();
        return report1bytes;
    }

    public static void sendMail(String filePathName, InternetAddress[] addresses, List<List<String>> md5cellRows, String today, byte[] report1bytes, byte[] report2bytes, byte[] report3bytes) throws MessagingException {
        Session session = Session.getDefaultInstance(System.getProperties());

        Multipart multipart = new MimeMultipart();

        Message message = new MimeMessage(session);
        // use "mail.from" system property if default is not usable!
        message.addRecipients(Message.RecipientType.TO, addresses);
        message.setSubject("Infomedia upload reports for " + filePathName);
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            String bodyText = "\n";
            for (List<String> row : md5cellRows) {
                bodyText = bodyText + String.join(" ", row) + "\n";
            }
            messageBodyPart.setText(bodyText);
            multipart.addBodyPart(messageBodyPart);
        }
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            DataSource source = new ByteArrayDataSource(report1bytes, "application/vnd.ms-excel");
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName("sektioner-" + today + ".xls");
            multipart.addBodyPart(messageBodyPart);
        }
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            DataSource source = new ByteArrayDataSource(report2bytes, "application/vnd.ms-excel");
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName("pdf-" + today + ".xls");
            multipart.addBodyPart(messageBodyPart);
        }
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            DataSource source = new ByteArrayDataSource(report3bytes, "application/vnd.ms-excel");
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName("xml-" + today + ".xls");
            multipart.addBodyPart(messageBodyPart);
        }
        message.setContent(multipart);
        Transport.send(message);
    }


    private static void extractMetadataForPagesInSectionReport(Path path, Map<String, Long> pdfsForSection, Map<String, String> sectionFor) {
        String pathString = path.toString();

        Pattern infomediaPDF = Pattern.compile(".*[/\\\\]([A-Z]{3}[^/]+)[A-Z](\\d.)#\\d\\d\\d\\d\\.pdf$");
        Matcher matcher = infomediaPDF.matcher(pathString);
        if (matcher.matches()) {
            // $seen{"$1?$2"}++;
            String key = matcher.group(1) + "?" + matcher.group(2);
            pdfsForSection.put(key, pdfsForSection.getOrDefault(key, 0L) + 1);
        } else if (pathString.endsWith(".xml")) {
            // look inside XML to get section name and the PDF file it belongs to.
            Document document = null;
            try {
                document = DOM.streamToDOM(new FileInputStream(path.toFile()), false);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(pathString + " not found");
            }

            XPath xpath = XPathFactory.newInstance().newXPath();
            String section = null;
            try {
                section = (String) xpath.compile("string(//Property[@FormalName=\"PSNA\"]/@Value)").evaluate(document, STRING);
            } catch (XPathExpressionException e) {
                throw new RuntimeException("section", e);
            }

            // <media-reference exists="1">JYP\2015\08\07\JYP20150807V15#0007.pdf</media-reference>
            String pdfFileName = null;
            try {
                pdfFileName = (String) xpath.compile("//media[@media-type=\"PDF\"]/media-reference[@exists=1][1]/text()").evaluate(document, STRING);
            } catch (XPathExpressionException e) {
                throw new RuntimeException("pdfFileName", e);
            }
            pdfFileName = pdfFileName.trim();

            Pattern embeddedPDF = Pattern.compile(".*[/\\\\]([A-Z]{3}[^/\\\\]+)[A-Z](\\d.)#\\d\\d\\d\\d\\.pdf$");
            Matcher matcher1 = embeddedPDF.matcher(pdfFileName);
            if (matcher1.matches()) {
                String key = matcher1.group(1) + "?" + matcher1.group(2);
                sectionFor.put(key, section); // many xml files may point to same pdf - last one wins.
            } else {
                // some XML files do not have PDF link
            }
        } else {
            // skip other files
        }
    }


    /**
     * <p> main looks at all the files in a given part of an Infomedia delivery and analyze each file, collecting the
     * results and mailing them as attachments.</p> <p>Default is to try to send the mail on port 25 on localhost with a
     * calculated sender address.  If this fails for any reason, set the appropriate JavaMail properties as system
     * properties to override the default behavior.</p>
     *
     * @param args args[0] is the relative path to the informedia-dump-dir to process (may be inside).  args[1] is the
     *             comma-separated emails to send mail to (no spaces).
     * @throws Exception fail fast in case of problems.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java " + Main.class.getName() + " infomedia-dump-dir comma-separated-emails");
        }

        //
        Path startPath = Paths.get(args[0]);
        InternetAddress[] addresses = InternetAddress.parse(args[1]);
        //

        File tmpDir = com.google.common.io.Files.createTempDir();

        Map<String, Long> pdfsForSection = new TreeMap();

        Map<String, String> sectionFor = new TreeMap();

        Files.walk(startPath)
                .filter(Files::isRegularFile)
                .forEach(path -> extractMetadataForPagesInSectionReport(path, pdfsForSection, sectionFor));

        List<List<String>> numberOfPagesInSectionCellRows =
                pdfsForSection.entrySet().stream().map(
                        e -> asList(e.getKey(), pdfsForSection.get(e.getKey()) + " sider", sectionFor.get(e.getKey()))
                ).collect(toList());


        /* Find all files in the given directory tree, and extract
         one or more row of cells for each file.  Create a spreadsheet corresponding to the rows.
         */
        Map<String, List<List<String>>> metadataCellRowsMap =
                Files.walk(startPath)
                        .filter(Files::isRegularFile)
                        .map(path -> Main.getRowKeyValueForPath(path, tmpDir))
                        .peek(e -> System.out.println(e))
                        .flatMap(map -> map.entrySet().stream())
                                // Combine Map.Entries - http://stackoverflow.com/a/31488386/53897
                        .collect(Collectors.groupingBy(entry -> entry.getKey(),
                                Collectors.mapping(entry -> entry.getValue(), Collectors.toList())));


        // ["PDF"->[["1","2"],["3","4"]], "XML"-> ..., "MD5"->...]

        for (List<List<String>> cellRows : metadataCellRowsMap.values()) {
            Collections.sort(cellRows, new CellRowComparator());
        }
        metadataCellRowsMap.get("PDF").add(0, PDF_HEADERS); // How to mark rows as headers in workbook sheet

        // and now create a mail for the desired recipients.
        // Recipe http://www.tutorialspoint.com/java/java_sending_email.htm

        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
        String today = sdfDate.format(new Date());


        sendMail(new File(args[0]).getAbsolutePath(),
                addresses,
                metadataCellRowsMap.get("MD5"),
                today,
                workbookBytesForCellRows(numberOfPagesInSectionCellRows),
                workbookBytesForCellRows(metadataCellRowsMap.get("PDF")),
                workbookBytesForCellRows(metadataCellRowsMap.get("XML"))
        );

        PathHelpers.deleteTreeBelowPath(tmpDir.toPath());
    }
}
