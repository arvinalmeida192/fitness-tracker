package com.fittrack.persistence.sqlite;

import com.fittrack.domain.common.DataAccessException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Small helper for multi-statement SQLite writes on the shared connection.
 */
public final class ConnectionTxn {

    @FunctionalInterface
    public interface Work {
        void run();
    }

    private ConnectionTxn() {
    }

    public static void run(Database database, Work work) {
        Connection conn = database.getConnection();
        boolean committed = false;
        try {
            conn.setAutoCommit(false);
            work.run();
            conn.commit();
            committed = true;
        } catch (SQLException e) {
            throw new DataAccessException("Transaction failed", e);
        } catch (RuntimeException e) {
            throw e;
        } finally {
            if (!committed) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                    // ignore
                }
            }
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // ignore
            }
        }
    }
}
