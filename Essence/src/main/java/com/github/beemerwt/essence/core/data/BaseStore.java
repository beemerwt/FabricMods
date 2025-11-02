package com.github.beemerwt.essence.core.data;

import java.sql.Connection;

abstract class BaseStore {
    protected final Connection conn;
    protected BaseStore(Database db) { this.conn = db.conn(); }
}
