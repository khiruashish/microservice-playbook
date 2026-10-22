package dev.foodfleet.ch16.legacy;

/**
 * A row of the monolith's ORDERS table as its JDBC code reads it. Column names and encodings are
 * from 2019: status letters, rupees as double, the address pipe-delimited, the time an IST string.
 *
 * @param ordId     "FF" + number
 * @param ordSts    P placed, K in kitchen, D delivered, X cancelled by customer, R refunded
 * @param amt       total in rupees
 * @param custTier  "G", "S", "" or null
 * @param addrLine  "flat|street|area|pincode", parts may be empty, older rows have no pincode
 * @param dlvTs     "dd-MM-yyyy HH:mm" in IST, or null if never delivered
 */
public record LegacyOrderRow(String ordId, String ordSts, double amt, String custTier, String addrLine,
                             String dlvTs) {
}
