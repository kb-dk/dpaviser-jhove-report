package dk.statsbiblioteket.dpaviser.report.helpers;

import dk.statsbiblioteket.util.xml.DOM;
import org.testng.annotations.Test;
import org.w3c.dom.Document;

import java.util.ArrayList;

import static java.util.stream.Collectors.toList;
import static org.testng.Assert.assertEquals;

public class XPathHelpersTest {
    @Test
    public void testSimpleJHoveOutput() {
        String xml = String.join("\n",
                "<jhove xmlns='http://hul.harvard.edu/ois/xml/ns/jhove' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' date='2015-09-15' name='name' release='release' xsi:schemaLocation='http://hul.harvard.edu/ois/xml/ns/jhove http://hul.harvard.edu/ois/xml/xsd/jhove/1.6/jhove.xsd'>",
                " <date>2015-09-22T14:45:49+02:00</date>",
                " <repInfo uri='/home/tra/Skrivebord/infomed/JYP/2015/06/05/JYP20150605L11%230001.pdf'>",
                " </repInfo>",
                "</jhove>");

        Document dom = DOM.stringToDOM(xml, true);
        assertEquals(XPathHelpers.getNodesFor(dom, "/j:jhove").collect(toList()), new ArrayList<String>(),
                "one repInfo node expected");
        assertEquals(XPathHelpers.getNodesFor(dom, "/j:jhove/j:repInfo").collect(toList()), new ArrayList<String>(),
                "one repInfo node expected");
    }
}
