package dk.statsbiblioteket.dpaviser.report;

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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.xml.xpath.XPathConstants.NODESET;


public class MainHelpers {
    /**
     * Create a function which accepts a Path, run jhove (internally through the main method, not by
     * executing an external process) on the Path, and return the output generated.
     */
    public static Function<Path, InputStream> getInternalJHoveInvoker(InputStream config, File tmpDir) {
        try {
            return getInternalJHoveInvoker0(checkNotNull(config), checkNotNull(tmpDir));
        } catch (JhoveException e) {
            throw new RuntimeException(e);
        }
    }

    protected static Function<Path, InputStream> getInternalJHoveInvoker0(InputStream config, File tmpDir) throws JhoveException {
        // http://www.garymcgath.com/jhovenote.html

        // First copy the configuration stream to a physical file.
        File configFile = null;
        try {
            configFile = File.createTempFile("jhove", ".conf", tmpDir);
            Files.copy(config, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("could not put configuration in file " + configFile ,e);
        }

        App app = new App("name", "release", new int[]{2015, 9, 15}, "usage", "rights");
        JhoveBase je = new JhoveBase();
        final Module module = null;
        je.setLogLevel("SEVERE");
        je.init(configFile.getAbsolutePath(), null);
        OutputHandler handler = Objects.requireNonNull(je.getHandler("xml"), "getHandler");
        je.setEncoding("utf-8");
        je.setTempDirectory(tmpDir.getAbsolutePath());
        je.setBufferSize(4096);
        je.setChecksumFlag(false);
        je.setShowRawFlag(false);

        // JHove either uses a file given by name or System.out iff null.  Use a file, return it as an input stream
        // and delete it when closed after reading.

        return (path) -> {
            try {
                String outputFile = File.createTempFile("jhove", ".xml", tmpDir).getAbsolutePath();
                je.dispatch(app, module, null, handler, outputFile, new String[]{path.toString()});
                return new FileInputStream(outputFile) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        new File(outputFile).delete();
                    }
                };
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static Stream<Node> getNodesFor(Document dom, XPathExpression repInfo) {
        try {
            // http://stackoverflow.com/a/23361853/53897
            NodeList nodeList = (NodeList) repInfo.evaluate(dom, NODESET);
            /* No simple way to convert a NodeList to an actual List, so create a Stream of an integer range and map
            it to the corresponding node in the NodeList. */
            return IntStream
                    .range(0, nodeList.getLength())
                    .mapToObj(nodeList::item);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("evaluate", e);
        }
    }

    /**
     * Get a function which applies an externally called JHove on the givne path and returns the output as an
     * inputstream. The JHove binary is placed in the maven tree and prepared with appassembler plugin
     */

//    public static Function<Path, InputStream> getjHoveProcessRunner() throws URISyntaxException, IOException {
//
//        // locate jhove binary in the maven module "next to" this.
//        URL location = Main.class.getProtectionDomain().getCodeSource().getLocation();
//        URI targetClassesURI = location.toURI();
//
//        File targetClassesDir = new File(targetClassesURI);
//        File thisMavenModuleDir = new File(targetClassesDir, "../..");
//        File jhoveBinDir = new File(thisMavenModuleDir, "../jhove/jhove-apps/target/appassembler/bin/");
//        String canonicalPath = jhoveBinDir.getCanonicalPath();
//
//        if (jhoveBinDir.exists() == false) {
//            throw new FileNotFoundException(canonicalPath);
//        }
//
//        String userDirPath = System.getProperty("user.dir");
//        System.out.println(userDirPath);
//
//        // Note: JHove output localized dates.
//        Map<String, String> environmentVariables = new HashMap<>();
//        //  environmentVariables.put("TZ", "UTC"); // Tell Linux to use UTC
//        // Changed launcher script instead.
//        return new JHoveProcessRunner(canonicalPath);
//    }

    /** Cache containing previously seen XPath expressions and their compiled version.
     * Introducing this caused runtime to go down from 146 to 121 seconds for the initial Infomedia
     * batch.  Currently does not expire entries.
     */
    static Map<String, XPathExpression> xPathExpressionCache = new HashMap<>();

    /**
     * prepare an XPathExpression with the given expression in the JHove namespace (mapped to <code>j</code>). This
     * allows the compilation step to be done outside the main stream (i.eval. only once).
     */

    public static XPathExpression xpathCompile(String expression) {
        if (xPathExpressionCache.containsKey(expression)) {
            return xPathExpressionCache.get(expression);
        }

        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new DefaultNamespaceContext("", "j", "http://hul.harvard.edu/ois/xml/ns/jhove"));
            XPathExpression xpathExpression = xpath.compile(expression);
            xPathExpressionCache.put(expression, xpathExpression);
            return xpathExpression;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(expression, e);
        }
    }

    /**
     * Evaluate XPath expression on node giving string, (including zero or more transformations (like
     * URLDecoder::decode)).
     *
     * @param expression           XPath expression to extractLinesFromJHoveOnSingleEditionDir to node.  Note that this
     *                             can be any node in the DOM, and that relative paths should be used.
     * @param afterTransformations transformations to extractLinesFromJHoveOnSingleEditionDir in sequence to the
     *                             original value extracted by XPath.
     * @return
     */
    public static Function<Node, String> eval(String expression, Function<String, String>... afterTransformations) {

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
}
