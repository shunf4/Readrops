package com.readrops.api.utils;

import com.readrops.db.logwrapper.Log;

import androidx.annotation.Nullable;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import java.util.Locale;

public final class DateUtils {

    private static final String TAG = DateUtils.class.getSimpleName();

    /**
     * Base of common RSS 2 date formats.
     * Examples :
     * Fri, 04 Jan 2019 22:21:46 GMT
     * Fri, 04 Jan 2019 22:21:46 +0000
     */
    private static final String RSS_2_BASE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss";

    private static final String GMT_PATTERN = "ZZZ";

    private static final String OFFSET_PATTERN = "Z";

    private static final String ISO_PATTERN = ".SSSZZ";

    private static final String EDT_PATTERN = "zzz";

    /**
     * Date pattern for format : 2019-01-04T22:21:46+00:00
     */
    private static final String ATOM_JSON_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    @Nullable
    public static LocalDateTime parse(String value) {
        if (value == null) {
            return null;
        }

        try {
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendOptional(DateTimeFormat.forPattern(RSS_2_BASE_PATTERN + " ").getParser()) // with timezone
                    .appendOptional(DateTimeFormat.forPattern(RSS_2_BASE_PATTERN).getParser()) // no timezone, important order here
                    .appendOptional(DateTimeFormat.forPattern(ATOM_JSON_DATE_FORMAT).getParser())
                    .appendOptional(DateTimeFormat.forPattern(GMT_PATTERN).getParser())
                    .appendOptional(DateTimeFormat.forPattern(OFFSET_PATTERN).getParser())
                    .appendOptional(DateTimeFormat.forPattern(ISO_PATTERN).getParser())
                    .appendOptional(DateTimeFormat.forPattern(EDT_PATTERN).getParser())
                    .toFormatter()
                    .withLocale(Locale.ENGLISH)
                    .withOffsetParsed();

            return formatter.parseLocalDateTime(value);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
            return null;
        }
    }

    public static String formattedDateByLocal(LocalDateTime dateTime) {
        return DateTimeFormat.mediumDate()
                .withLocale(Locale.getDefault())
                .print(dateTime);
    }

    public static String formattedDateTimeByLocal(LocalDateTime dateTime) {
        return DateTimeFormat.forPattern("dd MMM yyyy · HH:mm")
                .withLocale(Locale.getDefault())
                .print(dateTime);
    }
}
