package dk.statsbiblioteket.dpaviser.report;

import dk.statsbiblioteket.dpaviser.report.helpers.CellRowComparator;
import dk.statsbiblioteket.dpaviser.report.helpers.MailHelper;
import dk.statsbiblioteket.dpaviser.report.helpers.POIHelpers;
import dk.statsbiblioteket.dpaviser.report.helpers.PathHelpers;

import javax.mail.internet.InternetAddress;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public class Main {

    public static final List<String> PDF_HEADERS = asList(
            "Name", "Type",
            "Size", "Format", "Version", "Status", "Profiles",
            "Title", "Producer", "CreationDate", "ModDate",
            "PDF:JPEG", "PDF:UNCOMPRESSED", "PDF:FLATEDECODE",
            "PDF:OTHERIMAGES", "PDF:TYPE0", "PDF:TYPE1", "PDF:TRUETYPE", "PDF:OTHERFONTS", "PDF:UNEMBEDDEDFONTS",
            "PDF:Pages");

    public static final List<String> XML_HEADERS = asList(
            "Name", "Type", "Valid",
            "KKOD", "PUBX", "OFIL", "PUGE", "STATUS", "PMOD", "FOED", "PMOD", "PIND", "PSID", "PSEK", "PSIN", "PSNA",
            "CCRE", "UDGA", "PDF", "CMOD", "READERSHIP", "CIRCULATION", "MEDIATYPE", "COUNTRY", "REGION", "LIX", "LANGUAGE"
    );

    /* Converts a list of cell rows to a workbook and return the bytes which <code>write(...)</code> wrote.

     */

    public static byte[] workbookBytesForCellRows(List<List<String>> numberOfPagesInSectionCellRows) throws IOException {
        byte[] report1bytes;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        POIHelpers.workbookFor(numberOfPagesInSectionCellRows).write(baos);
        report1bytes = baos.toByteArray();
        return report1bytes;
    }


    /**
     * <p> main looks at all the files in a given part of an Infomedia delivery and analyze each file, collecting the
     * results and mailing them as attachments.</p> <p>Default is to try to send the mail on port 25 on localhost with a
     * calculated sender address.  If this fails for any reason, set the appropriate JavaMail properties as system
     * properties to override the default behavior.</p>
     *
     * @param args args[0] is the relative path to the informedia-dump-dir to process (may be inside).  args[1] is the
     *             comma-separated emails to send mail to (no spaces).
     * @throws Exception fail fast in case of problems.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java " + Main.class.getName() + " infomedia-dump-dir comma-separated-emails");
        }

        //
        Path startPath = Paths.get(args[0]);
        InternetAddress[] addresses = InternetAddress.parse(args[1]);
        //

        File tmpDir = com.google.common.io.Files.createTempDir();

        Map<String, Long> pdfsForSection = new TreeMap<>(); // sort keys automatically

        Map<String, String> sectionFor = new TreeMap<>(); // sort keys automatically

        /* First create the section overview.  For simplicity walk the directory structure twice */

        Files
                .walk(startPath)
                .filter(Files::isRegularFile)
                .forEach(path -> MetadataExtractorPerDelivery.extractMetadataForPagesInSectionReport(path, pdfsForSection, sectionFor));

        List<List<String>> numberOfPagesInSectionRows =
                pdfsForSection.entrySet().stream().map(
                        e -> asList(e.getKey(), pdfsForSection.get(e.getKey()) + " sider", sectionFor.get(e.getKey()))
                ).collect(toList());


        /* Find all files in the given directory tree, and extract
         one or more row of cells for each file.  Create a spreadsheet corresponding to the rows.
         */
        Map<String, List<List<String>>> metadataCellRowsMap = Files
                .walk(startPath)
                .filter(Files::isRegularFile)
                .flatMap(path -> MetadataExtractorPerPage.getZeroOrOneRowMappedByTypeForPath(path, tmpDir).entrySet().stream())
                        // Combine Map.Entries in a single Map - http://stackoverflow.com/a/31488386/53897
                .collect(Collectors.groupingBy(entry -> entry.getKey(),
                        Collectors.mapping(entry -> entry.getValue(), Collectors.toList())));


        // ["PDF"->[["3","4"],["1","2"]], "XML"-> ..., "MD5"->...]

        for (List<List<String>> cellRows : metadataCellRowsMap.values()) {
            Collections.sort(cellRows, new CellRowComparator());
        }

        // ["PDF"->[["1","2"],["3","4"]], "XML"-> ..., "MD5"->...]

        metadataCellRowsMap.get("PDF").add(0, PDF_HEADERS); // How to mark rows as headers in workbook sheet
        metadataCellRowsMap.get("XML").add(0, XML_HEADERS); // How to mark rows as headers in workbook sheet

        // and now create a mail for the desired recipients.
        // Recipe http://www.tutorialspoint.com/java/java_sending_email.htm

        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd'T'HHmm");
        String today = sdfDate.format(new Date());


        MailHelper.sendMail(new File(args[0]).getAbsolutePath(),
                addresses,
                metadataCellRowsMap.getOrDefault("MD5", new ArrayList<>()),
                today,
                workbookBytesForCellRows(numberOfPagesInSectionRows),
                workbookBytesForCellRows(metadataCellRowsMap.get("PDF")),
                workbookBytesForCellRows(metadataCellRowsMap.get("XML"))
        );

        PathHelpers.deleteTreeBelowPath(tmpDir.toPath());
    }
}
