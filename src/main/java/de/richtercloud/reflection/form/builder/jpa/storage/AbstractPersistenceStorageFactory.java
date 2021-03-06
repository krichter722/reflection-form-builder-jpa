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

import de.richtercloud.reflection.form.builder.storage.Storage;
import de.richtercloud.reflection.form.builder.storage.StorageConf;
import de.richtercloud.reflection.form.builder.storage.StorageCreationException;
import de.richtercloud.reflection.form.builder.storage.StorageFactory;
import de.richtercloud.validation.tools.FieldRetriever;

/**
 *
 * @author richter
 * @param <S> the type of storage to create
 * @param <C> the corresponding type of storage configuration
 */
public abstract class AbstractPersistenceStorageFactory<S extends Storage, C extends StorageConf> implements StorageFactory<S, C> {
    private final String persistenceUnitName;
    private final int parallelQueryCount;
    private final FieldRetriever fieldRetriever;

    public AbstractPersistenceStorageFactory(String persistenceUnitName,
            int parallelQueryCount,
            FieldRetriever fieldRetriever) {
        this.persistenceUnitName = persistenceUnitName;
        this.parallelQueryCount = parallelQueryCount;
        this.fieldRetriever = fieldRetriever;
    }

    public String getPersistenceUnitName() {
        return persistenceUnitName;
    }

    public int getParallelQueryCount() {
        return parallelQueryCount;
    }

    public FieldRetriever getFieldRetriever() {
        return fieldRetriever;
    }

    @Override
    public final S create(C storageConf) throws StorageCreationException {
        S retValue = create0(storageConf);
        retValue.start();
        return retValue;
    }

    protected abstract S create0(C storageConf) throws StorageCreationException;
}
