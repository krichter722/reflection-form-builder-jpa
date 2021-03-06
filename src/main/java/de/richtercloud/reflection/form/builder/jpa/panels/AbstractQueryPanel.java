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
import de.richtercloud.message.handler.MessageHandler;
import de.richtercloud.reflection.form.builder.fieldhandler.FieldHandlingException;
import de.richtercloud.reflection.form.builder.jpa.storage.FieldInitializer;
import de.richtercloud.reflection.form.builder.jpa.storage.PersistenceStorage;
import de.richtercloud.validation.tools.FieldRetriever;
import java.awt.Component;
import java.awt.LayoutManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultListSelectionModel;
import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The base class for {@link QueryPanel} and {@link QueryListPanel}.
 *
 * @author richter
 * @param <E> the type of entity to query
 */
@SuppressWarnings("PMD.SingularField")
public abstract class AbstractQueryPanel<E> extends JPanel {
    private static final long serialVersionUID = 1L;
    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractQueryPanel.class);
    public final static int QUERY_RESULT_TABLE_HEIGHT_DEFAULT = 100;
    private final Set<QueryPanelUpdateListener> updateListeners = new HashSet<>();
    private final JSeparator bidirectionalControlPanelSeparator;
    private final JLabel queryResultLabel;
    private final EntityTable<E> queryResultTable;
    private final EntityTableModel<E> queryResultTableModel;
    private final TableRowSorter<EntityTableModel<E>> queryResultTableRowSorter;
    private final JScrollPane queryResultTableScrollPane;
    private final ListSelectionModel queryResultTableSelectionModel = new DefaultListSelectionModel();
    private final QueryComponent<E> queryComponent;
    private final Class<?> entityClass;
    private final PersistenceStorage storage;
    private final JSeparator separator;
    private final GroupLayout.SequentialGroup verticalSequentialGroup;
    private final GroupLayout.ParallelGroup horizontalParallelGroup;
    private final BidirectionalControlPanel bidirectionalControlPanel;
    private final MessageHandler messageHandler;
    private final List<E> initialValues;
    private final FieldRetriever fieldRetriever;

    /**
     * Creates an {@code AbstractQueryPanel}.
     * @param bidirectionalControlPanel the bidirectional control panel to use
     * @param queryComponent the query component to use
     * @param fieldRetriever the field retriever
     * @param entityClass the entity class
     * @param storage the storage to use
     * @param fieldInitializer the field initializer
     * @param issueHandler the issue handler to use
     * @param queryResultTableSelectionMode the query result table selection
     *     mode
     * @param initialValues the initial values (to be selected in the query
     *     result if they're present) (subclasses which only support one
     *     selected value should pass this item in a list - KISS)
     * @throws FieldHandlingException if an exception during access to fields
     *     occurs
     * @throws IllegalArgumentException if one occurs during creation of
     *     {@link EntityTableModel}
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public AbstractQueryPanel(BidirectionalControlPanel bidirectionalControlPanel,
            QueryComponent<E> queryComponent,
            FieldRetriever fieldRetriever,
            final Class<?> entityClass,
            PersistenceStorage storage,
            FieldInitializer fieldInitializer,
            IssueHandler issueHandler,
            int queryResultTableSelectionMode,
            List<E> initialValues) throws FieldHandlingException {
        super();
        if(storage == null) {
            throw new IllegalArgumentException("entityManager mustn't be null");
        }
        if(fieldRetriever == null) {
            throw new IllegalArgumentException("reflectionFormBuilder mustn't be null");
        }
        if(entityClass == null) {
            throw new IllegalArgumentException("entityClass mustn't be null");
        }
        if(initialValues == null) {
            throw new IllegalArgumentException("initialValues mustn't be null");
        }
        this.queryComponent = queryComponent;
        this.fieldRetriever = fieldRetriever;
        this.entityClass = entityClass;
        this.storage = storage;
        if(issueHandler == null) {
            throw new IllegalArgumentException("messageHandler mustn't be null");
        }
        this.messageHandler = issueHandler;
        this.initialValues = initialValues;
        this.bidirectionalControlPanelSeparator = new JSeparator();
        this.separator = new JSeparator();
        queryResultLabel = new JLabel();
        queryResultTableScrollPane = new JScrollPane();
        this.queryResultTableModel = new EntityTableModel<>(fieldRetriever);
        queryResultTable = new EntityTable<E>(this.queryResultTableModel) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        QueryComponent.validateEntityClass(entityClass,
                storage);

        queryResultLabel.setText("Query result:");
        queryResultTable.setSelectionModel(this.queryResultTableSelectionModel);
        queryResultTableScrollPane.setViewportView(queryResultTable);
        this.queryResultTableSelectionModel.setSelectionMode(queryResultTableSelectionMode);
        this.queryResultTableRowSorter = new TableRowSorter<>(queryResultTableModel);
        this.queryResultTable.setRowSorter(queryResultTableRowSorter);
        this.queryComponent.addListener((QueryComponentEvent<E> event) -> {
            List<E> queryResults = event.getQueryResults();
            try {
                for(E queryResult : queryResults) {
                    fieldInitializer.initialize(queryResult);
                    //every result retrieved for the query should be
                    //initialized
                }
                //don't initialize a new table model in order to avoid
                //updating model reference on table row sorter (unelegant)
                queryResultTableRowSorter.setSortKeys(null);
                //reset sorting which what user might want after
                //eventually sorting in the query
                queryResultTableModel.clear();
                queryResultTableModel.updateColumns(queryResults);
                queryResultTableModel.addAllEntities(queryResults);
            } catch (FieldHandlingException ex) {
                LOGGER.error("unexpected exception during query execution occured",
                        ex);
                issueHandler.handleUnexpectedException(new ExceptionMessage(ex));
                return;
            }
            for(E initialValue : initialValues) {
                int initialValueIndex = queryResultTableModel.getEntities().indexOf(initialValue);
                queryResultTable.getSelectionModel().addSelectionInterval(initialValueIndex,
                        initialValueIndex);
            }
        });

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        this.horizontalParallelGroup = layout.createParallelGroup(GroupLayout.Alignment.LEADING);
        if(bidirectionalControlPanel != null) {
            horizontalParallelGroup.addComponent(bidirectionalControlPanel)
                    .addGap(18, 18, 18)
                    .addComponent(bidirectionalControlPanelSeparator);
        }
        horizontalParallelGroup
                .addComponent(queryComponent)
                .addComponent(separator)
                .addGroup(layout.createSequentialGroup()
                        .addComponent(queryResultLabel)
                        .addGap(0, 0, Short.MAX_VALUE));

        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(horizontalParallelGroup)
                                .addContainerGap())
        );
        this.verticalSequentialGroup = layout.createSequentialGroup();
        if(bidirectionalControlPanel != null) {
            verticalSequentialGroup.addComponent(bidirectionalControlPanel)
                    .addGap(18, 18, 18)
                    .addComponent(bidirectionalControlPanelSeparator,
                            GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE,
                            GroupLayout.PREFERRED_SIZE);
        }
        verticalSequentialGroup.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(queryComponent)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(separator,
                        GroupLayout.PREFERRED_SIZE,
                        10,
                        GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(queryResultLabel);
        layout.setVerticalGroup(verticalSequentialGroup);

        final TableCellRenderer tableCellRenderer = queryResultTable.getTableHeader().getDefaultRenderer();
        queryResultTable.getTableHeader().setDefaultRenderer((JTable table, Object o, boolean isSelected, boolean hasFocus, int row, int column) -> {
            Component retValue = tableCellRenderer.getTableCellRendererComponent(table, o, isSelected, hasFocus, row, column);
            if(retValue instanceof JComponent) {
                //might not be the case for some Look and Feels
                ((JComponent)retValue).setToolTipText(queryResultTable.getModel().getTooltipTextMap().get(column));
            }
            return retValue;
        });
        queryResultTable.setDefaultRenderer(byte[].class,
                new DefaultTableCellRenderer() {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                        assert value instanceof byte[];
                        byte[] valueCast = (byte[]) value;
                        return super.getTableCellRendererComponent(table,
                                String.format("%d bytes binary data", valueCast.length),
                                isSelected,
                                hasFocus,
                                row,
                                column);
                    }
                });
        //handle initialization of lazily fetched fields in
        //PersistenceStorage.initialize because the table cell renderer has no
        //knowledge of the field the value belongs to (and there's no sense in
        //making the effort to get it this knowledge)
        //In case this is reverted at any point in time note:
        //Some JPA implementations like EclipseLink use IndirectList which
        //implements Collection, but doesn't trigger the default renderer for
        //Collection.class -> register for Object and check type in conditional
        //statements
        this.bidirectionalControlPanel = bidirectionalControlPanel;
    }

    /*
    internal implementation notes:
    - can't add a check whether mgr is a GroupLayout and fail with
    IllegalArgumentException because setLayout is called in JPanel's constructor
    and requires a reference to this which can't be referenced because super
    constructor is called -> keep this implementation as marker for this note
    */
    /**
     * Sets the layout manager.
     * @param mgr the layout manager
     */
    @Override
    @SuppressWarnings("PMD.UselessOverridingMethod")
    public void setLayout(LayoutManager mgr) {
        super.setLayout(mgr);
    }

    @Override
    public GroupLayout getLayout() {
        return (GroupLayout) super.getLayout();
    }

    public GroupLayout.SequentialGroup getVerticalSequentialGroup() {
        return verticalSequentialGroup;
    }

    public GroupLayout.ParallelGroup getHorizontalParallelGroup() {
        return horizontalParallelGroup;
    }

    public void addUpdateListener(QueryPanelUpdateListener updateListener) {
        this.updateListeners.add(updateListener);
    }

    public void removeUpdateListener(QueryPanelUpdateListener updateListener) {
        this.updateListeners.remove(updateListener);
    }

    public Set<QueryPanelUpdateListener> getUpdateListeners() {
        return Collections.unmodifiableSet(updateListeners);
    }

    public void clearSelection() {
        this.queryResultTable.clearSelection();
    }

    public QueryComponent<E> getQueryComponent() {
        return queryComponent;
    }

    public EntityTable<E> getQueryResultTable() {
        return queryResultTable;
    }

    public EntityTableModel<E> getQueryResultTableModel() {
        return queryResultTableModel;
    }

    public JScrollPane getQueryResultTableScrollPane() {
        return queryResultTableScrollPane;
    }

    public PersistenceStorage getStorage() {
        return storage;
    }

    public JLabel getQueryResultLabel() {
        return queryResultLabel;
    }

    public FieldRetriever getFieldRetriever() {
        return fieldRetriever;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public ListSelectionModel getQueryResultTableSelectionModel() {
        return queryResultTableSelectionModel;
    }

    public BidirectionalControlPanel getBidirectionalControlPanel() {
        return bidirectionalControlPanel;
    }

    public MessageHandler getMessageHandler() {
        return messageHandler;
    }

    public List<E> getInitialValues() {
        return Collections.unmodifiableList(initialValues);
    }

    /**
     * Runs the query on the {@link QueryComponent}.
     * @param async whether or not to run the query asynchronously
     */
    public void runQuery(boolean async) {
        getQueryComponent().runQuery(async,
                false //skipHistoryEntryUsageCountIncrement
        );
    }
}
