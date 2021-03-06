/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.richtercloud.reflection.form.builder.jpa.storage;

import de.richtercloud.reflection.form.builder.storage.StorageConfValidationException;
import de.richtercloud.reflection.form.builder.storage.StorageCreationException;
import de.richtercloud.validation.tools.FieldRetriever;

/**
 *
 * @author richter
 */
public class PostgresqlPersistenceStorageFactory extends AbstractPersistenceStorageFactory<PostgresqlPersistenceStorage, PostgresqlPersistenceStorageConf>{

    public PostgresqlPersistenceStorageFactory(String persistenceUnitName,
            int parallelQueryCount,
            FieldRetriever fieldRetriever) {
        super(persistenceUnitName,
                parallelQueryCount,
                fieldRetriever);
    }

    @Override
    public PostgresqlPersistenceStorage create0(PostgresqlPersistenceStorageConf storageConf) throws StorageCreationException {
        PostgresqlPersistenceStorage retValue;
        try {
            retValue = new PostgresqlPersistenceStorage(storageConf,
                    getPersistenceUnitName(),
                    getParallelQueryCount(),
                    getFieldRetriever());
        } catch (StorageConfValidationException ex) {
            throw new StorageCreationException(ex);
        }
        return retValue;
    }
}
