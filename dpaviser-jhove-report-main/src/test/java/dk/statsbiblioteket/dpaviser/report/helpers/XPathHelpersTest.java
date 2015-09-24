package dk.statsbiblioteket.dpaviser.report.helpers;

import dk.statsbiblioteket.dpaviser.report.ExtractRowsFromRepInfoNodes;
import dk.statsbiblioteket.util.xml.DOM;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathExpressionException;
import java.io.InputStream;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static dk.statsbiblioteket.dpaviser.report.ExtractRowsFromRepInfoNodes.COUNT_CIDFONTTYPE0_FONT;
import static dk.statsbiblioteket.dpaviser.report.ExtractRowsFromRepInfoNodes.COUNT_FLATEDECODE;
import static dk.statsbiblioteket.dpaviser.report.ExtractRowsFromRepInfoNodes.COUNT_JPEG;
import static dk.statsbiblioteket.dpaviser.report.ExtractRowsFromRepInfoNodes.COUNT_OTHER_IMAGE_ENCODINGS;
import static dk.statsbiblioteket.dpaviser.report.ExtractRowsFromRepInfoNodes.COUNT_TRUETYPE0_FONT;
import static dk.statsbiblioteket.dpaviser.report.ExtractRowsFromRepInfoNodes.COUNT_TYPE0_FONT;
import static dk.statsbiblioteket.dpaviser.report.ExtractRowsFromRepInfoNodes.COUNT_TYPE1_FONT;
import static dk.statsbiblioteket.dpaviser.report.ExtractRowsFromRepInfoNodes.COUNT_UNCOMPRESSED;
import static dk.statsbiblioteket.dpaviser.report.helpers.XPathHelpers.*;
import static dk.statsbiblioteket.dpaviser.report.helpers.XPathHelpers.xpath;
import static java.util.stream.Collectors.toList;
import static org.testng.Assert.assertEquals;

public class XPathHelpersTest {

    @Test
    public void testSinglePDFJHoveOutput() throws XPathExpressionException {
        InputStream is = checkNotNull(getClass().getResourceAsStream("/single-pdf-jhove-output.xml"));
        Document dom = DOM.streamToDOM(is, true);

        List<Node> l = getNodesFor(dom, "/j:jhove/j:repInfo").collect(toList());
        assertEquals(l.size(), 1);
        for(Node node: l) {
            assertEquals(xpath(COUNT_JPEG).evaluate(node), "1", "1 jpeg encoded billede");
            assertEquals(xpath(COUNT_UNCOMPRESSED).evaluate(node), "0", "0 uncompressed billede");
            assertEquals(xpath(COUNT_FLATEDECODE).evaluate(node), "0", "0 FlateDecode billede");
            assertEquals(xpath(COUNT_OTHER_IMAGE_ENCODINGS).evaluate(node), "0", "0 uncompressed billede");

            assertEquals(xpath(COUNT_TYPE1_FONT).evaluate(node), "7", "type 1");
            assertEquals(xpath(COUNT_TYPE0_FONT).evaluate(node), "0", "type 0");
            assertEquals(xpath(COUNT_TRUETYPE0_FONT).evaluate(node), "0", "truetype");
            assertEquals(xpath(COUNT_CIDFONTTYPE0_FONT).evaluate(node), "0", "cidfonttype0");


            assertEquals(xpath("./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Info']/j:values/j:property[j:name/text() = 'Producer']/j:values/j:value/text()").evaluate(node), "Acrobat Distiller Server 8.1.0 (Pentium Linux, Built: 2007-09-07)");
        }
     }
}
