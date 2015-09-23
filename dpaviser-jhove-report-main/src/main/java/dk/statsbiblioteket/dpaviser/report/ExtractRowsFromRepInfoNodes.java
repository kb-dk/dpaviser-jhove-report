package dk.statsbiblioteket.dpaviser.report;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import dk.statsbiblioteket.dpaviser.report.helpers.XPathHelpers;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;


/**
 * Handles a single &lt;j:repInfo&gt; node in a full jhove output.  Return one or more "rows" of "cell values"
 * (corresponding to the number of rows to go in the final spreadsheet). Output must be expected to be sorted later.
 * <p>
 * In separate class as logic is expected to be too complex to fit comfortably in a lambda expression.
 */
public class ExtractRowsFromRepInfoNodes implements Function<Node, Stream<List<String>>> {
    public static final String COUNT_JPEG = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Images']/j:values/j:property[j:name/text() = 'Image']/j:values/j:property[j:name/text() = 'NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='JPEG'])";
    public static final String COUNT_UNCOMPRESSED = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Images']/j:values/j:property[j:name/text() = 'Image']/j:values/j:property[j:name/text() = 'NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='Uncompressed'])";
    public static final String COUNT_FLATEDECODE = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Images']/j:values/j:property[j:name/text() = 'Image']/j:values/j:property[j:name/text() = 'NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()='FlateDecode'])";
    public static final String COUNT_OTHER_IMAGE_ENCODINGS = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Images']/j:values/j:property[j:name/text() = 'Image']/j:values/j:property[j:name/text() = 'NisoImageMetadata']/j:values/j:value/mix:mix/mix:BasicDigitalObjectInformation/mix:Compression/mix:compressionScheme[text()!='JPEG' and text()!='Uncompressed' and text()!='FlateDecode'])";
    public static final String COUNT_TYPE1_FONT = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Fonts']/j:values/j:property[j:name/text() = 'Type1']/j:values/j:property[j:name/text() = 'Font'])";
    public static final String COUNT_TYPE0_FONT = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Fonts']/j:values/j:property[j:name/text() = 'Type0']/j:values/j:property[j:name/text() = 'Font'])";
    public static final String COUNT_TRUETYPE0_FONT = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Fonts']/j:values/j:property[j:name/text() = 'TrueType0']/j:values/j:property[j:name/text() = 'Font'])";
    public static final String COUNT_CIDFONTTYPE0_FONT = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Fonts']/j:values/j:property[j:name/text() = 'CIDFontType0']/j:values/j:property[j:name/text() = 'Font'])";
    public static final String COUNT_PAGES = "count(./j:properties/j:property[j:name/text()='PDFMetadata']/j:values/j:property[j:name/text() = 'Pages']/j:values/j:property[j:name/text() = 'Page'])";

    @Override
    public Stream<List<String>> apply(Node repInfoNode) {
        List<List<String>> rows = new ArrayList<>();

        // The file name is urlencoded so go through a URI to decode.
        URI uri;
        String uriString = null;
        try {
            uriString = XPathHelpers.evalXPath("@uri").apply(repInfoNode);
            uri = new URI(uriString);
        } catch (URISyntaxException e) {
            throw new RuntimeException("uriString=" + uriString, e);
        }

        List<String> row = new ArrayList<>();

        String uriPath = uri.getPath();
        Path path = Paths.get(uriPath);

        switch (com.google.common.io.Files.getFileExtension(uriPath).toLowerCase()) {
            case "md5":
                row.add(uriPath);
                String md5FileLine = null;

                try {
                    md5FileLine = Files.readAllLines(path).get(0);
                } catch (IOException e) {
                    throw new RuntimeException("cannot read lines from " + uriPath, e);
                }

                if (md5FileLine.length() == 32) {
                    File fileToHash = new File(uriPath.substring(0, uriPath.length() - ".md5".length()));
                    HashCode md5calculated = null;

                    try {
                        md5calculated = com.google.common.io.Files.hash(fileToHash, Hashing.md5());
                    } catch (IOException e) {
                        throw new RuntimeException("Cannot hash " + fileToHash.getAbsolutePath(), e);
                    }

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
                row.add(XPathHelpers.evalXPath("./j:size/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPath("./j:profiles/j:profile/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPath("./j:format/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPath("./j:version/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPath("./j:status/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPath("./j:profiles/j:profile/text()").apply(repInfoNode));
                rows.add(row);
                break;
            case "pdf": // https://github.com/statsbiblioteket/dpaviser-jhove-report/blob/master/PDF-report-elements.md
                row.add(uriPath);
                row.add(XPathHelpers.evalXPath("./j:size/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPath("./j:profiles/j:profile/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPath("./j:format/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPath("./j:version/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPath("./j:status/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPath("./j:profiles/j:profile/text()").apply(repInfoNode));
                //
                row.add(XPathHelpers.evalXPath(COUNT_JPEG).apply(repInfoNode));
                row.add(XPathHelpers.evalXPath(COUNT_UNCOMPRESSED).apply(repInfoNode));
                row.add(XPathHelpers.evalXPath(COUNT_FLATEDECODE).apply(repInfoNode));
                row.add(XPathHelpers.evalXPath(COUNT_OTHER_IMAGE_ENCODINGS).apply(repInfoNode));

                row.add(XPathHelpers.evalXPath(COUNT_TYPE1_FONT).apply(repInfoNode));
                row.add(XPathHelpers.evalXPath(COUNT_TYPE0_FONT).apply(repInfoNode));
                row.add(XPathHelpers.evalXPath(COUNT_TRUETYPE0_FONT).apply(repInfoNode));
                row.add(XPathHelpers.evalXPath(COUNT_CIDFONTTYPE0_FONT).apply(repInfoNode));

                row.add(XPathHelpers.evalXPath(COUNT_PAGES).apply(repInfoNode));

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
