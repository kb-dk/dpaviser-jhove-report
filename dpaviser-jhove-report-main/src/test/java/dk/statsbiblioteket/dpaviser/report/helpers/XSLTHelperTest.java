package dk.statsbiblioteket.dpaviser.report.helpers;

import dk.statsbiblioteket.util.xml.XSLT;
import org.testng.annotations.Test;

import javax.xml.transform.TransformerException;

import static org.testng.AssertJUnit.assertEquals;

@Test
public class XSLTHelperTest {

    public void testJhoveJyp1XSLT() throws TransformerException {

        assertEquals("<!DOCTYPE html><html>\n" +
                "<table>\n" +
                "<tr>\n" +
                "<td>./JYP/2015/06/05/JYP20150605L14%230022.pdf</td><td>21</td><td>1</td><td>21</td><td>0</td><td>3</td><td>1</td><td>4</td><td>3</td><td>1</td>\n" +
                "</tr>\n" +
                "</table>\n" +
                "</html>\n",
                XSLT.transform(getClass().getResource("/pdf-process-jhove-output.xsl"),
                        getClass().getResourceAsStream("/jhove-jyp-1.xml"),
                        null).toString());
    }

//    public String applyXSLT(String xsltResourceName, String inputXMLResourceName) {
//        Transformer transformer = null;
//        try {
//            URL resource = checkNotNull(getClass().getResource(xsltResourceName), xsltResourceName + " not found");
//            transformer = XSLT.createTransformer(resource);
//        } catch (TransformerException e) {
//            throw new RuntimeException("could not create transformer for " + xsltResourceName, e);
//        }
//        Reader reader = new InputStreamReader(getClass().getResourceAsStream(inputXMLResourceName), Charsets.UTF_8);
//        StringWriter stringWriter = new StringWriter();
//
//        final XMLReader xmlReader;
//        try {
//            xmlReader = XMLReaderFactory.createXMLReader();
//        } catch (SAXException e) {
//            throw new RuntimeException("could not create transformer", e);
//        }
//        try {
//            transformer.transform(new SAXSource(xmlReader, new InputSource(reader)), new StreamResult(stringWriter));
//        } catch (TransformerException e) {
//            throw new RuntimeException("could not transform " + inputXMLResourceName + " with " + xsltResourceName, e);
//        }
//
//        return stringWriter.toString();
//    }
}

