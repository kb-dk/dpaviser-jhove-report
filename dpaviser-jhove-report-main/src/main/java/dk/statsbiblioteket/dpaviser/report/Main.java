package dk.statsbiblioteket.dpaviser.report;

import dk.statsbiblioteket.dpaviser.report.jhove.JHoveProcessRunner;
import dk.statsbiblioteket.util.xml.DOM;
import org.w3c.dom.Document;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: infomedia-dump-dir");
        }
        Path infomediaDumpDirPath = Paths.get(args[0]);

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

        Stream<Document> doms =
                Files.walk(infomediaDumpDirPath)
                        .filter(Files::isDirectory)
                        .filter((path) -> singleEditionDirectoryPattern.matcher(path.toString()).matches())
                        .limit(3)
                        .map(jhove::apply)
                        .map(DOM::streamToDOM);
        System.out.println(doms.collect(toList()));
    }

    public static JHoveProcessRunner getjHoveProcessRunner() throws URISyntaxException, IOException {
        URL location = Main.class.getProtectionDomain().getCodeSource().getLocation();
        System.out.println(location);
        URI targetClassesURI = location.toURI();
        System.out.println(targetClassesURI);

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
}
