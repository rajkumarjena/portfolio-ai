package com.artech.portfolio.maintenance;

/**
 * BACKUP.md 4/5: the state every page needs to show (and automatically drop)
 * the site-wide warning banner while a database backup or refresh is running.
 *
 * Served publicly by GET /api/maintenance/status - it is a pure in-memory
 * snapshot, it never touches the database, so signed-out users can poll it too.
 *
 * @param active    true while a drain pause or a backup/refresh is running
 * @param phase     IDLE, DRAINING (the 5-10s pause) or ACTIVE (the real work)
 * @param process   BACKUP or REFRESH while active, null when idle
 * @param message   the warning text the banner displays
 * @param updatedAt epoch millis of the last phase transition (stale-recovery)
 */
public record MaintenanceStatus(boolean active, String phase, String process,
                                String message, long updatedAt) {

    /** Generic text used before the first message of a run is published. */
    public static final String DEFAULT_MESSAGE =
            "Database maintenance is in progress - please stay away from new transactions "
                    + "until it finishes.";
}
