package dk.statsbiblioteket.dpaviser.report;


import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import dk.statsbiblioteket.dpaviser.report.helpers.JSoupHelpers;
import dk.statsbiblioteket.dpaviser.report.jhove.JHoveHelpers;
import dk.statsbiblioteket.dpaviser.report.sax.ExceptionThrowingErrorHandler;
import dk.statsbiblioteket.util.xml.XSLT;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static javax.xml.xpath.XPathConstants.STRING;

public class MetadataExtractorPerPage {
    protected static DocumentBuilderFactory documentBuilderFactory;

    /**
     * Only return a single row - the multirow unpacking was too tricky for now
     */

    @SuppressWarnings("deprecation")
	protected static Map<String, List<String>> getZeroOrOneRowMappedByTypeForPath(Path path, File tmpDir) {

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
                firstRow.set(0, URLDecoder.decode(firstCell)); // URLDecode of paths. Hard in XSLT 1.0.

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

                List<String> row = new ArrayList<>(asList(pathString, "XML", "XML valid"));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='KKOD']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='PUBX']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='OFIL']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='PUGE']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='STATUS']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='PMOD']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='FOED']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='PMOD']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='PIND']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='PSID']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='PSEK']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='PSIN']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='PSNA']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='CCRE']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='UDGA']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='PDF']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='CMOD']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='READERSHIP']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='CIRCULATION']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='MEDIATYPE']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='COUNTRY']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='REGION']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='LIX']/@Value)", document, STRING));
                row.add((String) MetadataExtractorPerDelivery.noNamespacesXPath.evaluate("string(//Metadata[MetadataType/@FormalName='InfomediaMetadata']/Property[@FormalName='LANGUAGE']/@Value)", document, STRING));
                result.put("XML", row);
            } catch (SAXException e) {
                result.put("XML", asList(pathString, "XML", "XML not valid", e.getMessage()));
            } catch (ParserConfigurationException e) {
                throw new RuntimeException("cannot configure parser - bad Java?", e);
            } catch (XPathExpressionException e) {
                result.put("XML", asList(pathString, "XML", "XPath expression not valid", e.getMessage()));
                System.err.println(e.toString());
            } catch (IOException e) {
                result.put("XML", asList(pathString, "XML", "Cannot read input", e.getMessage()));
                e.printStackTrace();
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
}
