package com.btl.dbms.benchmark;

import com.btl.dbms.connection.ConnectionManager;
import java.sql.SQLException;

public class TidbTpccExecutor extends MySqlTpccExecutor {

    public TidbTpccExecutor() throws SQLException {
        super(ConnectionManager.getTiDbConnection());
    }

    @Override
    public String getDbName() {
        return "TiDB-NewSQL";
    }
}
