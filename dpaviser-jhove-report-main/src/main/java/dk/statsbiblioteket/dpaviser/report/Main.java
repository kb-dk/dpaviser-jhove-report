package dk.statsbiblioteket.dpaviser.report;

import dk.statsbiblioteket.dpaviser.report.jhove.JHoveProcessRunner;
import dk.statsbiblioteket.util.xml.DOM;
import dk.statsbiblioteket.util.xml.DefaultNamespaceContext;
import edu.harvard.hul.ois.jhove.App;
import edu.harvard.hul.ois.jhove.JhoveBase;
import edu.harvard.hul.ois.jhove.JhoveException;
import edu.harvard.hul.ois.jhove.Module;
import edu.harvard.hul.ois.jhove.OutputHandler;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

        //        Function<Path, InputStream> jhove = getjHoveProcessRunner();
        Function<Path, InputStream> jhove = getInternalJHoveInvoker();

        Predicate<Path> isSingleEditionDir = (path) -> singleEditionDirectoryPattern.matcher(path.toString()).matches();

        XPathExpression repInfo = xpathCompile("/j:jhove/j:repInfo");


        NodeListFunction nodeListFunction = new NodeListFunction();

        List<List<String>> cells =
                Files.walk(Paths.get(args[1]))
                        .filter(Files::isDirectory)
                        .filter(isSingleEditionDir)
                        //.limit(20)
                        .peek(System.out::println)
                        .map(jhove::apply)
                        .map(DOM::streamToDOM)
                        .flatMap(dom -> nodelistForXPathExpression(dom, repInfo))
                                // We are now looking at <j:repInfo> nodes in the full jhove DOM tree
                                //.parallel()
                        .flatMap(nodeListFunction)
                        .collect(toList());

        Workbook workbook = workbookFor(cells);
        writeToFile(workbook, args[0]);

        System.out.println(System.currentTimeMillis() - start + " ms.");

    }

    protected static Function<Path, InputStream> getInternalJHoveInvoker() {
        try {
            return getInternalJHoveInvoker0();
        } catch (JhoveException e) {
            throw new RuntimeException(e);
        }
    }

    protected static Function<Path, InputStream> getInternalJHoveInvoker0() throws JhoveException {
        // http://www.garymcgath.com/jhovenote.html
        App app = new App("name", "release", new int[]{2015, 9, 15}, "usage", "rights");
        JhoveBase je = new JhoveBase();
        final Module module = null;
        je.setLogLevel("SEVERE");
        je.init("/home/tra/git/dpaviser-jhove-report/jhove/jhove-apps/src/main/config/jhove.conf", null); //FIXME
        OutputHandler handler = Objects.requireNonNull(je.getHandler("xml"), "getHandler");
        je.setEncoding("utf-8");
        je.setTempDirectory("/tmp/jhove");  //FIXME
        je.setBufferSize(4096);
        je.setChecksumFlag(false);
        je.setShowRawFlag(false);

        try {
            // JHove either uses a file given by name or System.out iff null.
            String outputFile = File.createTempFile("jhove", ".xml").getAbsolutePath(); //FIXME
            return (path) -> {
                try {
                    je.dispatch(app, module, null, handler, outputFile, new String[]{path.toString()});
                    return new FileInputStream(outputFile) {
                        @Override
                        public void close() throws IOException {
                            super.close();
                            //new File(outputFile).delete();  /fails
                        }
                    };
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (IOException e) {
            throw new RuntimeException("could not create temp file", e);
        }
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
    public static Function<Node, String> e(String expression, Function<String, String>... afterTransformations) {

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
     * Create a HSSFWorkbook corresponding to a cell "array" of values (numeric values are recognized).
     */

    public static Workbook workbookFor(List<List<String>> cells) {
        HSSFWorkbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        for (List<String> rowList : cells) {
            Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
            int cellNumber = 0;
            for (String cellValue : rowList) {
                Cell cell = row.createCell(cellNumber++);
                try {
                    cell.setCellValue(Double.parseDouble(cellValue));
                } catch (NumberFormatException nfe) {
                    cell.setCellValue(cellValue);
                }
            }
        }
        return workbook;
    }

    /**
     * Write a Apache POI workbook to the given file
     */
    public static void writeToFile(Workbook workbook, String fileName) throws IOException {
        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
        }
    }

}
