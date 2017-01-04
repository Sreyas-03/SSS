package com.sismics.util.jpa;

/**
 * @author jtremeaux
 */
public class DialectUtil {
    /**
     * Checks if the error from the drivers relates to an object not found.
     *
     * @param message Error message
     * @return Object not found
     */
    public static boolean isObjectNotFound(String message) {
        return EMF.isDriverHsql() && message.contains("object not found") ||
                EMF.isDriverPostgresql() && message.contains("does not exist");
    }


    /**
     * Transform SQL dialect to current dialect.
     *
     * @param sql SQL to transform
     * @return Transformed SQL
     */
    public static String transform(String sql) {
        if (EMF.isDriverPostgresql()) {
            sql = transformToPostgresql(sql);
        }
        return sql;
    }

    /**
     * Transform SQL from HSQLDB dialect to current dialect.
     *
     * @param sql SQL to transform
     * @return Transformed SQL
     */
    public static String transformToPostgresql(String sql) {
        sql = sql.replaceAll("(cached|memory) table", "table");
        sql = sql.replaceAll("datetime", "timestamp");
        sql = sql.replaceAll("longvarchar", "text");
        sql = sql.replaceAll("bit not null", "bool not null");
        sql = sql.replaceAll("bit default 0", "bool default false");
        return sql;
    }

    public static String getDateDiff(String field, String diff, String unit) {
        if (EMF.isDriverHsql()) {
            return "DATE_SUB(" + field + ", INTERVAL " + diff + " " + unit + ")";
        } else if (EMF.isDriverPostgresql()) {
            return field + " - (" + diff + " * interval '1 " + unit + "')";
        } else {
            throw new RuntimeException("Unknown DB: " + EMF.getDriver());
        }
    }

    public static String getTimeStamp(String field) {
        if (EMF.isDriverHsql()) {
            return "TIMESTAMP(" + field + ")";
        } else if (EMF.isDriverPostgresql()) {
            return field;
        } else {
            throw new RuntimeException("Unknown DB: " + EMF.getDriver());
        }
    }
}
