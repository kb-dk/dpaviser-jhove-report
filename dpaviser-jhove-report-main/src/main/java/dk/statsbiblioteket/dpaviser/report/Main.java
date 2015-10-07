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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static javax.xml.xpath.XPathConstants.STRING;

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
     * main looks at all the files in a given part of an infomedia delivery and analyze each file, collecting the
     * results and mailing them as attachments
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

        List<List<String>> metadataCellRows =
                Files.walk(startPath)
                        .filter(Files::isRegularFile)
                        .flatMap(path -> Main.getRowsForPath(path, tmpDir))
                        .collect(toList());

        Collections.sort(metadataCellRows, new CellRowComparator());
        metadataCellRows.add(0, HEADERS); // How to mark rows as headers in workbook sheet

        // and now create a mail for the desired recipients.
        // Recipe http://www.tutorialspoint.com/java/java_sending_email.htm

        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
        String today = sdfDate.format(new Date());

        final byte[] report1bytes;
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            POIHelpers.workbookFor(numberOfPagesInSectionCellRows).write(baos);
            report1bytes = baos.toByteArray();
        }
        final byte[] report2bytes;
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            POIHelpers.workbookFor(metadataCellRows).write(baos);
            report2bytes = baos.toByteArray();
        }

        Session session = Session.getDefaultInstance(System.getProperties());

        Multipart multipart = new MimeMultipart();

        Message message = new MimeMessage(session);
        // use "mail.from" system property if default is not usable!
        //message.setFrom(new InternetAddress("do-not-reply"));
        message.addRecipients(Message.RecipientType.TO, addresses);
        message.setSubject("Infomedia upload reports for " + new File(args[0]).getAbsolutePath());
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("Se vedh\u00e6ftede rapporter.");
            multipart.addBodyPart(messageBodyPart);
        }
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            DataSource source = new ByteArrayDataSource(report1bytes, "application/vnd.ms-excel");
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName("report1-" + today + ".xls");
            multipart.addBodyPart(messageBodyPart);
        }
        {
            BodyPart messageBodyPart = new MimeBodyPart();
            DataSource source = new ByteArrayDataSource(report2bytes, "application/vnd.ms-excel");
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName("report2-" + today + ".xls");
            multipart.addBodyPart(messageBodyPart);
        }
        message.setContent(multipart);
        Transport.send(message);

        PathHelpers.deleteTreeBelowPath(tmpDir.toPath());
    }


    private static void extractMetadataForPagesInSectionReport(Path path, Map<String, Long> pdfsForSection, Map<String, String> sectionFor) {
        String pathString = path.toString();

        Pattern infomediaPDF = Pattern.compile(".*/([A-Z]{3}[^/]+)[A-Z](\\d.)#\\d\\d\\d\\d\\.pdf$");
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

}
