/*
 * (c) 2019-2020 Ionic Security Inc. By using this code, I agree to the Terms & Conditions
 * (https://dev.ionic.com/use.html) and the Privacy Policy (https://www.ionic.com/privacy-notice/).
 */

package com.ionic.jdbc;

import com.ionic.sdk.agent.Agent;
import com.ionic.sdk.agent.cipher.chunk.ChunkCipherV2;
import com.ionic.sdk.agent.cipher.chunk.data.ChunkCrypto;
import com.ionic.sdk.agent.request.getkey.GetKeysResponse;
import com.ionic.sdk.crypto.CryptoUtils;
import com.ionic.sdk.error.IonicException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collection;
import org.apache.commons.dbutils.ResultSetHandler;

/**
 * Implementation of commons-dbutils interface {@link ResultSetHandler}.
 * 
 * <p>Business logic for loading data from a {@link java.sql.ResultSet}.
 */
public class IonicResultSetHandler implements ResultSetHandler<RowSet> {

    /**
     * Test Ionic agent, used to protect data on insert into database, and unprotect data on fetch
     * from database.
     */
    private final Agent agent;

    /**
     * Constructor.
     *
     * @param agent Ionic agent, used to protect data on insert into database, and unprotect data on
     *        fetch from database
     */
    public IonicResultSetHandler(final Agent agent) {
        super();
        this.agent = agent;
    }

    /**
     * Turn the ResultSet into an Object.
     *
     * <p>Ionic-protected columns are being tracked as they are encountered in the
     * {@link ResultSet}. If all Machina key operations for a given record fail, the row is
     * filtered out of the data returned by the function.
     *
     * @param resultSet the JDBC {@link ResultSet} from the database
     * @return the Ionic-filtered representation of the input {@link ResultSet}
     * @throws SQLException on errors reading from the {@link ResultSet}
     */
    @Override
    public RowSet handle(final ResultSet resultSet) throws SQLException {

        final RowSet rowSet = new RowSet();
        final ResultSetMetaData metaData = resultSet.getMetaData();
        final int columnCount = metaData.getColumnCount();

        while (resultSet.next()) {
            final Object[] row = new Object[columnCount];

            for (int i = 0; i < columnCount; ++i) {
                final String columnName = metaData.getColumnName(i + 1);
                final Object value = resultSet.getObject(i + 1);
                final String valueText = (value == null) ? null : value.toString();
                row[i] = valueText;
            }
            rowSet.add(row);
        }
        return rowSet;
    }
}
