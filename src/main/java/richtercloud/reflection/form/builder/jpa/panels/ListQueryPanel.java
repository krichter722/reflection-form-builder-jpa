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
package richtercloud.reflection.form.builder.jpa.panels;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import javax.persistence.EntityManager;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import richtercloud.reflection.form.builder.ReflectionFormBuilder;
import richtercloud.reflection.form.builder.panels.AbstractListPanel;

/**
 *
 * @author richter
 */
public class ListQueryPanel extends javax.swing.JPanel {
    private static final long serialVersionUID = 1L;
    private final static Logger LOGGER = LoggerFactory.getLogger(ListQueryPanel.class);
    private DefaultTableModel resultTableModel = new DefaultTableModel();
    private EntityManager entityManager;
    private ReflectionFormBuilder reflectionFormBuilder;
    private Class<?> entityClass;
    private Set<ListQueryPanelUpdateListener> updateListeners = new HashSet<>();
    /**
     * A constantly up-to-date list of selected references (the "result" of the
     * panel)
     */
    private List<Object> resultList = new LinkedList<>();

    /**
     * Creates new form ListQueryPanel
     */
    public ListQueryPanel() {
    }

    public ListQueryPanel(EntityManager entityManager, ReflectionFormBuilder reflectionFormBuilder, Class<?> entityClass) {
        this.entityManager = entityManager;
        this.reflectionFormBuilder = reflectionFormBuilder;
        this.entityClass = entityClass;
        QueryPanel.initTableModel(this.resultTableModel, this.reflectionFormBuilder.retrieveRelevantFields(entityClass));
        initComponents();
        this.resultTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    public void addUpdateListener(ListQueryPanelUpdateListener updateListener) {
        this.updateListeners.add(updateListener);
    }

    public void removeUpdateListener(ListQueryPanelUpdateListener updateListener) {
        this.updateListeners.remove(updateListener);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        queryPanel = new QueryPanel<>(entityManager, entityClass, reflectionFormBuilder, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        ;
        removeButton = new javax.swing.JButton();
        addButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        resultTableScrollPane = new javax.swing.JScrollPane();
        resultTable = new JTable() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        removeButton.setText("Remove");
        removeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeButtonActionPerformed(evt);
            }
        });

        addButton.setText("Add");
        addButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addButtonActionPerformed(evt);
            }
        });

        jLabel1.setText("Selected entities:");

        resultTable.setModel(this.resultTableModel);
        resultTableScrollPane.setViewportView(resultTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(queryPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(resultTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 573, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(addButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(removeButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(queryPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 222, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(removeButton)
                    .addComponent(addButton)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(resultTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 167, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addButtonActionPerformed
        int[] indices = this.queryPanel.getQueryResultTable().getSelectedRows();
        for(int index : indices) {
            Object queryResult = this.queryPanel.getQueryResults().get(index);
            try {
                QueryPanel.handleInstanceToTableModel(this.resultTableModel, queryResult, reflectionFormBuilder, entityClass);
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                LOGGER.info("an exception occured while executing the query", ex);
                this.queryPanel.getQueryStatusLabel().setText(String.format("<html>%s</html>", ex.getMessage()));
            }
            this.resultList.add(queryResult);
        }
        for(ListQueryPanelUpdateListener updateListener : updateListeners) {
            updateListener.onUpdate(new ListQueryPanelUpdateEvent(this.resultList));
        }
    }//GEN-LAST:event_addButtonActionPerformed

    private void removeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeButtonActionPerformed
        int[] selectedRows = this.resultTable.getSelectedRows();
        if(selectedRows.length == 0) {
            return;
        }
        //need to sort in order to remove from highest to lowest value
        PriorityQueue<Integer> selectedRowsSorted = new PriorityQueue<>(selectedRows.length, AbstractListPanel.DESCENDING_ORDER);
        for(int selectedRow : selectedRows) {
            selectedRowsSorted.add(selectedRow);
        }
        for(int selectedRow : selectedRowsSorted) {
            this.resultTableModel.removeRow(selectedRow);
            Object queryResult = this.queryPanel.getQueryResults().get(selectedRow);
            this.resultList.remove(queryResult);
        }
        for(ListQueryPanelUpdateListener updateListener : updateListeners) {
            updateListener.onUpdate(new ListQueryPanelUpdateEvent(this.resultList));
        }

    }//GEN-LAST:event_removeButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addButton;
    private javax.swing.JLabel jLabel1;
    private richtercloud.reflection.form.builder.jpa.panels.QueryPanel queryPanel;
    private javax.swing.JButton removeButton;
    private javax.swing.JTable resultTable;
    private javax.swing.JScrollPane resultTableScrollPane;
    // End of variables declaration//GEN-END:variables
}