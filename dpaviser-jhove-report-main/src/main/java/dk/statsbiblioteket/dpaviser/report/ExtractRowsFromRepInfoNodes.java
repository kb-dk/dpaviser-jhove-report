package dk.statsbiblioteket.dpaviser.report;

import org.w3c.dom.Node;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;


/**
 * Handles a single &lt;j:repInfo&gt; node in a full jhove output.  Return one or more "rows" of "cell values"
 * (corresponding to the number of rows to go in the final spreadsheet). Output must be expected to be sorted.
 * <p/>
 * In separate class as logic is expected to be too complex to fit comfortably in a lambda expression.
 */
public class ExtractRowsFromRepInfoNodes implements Function<Node, Stream<List<String>>> {
    @Override
    public Stream<List<String>> apply(Node repInfoNode) {
        try {
            return apply0(repInfoNode);
        } catch (URISyntaxException e) {
            throw new RuntimeException("apply", e);
        }
    }

    public Stream<List<String>> apply0(Node repInfoNode) throws URISyntaxException {

        List<List<String>> rows = new ArrayList<>();
        {
            List<String> row = new ArrayList<>();
            // initial few cells, more will come.
            row.add(new URI(MainHelpers.eval("@uri").apply(repInfoNode)).getPath());
            row.add(MainHelpers.eval("./j:size/text()").apply(repInfoNode));
            row.add(MainHelpers.eval("./j:format/text()").apply(repInfoNode));
            row.add(MainHelpers.eval("./j:version/text()").apply(repInfoNode));
            row.add(MainHelpers.eval("./j:mimeType/text()").apply(repInfoNode));
            row.add(MainHelpers.eval("./j:status/text()").apply(repInfoNode));
            row.add(MainHelpers.eval("./j:messages/j:message/text()").apply(repInfoNode));
            rows.add(row);
        }
        return rows.stream();
    }
}
