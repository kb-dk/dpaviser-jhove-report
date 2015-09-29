package dk.statsbiblioteket.dpaviser.report.jhove;

import edu.harvard.hul.ois.jhove.App;
import edu.harvard.hul.ois.jhove.JhoveBase;
import edu.harvard.hul.ois.jhove.JhoveException;
import edu.harvard.hul.ois.jhove.Module;
import edu.harvard.hul.ois.jhove.OutputHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * helper routines for invoking JHove in order to get in-depth analysis of the files in question
 */

public class JHoveHelpers {
    /**
     * Create a function which accepts a Path, run jhove (internally through the main method, not by executing an
     * external process) on the Path, and return the output generated.
     */
    public static Function<Path, InputStream> getJHoveFunction(InputStream config, File tmpDir) {

        File configFile = readConfigFile(config, checkNotNull(tmpDir));

        //JHove Initilisation
        final App jhoveApp = new App("name", "release", new int[]{2015, 9, 15}, "usage", "rights");
        final JhoveBase je = getJhoveBase(tmpDir, configFile);
        final OutputHandler jhoveOutputHandler = Objects.requireNonNull(je.getHandler("xml"), "getHandler");
        final Module jhoveModule = null;
        final OutputHandler jhoveAboutHandler = null;


        Function<Path, InputStream> jhoveFunction = dataFilePath -> {
            final String jhoveResultFile;
            try {
                jhoveResultFile = File.createTempFile("jhove", ".xml", tmpDir).getAbsolutePath();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create temporary jhove result file in " + tmpDir, e);
            }
            try {
                je.dispatch(jhoveApp, jhoveModule, jhoveAboutHandler, jhoveOutputHandler, jhoveResultFile, new String[]{dataFilePath.toString()});
                return new SelfDeletingFileInputStream(jhoveResultFile);
            } catch (Exception e) {
                throw new RuntimeException(dataFilePath + " -> for " + jhoveResultFile, e);
            }
        };
        return jhoveFunction;
    }

    private static File readConfigFile(InputStream config, File tmpDir1) {
        // First copy the configuration stream to a physical file so JHove can read it.
        File configFile = null;
        try {
            configFile = File.createTempFile("jhove", ".conf", tmpDir1);
            Files.copy(checkNotNull(config), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("could not put configuration in file " + configFile, e);
        }
        return configFile;
    }

    private static JhoveBase getJhoveBase(File tmpDir, File configFile) {
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
        je.setEncoding("utf-8");
        je.setTempDirectory(tmpDir.getAbsolutePath());
        je.setBufferSize(4096);
        je.setChecksumFlag(false);
        je.setShowRawFlag(false);
        return je;
    }

    /**
     * This file input stream will delete its file on closed.
     */
    private static class SelfDeletingFileInputStream extends FileInputStream {
        private final String outputPath;

        public SelfDeletingFileInputStream(String outputPath) throws FileNotFoundException {
            super(outputPath);
            this.outputPath = outputPath;
        }

        /**
         * call super.close and delete the file. If super.close throws IOException, the file is not deleted.
         *
         * @throws IOException
         */
        @Override
        public void close() throws IOException {
            super.close();
            new File(outputPath).delete();
        }
    }
}
