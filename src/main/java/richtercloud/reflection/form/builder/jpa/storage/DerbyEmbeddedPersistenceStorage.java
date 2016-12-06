/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package richtercloud.reflection.form.builder.jpa.storage;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author richter
 */
public class DerbyEmbeddedPersistenceStorage extends AbstractPersistenceStorage<DerbyEmbeddedPersistenceStorageConf> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DerbyEmbeddedPersistenceStorage.class);

    public DerbyEmbeddedPersistenceStorage(DerbyEmbeddedPersistenceStorageConf storageConf) {
        super(storageConf);
    }

    @Override
    public void shutdown() {
        super.shutdown();

        //call to DriverManager.getConnection(String.format("%s;shutdown=true", DERBY_CONNECTION_URL));
        //fails due to `java.sql.SQLNonTransientConnectionException: Database '/home/richter/.document-scanner/databases' shutdown.`
        //with cause `Caused by: org.apache.derby.iapi.error.StandardException: Database '/home/richter/.document-scanner/databases' shutdown.`
        //(maybe EntityManagerFactory.close shuts down the database), but is
        //somehow necessary in order to remove db.lck in the database directory.
        try {
            LOGGER.info("Shutting down database and database server");
            DriverManager.getConnection(String.format("%s;shutdown=true",
                    getStorageConf().getConnectionURL()));
                //shutdown Derby (supposed to remove db.lck<ref>http://db.apache.org/derby/docs/10.8/devguide/tdevdvlp40464.html#tdevdvlp40464</ref>, but doesn't)
        } catch (SQLException ex) {
            LOGGER.error("an exception during shutdown of the database connection occured", ex);
        }
    }
}