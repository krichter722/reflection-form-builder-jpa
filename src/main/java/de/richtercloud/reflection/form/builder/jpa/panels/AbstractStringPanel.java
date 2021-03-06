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
package de.richtercloud.reflection.form.builder.jpa.panels;

import de.richtercloud.reflection.form.builder.jpa.storage.PersistenceStorage;
import de.richtercloud.reflection.form.builder.storage.StorageException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;

/**
 *
 * @author richter
 */
public abstract class AbstractStringPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final PersistenceStorage storage;
    private final int initialQueryLimit;
    private final Class<?> entityClass;
    private final String fieldName;
    private final Set<StringPanelUpdateListener> updateListeners = new HashSet<>();

    public AbstractStringPanel(PersistenceStorage storage,
            Class<?> entityClass,
            String fieldName,
            int initialQueryLimit) {
        super();
        this.storage = storage;
        this.entityClass = entityClass;
        this.fieldName = fieldName;
        this.initialQueryLimit = initialQueryLimit;
    }

    public void addUpdateListener(StringPanelUpdateListener updateListener) {
        this.updateListeners.add(updateListener);
    }

    public void removeUpdateListener(StringPanelUpdateListener updateListener) {
        this.updateListeners.remove(updateListener);
    }

    public Set<StringPanelUpdateListener> getUpdateListeners() {
        return Collections.unmodifiableSet(updateListeners);
    }

    /**
     * Queries the database to check if there's entities which match the current
     * value of the text field with a LIKE query.
     * @param textFieldText the text field text to check
     * @return the list of suggestions
     */
    /*
    internal implementation notes:
    - retrieving Query.getResultList is the shortest, maybe only way of getting
    the size of the result set
    */
    protected List<String> check(String textFieldText) throws StorageException {
        String queryText = generateQueryText(textFieldText);
        List<String> retValue = storage.runQuery(queryText,
                String.class, //clazz
                this.initialQueryLimit);
        return retValue;
    }

    /**
     * Since there's no converter between text and criteria API or JPA Query
     * instances<ref>http://stackoverflow.com/questions/6276122/can-i-get-the-sql-string-from-a-jpa-query-object</ref>
     * we need to use text everywhere.
     *
     * @param textFieldText the text field text
     * @return the generated query text
     */
    protected String generateQueryText(String textFieldText) {
        //if there ever is a way to convert to text use the following saver
        //routine:
//        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
//        CriteriaQuery<T> c = cb.createQuery(this.entityClass);
//        Root<T> emp = c.from(this.entityClass);
//        c.select(emp);
//        List<Predicate> criteria = new ArrayList<>();
//        ParameterExpression<String> p = cb.parameter(String.class, this.fieldName);
//        criteria.add(cb.like(emp.<String>get(this.fieldName), p));
//        c.where(criteria.get(0));
//        TypedQuery<T> q = this.entityManager.createQuery(c);

        String entityClassQueryIdentifier = QueryHistoryEntryStorageFactory.generateEntityClassQueryIdentifier(entityClass);
        String retValue = String.format("SELECT %s.%s from %s %s WHERE %s.%s LIKE '%s'",
                entityClassQueryIdentifier,
                this.fieldName,
                entityClass.getSimpleName(),
                entityClassQueryIdentifier,
                entityClassQueryIdentifier,
                this.fieldName,
                "%"+textFieldText.replaceAll("'", "''")
                        //' is escaped with ''<ref>http://stackoverflow.com/questions/9891205/escape-character-in-jpql</ref>
                        +"%" //specify % here in order to
                    //keep them output of the format string
        );
        return retValue;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getFieldName() {
        return fieldName;
    }

    /**
     * Resets the component.
     */
    public abstract void reset();
}
