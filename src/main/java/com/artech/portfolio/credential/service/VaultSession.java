package com.artech.portfolio.credential.service;

import com.artech.portfolio.credential.util.VaultConstants;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * CRED_V1 session management - the DEK lives here and nowhere else.
 *
 * <p>It is never written to the database, to a file, to a log or to the browser;
 * the browser only ever receives a single password it asked for. After
 * {@link VaultConstants#IDLE_LOCK_MINUTES} minutes without a real operation the
 * key is dropped, so the next action needs the Master Password again.</p>
 *
 * <p>The status poll deliberately does not count as activity - otherwise a page
 * left open would keep the vault unlocked for ever.</p>
 */
@Service
public class VaultSession {

    private static final Duration IDLE_LOCK =
            Duration.ofMinutes(VaultConstants.IDLE_LOCK_MINUTES);

    private final Clock clock;

    private SecretKey dek;
    private Instant lastActivity;

    public VaultSession(Clock clock) {
        this.clock = clock;
    }

    /** Unlock: the DEK has just been recovered from vault_config. */
    public synchronized void unlock(SecretKey dek) {
        this.dek = dek;
        this.lastActivity = Instant.now(clock);
    }

    /** Lock: drop the key and let it be garbage collected. */
    public synchronized void lock() {
        this.dek = null;
        this.lastActivity = null;
    }

    /** Read-only state for the screen - does not extend the session. */
    public synchronized boolean isUnlocked() {
        return liveKey() != null;
    }

    /**
     * The DEK for one operation, or a clear error when the vault is locked.
     * Counts as activity, so a working screen keeps its session.
     */
    public synchronized SecretKey requireDek() {
        SecretKey key = liveKey();
        if (key == null) {
            throw new IllegalStateException(
                    "The vault is locked - enter the Master Password to unlock it again");
        }
        lastActivity = Instant.now(clock);
        return key;
    }

    /** Expiry check shared by both accessors; never extends the session. */
    private SecretKey liveKey() {
        if (dek == null) {
            return null;
        }
        if (Duration.between(lastActivity, Instant.now(clock)).compareTo(IDLE_LOCK) >= 0) {
            dek = null;
            lastActivity = null;
            return null;
        }
        return dek;
    }
}
