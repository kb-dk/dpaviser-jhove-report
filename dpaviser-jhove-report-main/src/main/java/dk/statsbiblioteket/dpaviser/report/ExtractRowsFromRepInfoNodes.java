package dk.statsbiblioteket.dpaviser.report;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
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
        try {
            return apply0(repInfoNode);
        } catch (Exception e) {
            throw new RuntimeException("apply", e);
        }
    }

    public Stream<List<String>> apply0(Node repInfoNode) throws URISyntaxException, IOException {

        List<List<String>> rows = new ArrayList<>();
        {
            List<String> row = new ArrayList<>();

            URI uri = new URI(MainHelpers.eval("@uri").apply(repInfoNode));

            String uriPath = uri.getPath();

            Path path = Paths.get(uriPath);

            row.add(uriPath);
            if (uriPath.endsWith(".md5")) { // do checksum check
                String md5FileLine = Files.readAllLines(path).get(0);
                if (md5FileLine.length() == 32) {
                    File fileToHash = new File(uriPath.substring(0, uriPath.length() - ".md5".length()));
                    HashCode md5calculated = com.google.common.io.Files.hash(fileToHash, Hashing.md5());
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
                row.add(MainHelpers.eval("./j:size/text()").apply(repInfoNode));
                row.add(MainHelpers.eval("./j:format/text()").apply(repInfoNode));
                row.add(MainHelpers.eval("./j:version/text()").apply(repInfoNode));
                row.add(MainHelpers.eval("./j:mimeType/text()").apply(repInfoNode));
                row.add(MainHelpers.eval("./j:status/text()").apply(repInfoNode));
                row.add(MainHelpers.eval("./j:messages/j:message/text()").apply(repInfoNode));
            }
            rows.add(row);
        }
        return rows.stream();
    }
}
