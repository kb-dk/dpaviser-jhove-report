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
 */
public class NodeListFunction implements Function<Node, Stream<List<String>>> {
    @Override
    public Stream<List<String>> apply(Node repInfoNode) {
        try {
            return apply0(repInfoNode);
        } catch (URISyntaxException e) {
            throw new RuntimeException("apply0", e);
        }
    }

    public Stream<List<String>> apply0(Node repInfoNode) throws URISyntaxException {
        URI uri = new URI(Main.e("@uri").apply(repInfoNode));


        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();

        row.add(uri.getPath());
        row.add(Main.e("./j:size/text()").apply(repInfoNode));
        row.add(Main.e("./j:format/text()").apply(repInfoNode));
        row.add(Main.e("./j:version/text()").apply(repInfoNode));
        row.add(Main.e("./j:mimeType/text()").apply(repInfoNode));
        row.add(Main.e("./j:status/text()").apply(repInfoNode));
        row.add(Main.e("./j:messages/j:message/text()").apply(repInfoNode));

        rows.add(row);
        return rows.stream();
        //return asList(asList(uri.getPath())).stream();

//        Main.e("j:status/text()"),
//                Main.e("j:size/text()"),
//                Main.e("name(.)"),
//                Main.e(".//j:property[j:name/text() = 'Producer']/j:values/j:value/text()"),
//                Main.e(".//j:size/text()")).stream()
//                .map(expression -> expression.apply(repInfoNode))
//                .map(s -> s.length() < 100 ? s : new String(s.substring(0, 100) + "..."))
//                .collect(toList());
    }
}
