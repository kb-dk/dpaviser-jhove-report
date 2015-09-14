package dk.statsbiblioteket.dpaviser.report;

import com.google.common.base.Function;
import dk.statsbiblioteket.dpaviser.report.jhove.JHoveProcessRunner;
import dk.statsbiblioteket.util.xml.DOM;
import dk.statsbiblioteket.util.xml.DefaultNamespaceContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static javax.xml.xpath.XPathConstants.NODESET;

public class Main {

    protected final XPathFactory xpathFactory = XPathFactory.newInstance();
    protected final NamespaceContext context = new DefaultNamespaceContext("", "j", "http://hul.harvard.edu/ois/xml/ns/jhove");

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: infomedia-dump-dir");
        }

        Path infomediaDumpDirPath = Paths.get(args[0]);
        System.out.println(new Main().apply(infomediaDumpDirPath).toString().length());


    }

    public static JHoveProcessRunner getjHoveProcessRunner() throws URISyntaxException, IOException {
        // locate jhove binary in the maven module "next to" this.
        URL location = Main.class.getProtectionDomain().getCodeSource().getLocation();
        URI targetClassesURI = location.toURI();

        File targetClassesDir = new File(targetClassesURI);
        File thisMavenModuleDir = new File(targetClassesDir, "../..");
        File jhoveBinDir = new File(thisMavenModuleDir, "../jhove/jhove-apps/target/appassembler/bin/");
        String canonicalPath = jhoveBinDir.getCanonicalPath();

        if (jhoveBinDir.exists() == false) {
            throw new FileNotFoundException(canonicalPath);
        }

        System.out.println(canonicalPath); // FIXME: Remove when ready.
        String userDirPath = System.getProperty("user.dir");
        System.out.println(userDirPath);

        // Note: JHove output localized dates.
        Map<String, String> environmentVariables = new HashMap<>();
//        environmentVariables.put("TZ", "UTC"); // Tell Linux to use UTC
// Changed launcher script instead.
        return new JHoveProcessRunner(canonicalPath);
    }

    public Object apply(Path infomediaDumpDirPath) throws URISyntaxException, IOException {
        // .../infomed/NOR/2015/06/03
        Pattern singleEditionDirectoryPattern = Pattern.compile("^.*/[A-Z0-9]+/[0-9]{4}/(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])$");

        JHoveProcessRunner jhove = getjHoveProcessRunner();

//        Stream<Document> doms =
//                Files.walk(infomediaDumpDirPath)
//                        .filter(Files::isDirectory)
//                        .filter((path) -> singleEditionDirectoryPattern.matcher(path.toString()).matches())
//                        .peek(System.out::println)
//                        .limit(3)
//                        .map(jhove::apply)
//                        .map(DOM::streamToDOM)
//                        .peek(dom -> {
//                            try {
//                                System.out.println(DOM.domToString(dom));
//                            } catch (TransformerException e) {
//                                throw new RuntimeException("domToString", e);
//                            }
//                        });

//        Object = asList(
//                () -> null
//        );
        Predicate<Path> isSingleEditionDir = (path) -> singleEditionDirectoryPattern.matcher(path.toString()).matches();

        Stream<Path> pathStream = Files.walk(infomediaDumpDirPath)
                .filter(Files::isDirectory)
                .filter(isSingleEditionDir)
                .limit(1);

        Function<Path, Document> pathDocumentFunction = path -> DOM.streamToDOM(jhove.apply(path), true);

//        Stream<Document> documentStream = pathStream
//                .map(path -> pathDocumentFunction.apply(path));
//
//        Stream<List<String>> stringListStream = documentStream
//                .map(this::applyXPathsToDocument);
//        // XPath set up stolen from IteratorForFedora3.java

//        XPath xp = xpathFactory.newXPath();
//        xp.setNamespaceContext(context);

//        ResultCollector result = getResultCollectorFor("name",
//                d -> xpathCheck(xp, "/j:jhove/j:repInfo/j:version/text()", d, "1.4"),
//                d -> xpathCheck(xp, "/j:jhove/j:repInfo/j:status/text()", d, "Well-Formed and valid"),
//                d -> xpathCheck(xp, "count(//j:property/j:name[text() = 'Font'])", d, "3")
//        );
//        Assert.assertTrue(result.isSuccess(), result.toReport());  // If unsuccessful, use the report as message.
//


//        XPath xpath = ;
//
//        Object xpaths = asList((doc) -> n
//        BiFunction<Path, Document, List<String>> extractInfo =
//                (path, document) -> xpaths
//
        XPathExpression repInfo;
        try {
            XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(context);
            //           repInfo = xpath.compile("/jhove/repInfo");
            repInfo = xpath.compile("/j:jhove/j:repInfo");
        } catch (XPathExpressionException e) {
            throw new RuntimeException("repInfo");
        }

        Stream<Document> domsStream =
                pathStream
                        .map(jhove::apply)
                        .map(DOM::streamToDOM);

        Stream<Node> repInfoNodeStream = domsStream
                .peek(System.out::println)
                .flatMap(dom -> {
                    try {
                        // http://stackoverflow.com/a/23361853/53897
                        NodeList nodeList = (NodeList) repInfo.evaluate(dom, NODESET);
                        System.out.println(nodeList.getLength());
                        return IntStream.range(0, nodeList.getLength())
                                .mapToObj(nodeList::item);
                    } catch (XPathExpressionException e) {
                        throw new RuntimeException("repINfo evaluate");
                    }
                });

        List<String> fieldExpressions = asList(
                "@uri", "j:status/text()", "j:size/text()",
                "name(.)", ".//j:property[j:name/text() = 'Producer']/j:values/j:value/text()", ".//j:size/text()"
        );

        Stream<List<String>> fieldLinesList = repInfoNodeStream
                //.limit(10)
                .map(repInfoNode -> getFieldsForLine(repInfoNode, fieldExpressions));

        List<Object> l = fieldLinesList
                .peek(System.out::println)
                .collect(toList());


        return l;
    }

    public List<String> getFieldsForLine(Node repInfoNode, List<String> fieldExpressions) {
        List<String> l = new ArrayList<>();
        for (String expression : fieldExpressions) {
            XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(context);
            String result = null;
            try {
                result = xpath.compile(expression).evaluate(repInfoNode);
            } catch (XPathExpressionException e) {
                throw new RuntimeException(expression, e);
            }
            if (result.length() > 100) {
                result = new String(result.substring(0, 100)+"...");
            }
            l.add(result);
        }
        return l;
    }

    private String domToString(Node document) {
        try {
            return DOM.domToString(document);
        } catch (TransformerException e) {
            throw new RuntimeException("domToString");
        }
    }


    protected List<String> applyXPathsToDocument(Document document) {

        return null;
    }

}
