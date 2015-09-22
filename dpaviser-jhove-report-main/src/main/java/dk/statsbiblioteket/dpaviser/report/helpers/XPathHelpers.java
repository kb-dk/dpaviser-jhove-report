package dk.statsbiblioteket.dpaviser.report.helpers;


import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import dk.statsbiblioteket.util.xml.DefaultNamespaceContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.concurrent.NotThreadSafe;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static javax.xml.xpath.XPathConstants.NODESET;

@NotThreadSafe // XPathExpressions are not threadsafe.
public class XPathHelpers {
    /**
     * Cache containing previously seen XPath expressions and their compiled version. Introducing this caused runtime to
     * go down from 146 to 121 seconds for the initial Infomedia batch.  Currently does not expire entries.  A BiMap to
     * allow to look up the original expression.
     */
    static BiMap<String, XPathExpression> xPathExpressionCache = HashBiMap.create();

    /**
     * Returns a stream of org.w3c.dom.Node corresponding to the NodeList returned by applying xPathExpression to
     * document.
     */
    public static Stream<Node> getNodesFor(Document dom, String expression) {
        return getNodesFor(dom, xpathCompile(expression));
    }

    public static Stream<Node> getNodesFor(Document document, XPathExpression xPathExpression) {
        try {
            // http://stackoverflow.com/a/23361853/53897
            NodeList nodeList = (NodeList) xPathExpression.evaluate(document, NODESET);
            /* No simple way to convert a NodeList to an actual List, so create a Stream of an integer range and map
            it to the corresponding node in the NodeList. */
            int nodeListLength = nodeList.getLength();
            return IntStream
                    .range(0, nodeListLength)
                    .mapToObj(nodeList::item);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("evaluate " + xPathExpressionCache.inverse().get(xPathExpression), e);
        }
    }

    /**
     * prepare an XPathExpression with the given expression in the JHove namespace (mapped to <code>j</code>). This
     * allows the compilation step to be done outside the main stream (i.evalXPathAndApply. only once), and the result
     * is cached to avoid recompiling later, as well as provide a toString() replacement linking back to the original
     * expression which is lost during compilation.  NOT THREAD SAFE!!
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
     * Returns a function which evaluate XPath expression on node giving string, (including zero or more transformations
     * like URLDecoder::decode or String::toLowerCase).
     *
     * @param expression           XPath expression to apply to node.  Note that this can be any node in the entire DOM,
     *                             and that relative paths should be used.
     * @param afterTransformations transformations to apply in sequence to the original value extracted by XPath.
     * @return final string value
     */
    public static Function<Node, String> evalXPathAndApply(String expression, Function<String, String>... afterTransformations) {

        XPathExpression matcher = xpathCompile(expression);

        return expressionToEvaluate -> {
            try {
                String stringToReturn = matcher.evaluate(expressionToEvaluate);
                for (Function<String, String> function : afterTransformations) {
                    stringToReturn = function.apply(stringToReturn);
                }
                return stringToReturn;
            } catch (XPathExpressionException e) {
                throw new RuntimeException("bad expression: " + expression, e);
            }
        };
    }

}
