package dk.statsbiblioteket.dpaviser.report.jhove;

import edu.harvard.hul.ois.jhove.App;
import edu.harvard.hul.ois.jhove.JhoveBase;
import edu.harvard.hul.ois.jhove.JhoveException;
import edu.harvard.hul.ois.jhove.Module;
import edu.harvard.hul.ois.jhove.OutputHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;


/** helper routines for invoking JHove in order to get in-depth analysis of the files in question */

public class JHoveHelpers {
    /**
     * Create a function which accepts a Path, run jhove (internally through the main method, not by executing an
     * external process) on the Path, and return the output generated.
     */
    public static Function<Path, InputStream> getInternalJHoveInvoker(InputStream config, File tmpDir) {

        File tmpDir1 = checkNotNull(tmpDir);
        // http://www.garymcgath.com/jhovenote.html

        // First copy the configuration stream to a physical file so JHove can read it.
        File configFile = null;
        try {
            configFile = File.createTempFile("jhove", ".conf", tmpDir1);
            Files.copy(checkNotNull(config), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("could not put configuration in file " + configFile.getAbsolutePath(), e);
        }

        App app = new App("name", "release", new int[]{2015, 9, 15}, "usage", "rights");
        final Module module = null;
        JhoveBase je;
        try {
            je = new JhoveBase();
        } catch (JhoveException e) {
            throw new RuntimeException("new JhoveBase()", e);
        }
        je.setLogLevel("SEVERE");
        try {
            je.init(configFile.getAbsolutePath(), null);
        } catch (JhoveException e) {
            throw new RuntimeException("JHove initialization with " + configFile.getAbsolutePath(), e);
        }
        OutputHandler handler = Objects.requireNonNull(je.getHandler("xml"), "getHandler");
        je.setEncoding("utf-8");
        je.setTempDirectory(tmpDir1.getAbsolutePath());
        je.setBufferSize(4096);
        je.setChecksumFlag(false);
        je.setShowRawFlag(false);

        // JHove either uses a file given by name or System.out iff null.  Use a file, return it as an input stream
        // and delete it when closed after reading.

        return path -> {
            String outputPath = null;
            try {
                outputPath = File.createTempFile("jhove", ".xml", tmpDir1).getAbsolutePath();
                //System.out.println(outputPath);
                je.dispatch(app, module, null, handler, outputPath, new String[]{path.toString()});

                final String finalOutputPath = outputPath;
                return new FileInputStream(finalOutputPath) {
                    @Override
                    public void close() throws IOException {
                        super.close();
//                        if (path.toString().endsWith(".pdf")) {
//                            System.out.println(path);
//                        }
                        new File(finalOutputPath).delete();
                    }
                };
            } catch (Exception e) {
                throw new RuntimeException("-> for " + outputPath, e);
            }
        };
    }
}
