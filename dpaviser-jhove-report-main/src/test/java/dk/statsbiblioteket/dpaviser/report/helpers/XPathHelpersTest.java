package dk.statsbiblioteket.dpaviser.report.helpers;

import dk.statsbiblioteket.dpaviser.report.ExtractRowsFromRepInfoNodes;
import dk.statsbiblioteket.util.xml.DOM;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.InputStream;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static dk.statsbiblioteket.dpaviser.report.helpers.XPathHelpers.evalXPath;
import static java.util.stream.Collectors.toList;
import static org.testng.Assert.assertEquals;

public class XPathHelpersTest {

    @Test
    public void testSinglePDFJHoveOutput() {
        InputStream is = checkNotNull(getClass().getResourceAsStream("/single-pdf-jhove-output.xml"));
        Document dom = DOM.streamToDOM(is, true);

        List<Node> l = XPathHelpers.getNodesFor(dom, "/j:jhove/j:repInfo").collect(toList());
        assertEquals(l.size(), 1);
        for(Node node: l) {
            assertEquals(evalXPath(ExtractRowsFromRepInfoNodes.COUNT_JPEG).apply(node), "1", "1 jpeg encoded billede");
            assertEquals(evalXPath(ExtractRowsFromRepInfoNodes.COUNT_UNCOMPRESSED).apply(node), "0", "0 uncompressed billede");
            assertEquals(evalXPath(ExtractRowsFromRepInfoNodes.COUNT_FLATEDECODE).apply(node), "0", "0 FlateDecode billede");
            assertEquals(evalXPath(ExtractRowsFromRepInfoNodes.COUNT_OTHER_IMAGE_ENCODINGS).apply(node), "0", "0 uncompressed billede");
            assertEquals(evalXPath("./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Info']/j:values/j:property[j:name/text() = 'Producer']/j:values/j:value/text()").apply(node), "Acrobat Distiller Server 8.1.0 (Pentium Linux, Built: 2007-09-07)");
        }
     }
}
