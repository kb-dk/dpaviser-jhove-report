package dk.statsbiblioteket.dpaviser.report.helpers;

import dk.statsbiblioteket.util.xml.XSLT;
import org.testng.annotations.Test;

import javax.xml.transform.TransformerException;
import java.util.List;

import static java.util.Arrays.asList;
import static org.testng.AssertJUnit.assertEquals;

@Test
public class XSLTHelperTest {

    public void testJhoveJyp1XSLT() throws TransformerException {

        String result = XSLT.transform(
                getClass().getResource("/pdf-process-jhove-output.xsl"),
                getClass().getResourceAsStream("/jhove-jyp-1.xml"),
                null
        ).toString();

        assertEquals("<!DOCTYPE html><html>\n" +
                        "<table>\n" +
                        "<tr>\n" +
                        "<td>./JYP/2015/06/05/JYP20150605L14%230022.pdf</td><td>PDF</td><td>411749</td><td>PDF</td><td>1.3</td><td>Well-Formed and valid</td><td>Linearized PDF</td><td>untitled</td><td>Acrobat Distiller Server 8.1.0 (Pentium Linux, Built: 2007-09-07)</td><td>Thu Jun 04 22:13:10 CEST 2015</td><td>Thu Jun 04 22:13:10 CEST 2015</td><td>21</td><td>1</td><td>21</td><td>0</td><td>3</td><td>1</td><td>4</td><td>3</td><td></td><td>1</td>\n" +
                        "</tr>\n" +
                        "</table>\n" +
                        "</html>\n",
                result);

        List<List<String>> l = JSoupHelpers.getFirstTableFromHTML(result);
        assertEquals(asList(asList("./JYP/2015/06/05/JYP20150605L14%230022.pdf", "PDF", "1.3","21", "1", "21", "0", "3", "1", "4", "3", "Font BaseFont DEMPLL+Arial-BoldMT Encoding Identity-H", "1")), l);
    }

}

