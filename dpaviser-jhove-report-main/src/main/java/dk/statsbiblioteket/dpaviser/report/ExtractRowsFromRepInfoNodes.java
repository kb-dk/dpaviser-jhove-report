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
 * (corresponding to the number of rows to go in the final spreadsheet). Output must be expected to be sorted.
 * <p>
 * In separate class as logic is expected to be too complex to fit comfortably in a lambda expression.
 */
public class ExtractRowsFromRepInfoNodes implements Function<Node, Stream<List<String>>> {
    @Override
    public Stream<List<String>> apply(Node repInfoNode) {

        List<List<String>> rows = new ArrayList<>();
        {
            List<String> row = new ArrayList<>();

            URI uri;
            String uriString = null;
            try {
                uriString = XPathHelpers.evalXPathAndApply("@uri").apply(repInfoNode);
                uri = new URI(uriString);
            } catch (URISyntaxException e) {
                throw new RuntimeException("uriString=" + uriString, e);
            }

            String uriPath = uri.getPath();

            Path path = Paths.get(uriPath);

            row.add(uriPath);
            if (uriPath.endsWith(".md5")) { // do checksum check
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
                        row.add("MD5 " + md5FileLine + " expected, found " + md5calculated);
                    }
                } else {
                    row.add("MD5 first line not 32 characters.");
                }
            } else {
                // initial few cells, more will come.
                row.add(XPathHelpers.evalXPathAndApply("./j:size/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPathAndApply("./j:format/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPathAndApply("./j:version/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPathAndApply("./j:mimeType/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPathAndApply("./j:status/text()").apply(repInfoNode));
                row.add(XPathHelpers.evalXPathAndApply("./j:messages/j:message/text()").apply(repInfoNode));
            }
            rows.add(row);
        }
        return rows.stream();
    }
}
