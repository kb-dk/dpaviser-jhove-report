package dk.statsbiblioteket.dpaviser.report;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import dk.statsbiblioteket.dpaviser.report.helpers.UtilException;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.google.common.io.Files.getFileExtension;
import static dk.statsbiblioteket.dpaviser.report.helpers.XPathHelpers.xpath;


/**
 * Handles a single &lt;j:repInfo&gt; node in a full jhove output.  Return one or more "rows" of "cell values"
 * (corresponding to the number of rows to go in the final spreadsheet). Output must be expected to be sorted later.
 * <p>
 * In separate class as logic is expected to be too complex to fit comfortably in a lambda expression.
 */
public class ExtractRowsFromRepInfoNodes implements UtilException.Function_WithExceptions<Node, Stream<List<String>>> {

    public static final String COUNT_JPEG = "count(.//j:property[j:name='PDFMetadata']//j:property[j:name='Images']//" +
            "j:property[j:name='Image']//j:property[j:name='NisoImageMetadata']//mix:Compression[mix:compressionScheme='JPEG'])";
    public static final String COUNT_UNCOMPRESSED = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Images']/j:values/j:property[j:name/text() = 'Image']/j:values/j:property[j:name/text() = 'NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='Uncompressed'])";
    public static final String COUNT_FLATEDECODE = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Images']/j:values/j:property[j:name/text() = 'Image']/j:values/j:property[j:name/text() = 'NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='FlateDecode'])";
    public static final String COUNT_OTHER_IMAGE_ENCODINGS = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Images']/j:values/j:property[j:name/text() = 'Image']/j:values/j:property[j:name/text() = 'NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme) - " + COUNT_JPEG + " - " + COUNT_FLATEDECODE + " - " + COUNT_UNCOMPRESSED;
    public static final String COUNT_TYPE1_FONT = "count(.//j:property[j:name='PDFMetadata']//j:property[j:name='Fonts']//j:property[j:name='Type1']//j:property[j:name='Font'])";
    public static final String COUNT_TYPE0_FONT = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Fonts']/j:values/j:property[j:name/text() = 'Type0']/j:values/j:property[j:name/text() = 'Font'])";
    public static final String COUNT_TRUETYPE0_FONT = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Fonts']/j:values/j:property[j:name/text() = 'TrueType0']/j:values/j:property[j:name/text() = 'Font'])";
    public static final String COUNT_CIDFONTTYPE0_FONT = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Fonts']/j:values/j:property[j:name/text() = 'CIDFontType0']/j:values/j:property[j:name/text() = 'Font'])";
    public static final String COUNT_PAGES = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Pages']/j:values/j:property[j:name/text() = 'Page'])";

    @Override
    public Stream<List<String>> apply(Node repInfoNode) throws XPathExpressionException, URISyntaxException, IOException {
        List<List<String>> rows = new ArrayList<>();

        // The file name is urlencoded so go through a URI to decode.
        URI uri = new URI(xpath("@uri").evaluate(repInfoNode));

        List<String> row = new ArrayList<>();

        String uriPath = uri.getPath();

        switch (getFileExtension(uriPath).toLowerCase()) {
            case "md5":
                row.add(uriPath);
                String md5FileLine = Files.readAllLines(Paths.get(uriPath)).get(0);

                if (md5FileLine.length() == 32) {
                    File fileToHash = new File(uriPath.substring(0, uriPath.length() - ".md5".length()));
                    HashCode md5calculated = com.google.common.io.Files.hash(fileToHash, Hashing.md5());

                    if (md5FileLine.equalsIgnoreCase(md5calculated.toString())) {
                        row.add("MD5 valid");
                    } else {
                        row.add("MD5 " + md5FileLine + " stated in file, calculated " + md5calculated);
                    }
                } else {
                    row.add("MD5 first line not 32 characters.");
                }
                rows.add(row);
                break;
            case "xml": // Infomedia NewsXML dialect -- awaiting feedback from KFC.
                row.add(uriPath);
                row.add(xpath("./j:size/text()").evaluate(repInfoNode));
                row.add(xpath("./j:profiles/j:profile/text()").evaluate(repInfoNode));
                row.add(xpath("./j:format/text()").evaluate(repInfoNode));
                row.add(xpath("./j:version/text()").evaluate(repInfoNode));
                row.add(xpath("./j:status/text()").evaluate(repInfoNode));
                rows.add(row);
                break;
            case "pdf": // https://github.com/statsbiblioteket/dpaviser-jhove-report/blob/master/PDF-report-elements.md
                row.add(uriPath);
                row.add(xpath("./j:size/text()").evaluate(repInfoNode));
                row.add(xpath("./j:profiles/j:profile/text()").evaluate(repInfoNode));
                row.add(xpath("./j:format/text()").evaluate(repInfoNode));
                row.add(xpath("./j:version/text()").evaluate(repInfoNode));
                row.add(xpath("./j:status/text()").evaluate(repInfoNode));
                //
                row.add(xpath(COUNT_JPEG).evaluate(repInfoNode));
                row.add(xpath(COUNT_UNCOMPRESSED).evaluate(repInfoNode));
                row.add(xpath(COUNT_FLATEDECODE).evaluate(repInfoNode));
                row.add(xpath(COUNT_OTHER_IMAGE_ENCODINGS).evaluate(repInfoNode));

                row.add(xpath(COUNT_TYPE1_FONT).evaluate(repInfoNode));
                row.add(xpath(COUNT_TYPE0_FONT).evaluate(repInfoNode));
                row.add(xpath(COUNT_TRUETYPE0_FONT).evaluate(repInfoNode));
                row.add(xpath(COUNT_CIDFONTTYPE0_FONT).evaluate(repInfoNode));

                row.add(xpath(COUNT_PAGES).evaluate(repInfoNode));

                row.add("XX");
                System.out.println(row);
                rows.add(row);
                break;
            default:
                // don't generate any cells for unrecognized file extensions.
                break;
        }

        return rows.stream();
    }
}
