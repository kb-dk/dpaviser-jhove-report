package dk.statsbiblioteket.dpaviser.report;


import dk.statsbiblioteket.util.xml.DOM;
import org.w3c.dom.Document;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.xml.xpath.XPathConstants.STRING;

public class MetadataExtractorPerDelivery {
    final static XPath noNamespacesXPath = XPathFactory.newInstance().newXPath();
    // optimize patterns and xpath compilations.
    static Pattern infomediaPDFPattern = Pattern.compile(".*[/\\\\]([A-Z]{3}[^/]+)[A-Z](\\d.)#\\d\\d\\d\\d\\.pdf$");
    static XPathExpression sectionNameExpression;
    static XPathExpression pdfFileNameExpression;
    private static final Pattern embeddedPDFPattern = Pattern.compile(".*[/\\\\]([A-Z]{3}[^/\\\\]+)[A-Z](\\d.)#\\d\\d\\d\\d\\.pdf$");

    /** Extract metadata from a single page for the section report.  For PDF increment the count for a "page is in this section" key.
     * For XML extract the section header and the name of the PDF file it belongs to and store that for later. */

    static void extractMetadataForPagesInSectionReport(Path path, Map<String, Long> pdfsForSection, Map<String, String> sectionFor) {
        String pathString = path.toString();

        Matcher matcher = infomediaPDFPattern.matcher(pathString);
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

            String sectionXPath = null;
            try {
                if (sectionNameExpression == null) {
                    sectionNameExpression = noNamespacesXPath.compile("string(//Property[@FormalName=\"PSNA\"]/@Value)");
                }
                sectionXPath = (String) sectionNameExpression.evaluate(document, STRING);
            } catch (XPathExpressionException e) {
                throw new RuntimeException("section", e);
            }

            // <media-reference exists="1">JYP\2015\08\07\JYP20150807V15#0007.pdf</media-reference>
            String pdfFileName = null;
            try {
                if (pdfFileNameExpression == null) {
                    pdfFileNameExpression = noNamespacesXPath.compile("//media[@media-type=\"PDF\"]/media-reference[@exists=1][1]/text()");
                }
                pdfFileName = (String) pdfFileNameExpression.evaluate(document, STRING);
            } catch (XPathExpressionException e) {
                throw new RuntimeException("pdfFileName", e);
            }
            pdfFileName = pdfFileName.trim();

            Matcher matcher1 = embeddedPDFPattern.matcher(pdfFileName);
            if (matcher1.matches()) {
                String key = matcher1.group(1) + "?" + matcher1.group(2);
                sectionFor.put(key, sectionXPath); // many xml files may point to same pdf - last one wins.
            } else {
                // some XML files do not have PDF link
            }
        } else {
            // skip other files
        }
    }
}
