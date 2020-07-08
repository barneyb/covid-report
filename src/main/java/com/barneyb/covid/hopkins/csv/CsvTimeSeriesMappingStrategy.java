package com.barneyb.covid.hopkins.csv;

import com.opencsv.CSVReader;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.*;
import lombok.val;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class CsvTimeSeriesMappingStrategy<T extends CsvTimeSeries> extends HeaderColumnNameMappingStrategy<T> {

    private int columnCount;
    private LocalDate[] dateSequence;
    private int firstDataColumn;

    public CsvTimeSeriesMappingStrategy(Class<T> clazz) {
        this.setType(clazz);
    }

    @Override
    public void captureHeader(CSVReader reader) throws IOException, CsvRequiredFieldEmptyException {
        super.captureHeader(reader);
        columnCount = headerIndex.findMaxIndex();
        for (int i = 0; i < columnCount; i++) {
            // this feels gross.
            try {
                headerToDate(i);
                firstDataColumn = i;
                break;
            } catch (DateTimeParseException ignored) {
                // next!
            }
        }
        dateSequence = new LocalDate[columnCount - firstDataColumn + 1];
        for (int i = firstDataColumn; i <= columnCount; i++) {
            dateSequence[i - firstDataColumn] = headerToDate(i);
        }
    }

    private LocalDate headerToDate(int i) {
        return CsvTimeSeries.DATE_FORMAT.parse(headerIndex.getByPosition(i), LocalDate::from);
    }

    @Override
    public T populateNewBean(String[] line) throws CsvBeanIntrospectionException, CsvRequiredFieldEmptyException, CsvDataTypeMismatchException, CsvConstraintViolationException, CsvValidationException {
        val series = super.populateNewBean(line);
        series.setDateSequence(dateSequence);
        val data = new int[columnCount - firstDataColumn + 1];
        for (int i = firstDataColumn; i <= columnCount; i++) {
            data[i - firstDataColumn] = Integer.parseInt(line[i]);
        }
        series.setData(data);
        return series;
    }

}
