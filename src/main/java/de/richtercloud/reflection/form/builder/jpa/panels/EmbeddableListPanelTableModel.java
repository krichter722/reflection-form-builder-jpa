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

import de.richtercloud.message.handler.ExceptionMessage;
import de.richtercloud.message.handler.IssueHandler;
import de.richtercloud.reflection.form.builder.panels.ListPanelTableModel;
import de.richtercloud.validation.tools.FieldRetriever;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.DefaultTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Doesn't initialize column names which has to be done in caller.
 * @author richter
 */
public class EmbeddableListPanelTableModel extends DefaultTableModel implements ListPanelTableModel<Object> {
    private static final long serialVersionUID = 1L;
    private final static Logger LOGGER = LoggerFactory.getLogger(EmbeddableListPanelTableModel.class);
    private final List<Object> embeddables = new ArrayList<>();
    private final Constructor<?> embeddableClassConstructor;
    private final List<Field> embeddableClassFields;
    private final IssueHandler issueHandler;

    /**
     *
     * @param embeddableClass the embeddable class
     * @param fieldRetriever the field retriever
     * @param issueHandler the issue handler to use
     * @throws IllegalArgumentException if {@code embeddableClass} doesn't have
     *     a zero-argument constructor and raises {@link NoSuchMethodException}
     *     at retrieval of this constructor with
     *     {@link Class#getDeclaredConstructor(java.lang.Class...) }
     */
    public EmbeddableListPanelTableModel(Class<?> embeddableClass,
            FieldRetriever fieldRetriever,
            IssueHandler issueHandler) {
        super(0, //rowCount
                fieldRetriever.retrieveRelevantFields(embeddableClass).size() //columnCount
        );
        if(embeddableClass == null) {
            throw new IllegalArgumentException("embeddableClass mustn't be null");
        }
        if(issueHandler == null) {
            throw new IllegalArgumentException("issueHandler mustn't be null");
        }
        this.issueHandler = issueHandler;
        try {
            this.embeddableClassConstructor = embeddableClass.getDeclaredConstructor();
        }catch(NoSuchMethodException ex) {
            throw new IllegalArgumentException(String.format("embeddableClass %s doesn't have a zero-argument constructor", embeddableClass), ex);
        }
        this.embeddableClassConstructor.setAccessible(true);
        this.embeddableClassFields = fieldRetriever.retrieveRelevantFields(embeddableClass);
    }

    /*
    internal implementation notes:
    - must not be unmodifiable because Hibernate will invoke clear on it @TODO
    check if that can be fixed because this violates the interface specification
    (i.e. the idea to enforce all manipulations through other methods which is a
    quite common use case and hibernate should support it)
    */
    /**
     * Gets the data.
     * @return a reference to {@code embeddables}.
     */
    @Override
    public List<Object> getData() {
        return this.embeddables;
    }

    @Override
    public void addColumn(String columnName) {
        super.addColumn(columnName);//should fire data change event
        this.fireTableStructureChanged();
    }

    @Override
    public void removeElement(int row) {
        embeddables.remove(row);
        this.fireTableDataChanged();
    }

    @Override
    public void addElement(Object element) {
        this.embeddables.add(element);
        this.fireTableDataChanged();
    }

    @Override
    public void insertElementAt(int row, Object element) {
        this.embeddables.add(row, element);
        this.fireTableDataChanged();
    }

    @Override
    public void removeElement(Object element) {
        this.embeddables.remove(element);
        this.fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        if(this.embeddables == null) {
            //during initialization (this is inefficient, but due to the bad design of DefaultTableModel
            return 0;
        }
        return embeddables.size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return this.embeddableClassFields.get(columnIndex).getType();
    }

    /**
     * Check whether the table cell with the specified row and column index is
     * editable
     * @param rowIndex the row index
     * @param columnIndex the column index
     * @return always {@code false} because editing takes place with
     *     {@link ReflectionFormPanel} in dialog
     */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    @SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
    public Object getValueAt(int rowIndex, int columnIndex) {
        try {
            return this.embeddableClassFields.get(columnIndex).get(this.embeddables.get(rowIndex));
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            LOGGER.error("unexpected exception during retrieval of table cell value occured",
                    ex);
            issueHandler.handleUnexpectedException(new ExceptionMessage(ex));
            throw new RuntimeException(ex);
        }
    }

    /**
     * Configures columns 0...n to represent the embeddable {@code aValue}.
     * Therefore {@code columnIndex} is ignored and a value != 0 causes an
     * {@link IllegalArgumentException} to be thrown.
     * @param aValue the value to set
     * @param rowIndex the row index
     * @param columnIndex the column index
     * @throws IllegalArgumentException if {@code columnIndex} > 0
     */
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if(columnIndex > 0) {
            throw new IllegalArgumentException("columnIndex > 0 isn't supported");
        }
        Object embeddable;
        if(rowIndex > this.embeddables.size()) {
            throw new IllegalArgumentException(String.format("rowIndex (%d) must not be larger than the size of embeddables (%d)", rowIndex, this.embeddables.size())); //if this becomes a problem introduce null padding
        }else if(rowIndex == this.embeddables.size()) {
            try {
                embeddable = embeddableClassConstructor.newInstance();
            } catch (InstantiationException
                    | IllegalAccessException
                    | IllegalArgumentException
                    | InvocationTargetException ex) {
                LOGGER.error("unexpected exception during setting of table cell value occured",
                        ex);
                issueHandler.handleUnexpectedException(new ExceptionMessage(ex));
                return;
            }
            this.embeddables.add(embeddable);
        }else {
            embeddable = this.embeddables.get(rowIndex);
        }
        try {
            for(Field embeddableClassField : this.embeddableClassFields) {
                embeddableClassField.set(embeddable, embeddableClassField.get(aValue));
            }
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            LOGGER.error("unexpected exception during setting of table cell value occured",
                    ex);
            issueHandler.handleUnexpectedException(new ExceptionMessage(ex));
        }
    }

    @Override
    public String getColumnName(int column) {
        return this.embeddableClassFields.get(column).getName();
    }

    @Override
    public int getColumnCount() {
        return this.embeddableClassFields.size();
    }
}
