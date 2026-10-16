package com.artech.portfolio.mf.util;

/**
 * Shared string constants for the Mutual Fund module (MF.md).
 * Kept in one place so the front-end dropdowns, entities and services agree.
 */
public final class MfConstants {

    private MfConstants() {
    }

    // registrationType on MfHeader (MF.md 11 / 12)
    public static final String REG_SIP = "SIP";
    public static final String REG_ONE_TIME = "ONE_TIME";

    // sipType period gap (MF.md 6 / 7)
    public static final String SIP_WEEKLY = "WEEKLY";
    public static final String SIP_MONTHLY = "MONTHLY";
    public static final String SIP_QUARTERLY = "QUARTERLY";
    public static final String SIP_HALF_YEARLY = "HALF_YEARLY";
    public static final String SIP_YEARLY = "YEARLY";

    // header status (MF.md 10 / 11)
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_CLOSED = "CLOSED";

    // transaction txnType (MF.md 8)
    public static final String TXN_SIP_INSTALMENT = "SIP_INSTALMENT";
    public static final String TXN_PURCHASE = "PURCHASE";
    public static final String TXN_REDEMPTION = "REDEMPTION";
    public static final String TXN_SWITCH_IN = "SWITCH_IN";
    public static final String TXN_SWITCH_OUT = "SWITCH_OUT";
    public static final String TXN_DIVIDEND = "DIVIDEND";
    public static final String TXN_OTHER = "OTHER";

    // transaction source
    public static final String SRC_SIP = "SIP";
    public static final String SRC_MANUAL = "MANUAL";
    public static final String SRC_IMPORT = "IMPORT";
    public static final String SRC_REDEMPTION = "REDEMPTION";

    // redemption modes (MF.md 10)
    public static final String REDEEM_UNITS = "UNITS";
    public static final String REDEEM_AMOUNT = "AMOUNT";
    public static final String REDEEM_FULL = "FULL";

    // MfImportBatch fileType (MF.md 14 - xlsx and PDF, plus csv provision)
    public static final String FILE_XLSX = "XLSX";
    public static final String FILE_CSV = "CSV";
    public static final String FILE_PDF = "PDF";

    // MfImportBatch.status
    public static final String BATCH_UPLOADED = "UPLOADED";
    public static final String BATCH_ACCEPTED = "ACCEPTED";
    public static final String BATCH_REJECTED = "REJECTED";

    // MfImportBatch.validStatus - reconciliation color code (MF.md 15)
    public static final String VALID_PENDING = "PENDING";
    public static final String VALID_GREEN = "GREEN";
    public static final String VALID_AMBER = "AMBER";
    public static final String VALID_RED = "RED";

    // MfImportLine.lineType (MF.md 14 - report header = holdings, detail = transactions)
    public static final String LINE_HOLDING = "HOLDING";
    public static final String LINE_TRANSACTION = "TRANSACTION";

    // MfImportLine.status - row colors on the reconciliation screen (MF.md 15 / 20 / 22)
    public static final String LINE_NEW = "NEW";              // green  - new, will be accepted
    public static final String LINE_EXISTS = "EXISTS";        // grey   - already in backend, skipped (MF.md 22)
    public static final String LINE_NEW_SCHEME = "NEW_SCHEME";// orange - scheme not registered yet (MF.md 20)
    public static final String LINE_MAPPED = "MAPPED";        // blue   - user remapped to a registered scheme (MF.md 20)
    public static final String LINE_ERROR = "ERROR";          // red   - row could not be understood (MF.md 15)
    public static final String LINE_SKIPPED = "SKIPPED";      // grey  - empty row (no units/values), ignored on accept
}
