package dk.statsbiblioteket.dpaviser.report;

import dk.statsbiblioteket.dpaviser.report.jhove.JHoveProcessRunner;
import dk.statsbiblioteket.util.xml.DOM;
import dk.statsbiblioteket.util.xml.DefaultNamespaceContext;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
            System.err.println("usage: spreadsheet.xls infomedia-dump-dir");
        }
        long start = System.currentTimeMillis();

        final Main main = new Main();
        Stream<List<String>> cellLines = main.extractLinesFromJHoveOnSingleEditionDir(Paths.get(args[1])).peek(System.out::println);
        main.collectCellLinesInSpreadSheet(args[0], cellLines);
        //System.out.println(cellLines).toString().length());

        System.out.println(System.currentTimeMillis() - start);

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

    protected void collectCellLinesInSpreadSheet(String fileName, Stream<List<String>> cellLines) throws Exception {
        Workbook wb = new HSSFWorkbook(); // old Excel for now.
        Sheet sheet = wb.createSheet("sample title");
        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setLandscape(true);

        // For now collect first and then create rows.  Look into writing a collector.

        List<List<String>> l = cellLines.collect(toList());

        int rownumber = sheet.getPhysicalNumberOfRows(); // start with 0
        for (List<String> ls : l) {
            Row row = sheet.createRow(rownumber++);
            int cellNumber = 0;
            for (String cellValue : ls) {
                Cell cell = row.createCell(cellNumber++);
                try {
                    long longValue = Long.valueOf(cellValue);
                    cell.setCellValue(longValue);
                } catch (NumberFormatException nbe) {
                    // Not a number, save as string
                    cell.setCellValue(cellValue);
                }
            }
        }

        FileOutputStream out = new FileOutputStream(fileName);
        wb.write(out);
        out.close();

    }

    public Stream<List<String>> extractLinesFromJHoveOnSingleEditionDir(Path infomediaDumpDirPath) throws URISyntaxException, IOException {
        // .../infomed/NOR/2015/06/03
        Pattern singleEditionDirectoryPattern = Pattern.compile("^.*/[A-Z0-9]+/[0-9]{4}/(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])$");

        JHoveProcessRunner jhove = getjHoveProcessRunner();

        Predicate<Path> isSingleEditionDir = (path) -> singleEditionDirectoryPattern.matcher(path.toString()).matches();

        Stream<Path> pathStream = Files.walk(infomediaDumpDirPath)
                .filter(Files::isDirectory)
                .filter(isSingleEditionDir)
                .limit(1);

        Function<Path, Document> pathDocumentFunction = path -> DOM.streamToDOM(jhove.apply(path), true);

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

        List<Function<Node, String>> fieldExpressions = asList(
                e("@uri", URLDecoder::decode), e("j:status/text()"), e("j:size/text()"),
                e("name(.)"), e(".//j:property[j:name/text() = 'Producer']/j:values/j:value/text()"),
                e(".//j:size/text()")
        );

        Stream<List<String>> fieldLinesList = repInfoNodeStream
                //.limit(10)
                //.parallel()
                .map(repInfoNode -> getFieldsForLine(repInfoNode, fieldExpressions));

        return fieldLinesList;
    }

    /**
     * Evaluate XPath expression on node giving string, (including optional transformations (like URLDecoder::decode)).
     *
     * @param expression           XPath expression to extractLinesFromJHoveOnSingleEditionDir to node.  Note that this
     *                             can be any node in the DOM, and that relative paths should be used.
     * @param afterTransformations transformations to extractLinesFromJHoveOnSingleEditionDir in sequence to the
     *                             original value extracted by XPath.
     * @return
     */
    protected Function<Node, String> e(String expression, Function<String, String>... afterTransformations) {
        XPath xpath = xpathFactory.newXPath();
        xpath.setNamespaceContext(context);
        XPathExpression matcher;
        try {
            matcher = xpath.compile(expression);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("bad expression: " + expression, e);
        }

        return e -> {
            try {
                String s = matcher.evaluate(e);
                for (Function<String, String> f : afterTransformations) {
                    s = f.apply(s);
                }
                return s;
            } catch (XPathExpressionException e1) {
                throw new RuntimeException("bad expression: " + expression, e1);
            }
        };
    }

    /**
     * Given a Node create a List of strings by applying each fieldExpression to the node.  If the field value becomes
     * longer than 100 characters it is truncated.
     *
     * @param repInfoNode
     * @param fieldExpressions
     * @return
     */
    public List<String> getFieldsForLine(Node repInfoNode, List<Function<Node, String>> fieldExpressions) {
        return fieldExpressions.stream()
                .map(expression -> expression.apply(repInfoNode))
                .map(s -> s.length() < 100 ? s : new String(s.substring(0, 100) + "..."))
                .collect(toList());
    }

    private String domToString(Node document) {
        try {
            return DOM.domToString(document);
        } catch (TransformerException e) {
            throw new RuntimeException("domToString");
        }
    }

}

