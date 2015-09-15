package dk.statsbiblioteket.dpaviser.report.jhove;

import dk.statsbiblioteket.util.console.ProcessRunner;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;


public class JHoveProcessRunner implements Function<Path, InputStream> {
    private final List<String> command;
    private Map<String, String> environmentVariables;


    public JHoveProcessRunner(String dir) {
        List<String> l = new ArrayList<String>();
        l.addAll(System.getProperty("os.name").startsWith("Windows")
                ? asList("cmd", "/c", dir + System.getProperty("file.separator") + "jhove.bat", "-c", dir + "/../conf/jhove.conf")
                : asList(dir + System.getProperty("file.separator") + "jhove", "-c", dir + "/../conf/jhove.conf"));

        l.addAll(asList("-h", "xml", "-l", "OFF"));
        this.command = l;
    }


    @Override
    public InputStream apply(Path infomediaSingleEditionDirPath) {
        List<String> actualCommand = new ArrayList<>(command);
        actualCommand.add(infomediaSingleEditionDirPath.toString());

        System.out.println(actualCommand);
        ProcessRunner processRunner = new ProcessRunner(actualCommand);
        processRunner.setEnviroment(environmentVariables);
        processRunner.setErrorCollectionByteSize(-1);
        processRunner.setOutputCollectionByteSize(-1);
        processRunner.run();
        String processError = processRunner.getProcessErrorAsString();
        if (processError.isEmpty() == false) {
            System.err.println(processError);
        }
        return processRunner.getProcessOutput();
    }
}
