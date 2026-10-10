package com.artech.portfolio.common;

import org.hibernate.dialect.identity.IdentityColumnSupportImpl;

public class SQLiteIdentityColumnSupport extends IdentityColumnSupportImpl {

    @Override
    public boolean supportsIdentityColumns() {
        return true;
    }

    @Override
    public String getIdentityColumnString(int type) {
        // SQLite uses INTEGER PRIMARY KEY AUTOINCREMENT
        return "integer";
    }

    @Override
    public String getIdentitySelectString(String table, String column, int type) {
        return "select last_insert_rowid()";
    }

    @Override
    public boolean hasDataTypeInIdentityColumn() {
        return false;
    }
}
