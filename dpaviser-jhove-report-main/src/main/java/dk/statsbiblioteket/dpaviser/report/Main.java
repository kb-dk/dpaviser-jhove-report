package dk.statsbiblioteket.dpaviser.report;

import dk.statsbiblioteket.dpaviser.report.jhove.JHoveProcessRunner;
import dk.statsbiblioteket.util.xml.DOM;
import dk.statsbiblioteket.util.xml.DefaultNamespaceContext;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: spreadsheet.xls infomedia-dump-dir");
        }
        long start = System.currentTimeMillis();

        // .../infomed/NOR/2015/06/03
        Pattern singleEditionDirectoryPattern = Pattern.compile("^.*/[A-Z0-9]+/[0-9]{4}/(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])$");

        Function<Path, InputStream> jhove = getjHoveProcessRunner();

        Predicate<Path> isSingleEditionDir = (path) -> singleEditionDirectoryPattern.matcher(path.toString()).matches();

        XPathExpression repInfo = xpathCompile("/j:jhove/j:repInfo");


        List<List<String>> cells =
                Files.walk(Paths.get(args[1]))
                        .filter(Files::isDirectory)
                        .filter(isSingleEditionDir)
                        .limit(20)
                        .map(jhove::apply)
                        .map(DOM::streamToDOM)
                        .peek(System.out::println)
                        .flatMap(dom -> nodelistForXPathExpression(dom, repInfo))
                                // We are now looking at <j:repInfo> nodes in the full jhove DOM tree
                        .parallel()
                        .map(repInfoNode -> asList(
                                e("@uri", URLDecoder::decode),
                                e("j:status/text()"),
                                e("j:size/text()"),
                                e("name(.)"),
                                e(".//j:property[j:name/text() = 'Producer']/j:values/j:value/text()"),
                                e(".//j:size/text()")).stream()
                                .map(expression -> expression.apply(repInfoNode))
                                .map(s -> s.length() < 100 ? s : new String(s.substring(0, 100) + "..."))
                                .collect(toList()))
                        .collect(toList());

        Workbook workbook = workbookFor(cells);
        writeToFile(workbook, args[0]);

        System.out.println(System.currentTimeMillis() - start);

    }

    protected static Stream<? extends Node> nodelistForXPathExpression(Document dom, XPathExpression repInfo) {
        try {
            // http://stackoverflow.com/a/23361853/53897
            NodeList nodeList = (NodeList) repInfo.evaluate(dom, NODESET);
            // No simple way to convert a NodeList to an actual List, so create a Stream<int> and map it to the
            // corresponding node in the NodeList.
            return IntStream
                    .range(0, nodeList.getLength())
                    .mapToObj(nodeList::item);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("evaluate", e);
        }
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

        String userDirPath = System.getProperty("user.dir");
        System.out.println(userDirPath);

        // Note: JHove output localized dates.
        Map<String, String> environmentVariables = new HashMap<>();
        //  environmentVariables.put("TZ", "UTC"); // Tell Linux to use UTC
        // Changed launcher script instead.
        return new JHoveProcessRunner(canonicalPath);
    }

    /**
     * prepare an XPathExpression with the given expression in the JHove namespace.
     */
    public static XPathExpression xpathCompile(String expression) {
        XPathExpression xpathExpression;
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new DefaultNamespaceContext("", "j", "http://hul.harvard.edu/ois/xml/ns/jhove"));
            xpathExpression = xpath.compile(expression);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(expression, e);
        }
        return xpathExpression;
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
    protected static Function<Node, String> e(String expression, Function<String, String>... afterTransformations) {

        XPathExpression matcher = xpathCompile(expression);

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
     * Create a HSSFWorkbook corresponding to a cell "array" of string values
     */

    public static Workbook workbookFor(List<List<String>> cells) {
        HSSFWorkbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        for (List<String> rowList : cells) {
            Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
            int cellNumber = 0;
            for (String cellValue : rowList) {
                row.createCell(cellNumber++).setCellValue(cellValue);
            }
        }
        return workbook;
    }

    /**
     * Write a workbook to the given file
     */
    public static void writeToFile(Workbook workbook, String fileName) throws IOException {
        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
    }
}

