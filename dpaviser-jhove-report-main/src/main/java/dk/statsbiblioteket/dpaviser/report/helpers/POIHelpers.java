package dk.statsbiblioteket.dpaviser.report.helpers;


import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

/**
 * Helper routines for <a href="https://poi.apache.org/">Apache POI</a>
 */
public class POIHelpers {
    /**
     * Create a HSSFWorkbook corresponding to a cell "array" of values (numeric values are recognized). <a
     * href="https://poi.apache.org/apidocs/org/apache/poi/hssf/usermodel/HSSFWorkbook.html">https://poi.apache.org/apidocs/org/apache/poi/hssf/usermodel/HSSFWorkbook.html</a>
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
