package ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import model.User;
import model.IssueTransaction;
import dao.TransactionDAO;
import dao.impl.TransactionDAOImpl;
import service.FinePolicy;
import service.StandardFinePolicy;
import service.FacultyFinePolicy;
import java.sql.SQLException;

public class LoansPanel extends JPanel {
    private User u;
    private DefaultTableModel tablem;
    private JTable loant;
    private JButton refbtn;
    private JLabel summarylbl;
    private TransactionDAO txndao = new TransactionDAOImpl();

    // load loans from database
    public void loadloan() {
        try {
            tablem.setRowCount(0);
            List<IssueTransaction> ll = txndao.findActiveByUser(u.getId());
            LocalDate today = LocalDate.now();

            // pick policy based on role
            FinePolicy policy;
            if ("Faculty".equalsIgnoreCase(u.getRole())) {
                policy = new FacultyFinePolicy();
            } else {
                policy = new StandardFinePolicy();
            }

            double totfine = 0.0;
            int overduecount = 0;

            // calculate fine for each loan
            for (IssueTransaction it : ll) {
                long overdue = ChronoUnit.DAYS.between(it.getDueDate(), today);
                int overduedays = (int) Math.max(0, overdue);
                if (overdue > 0) {
                    overduecount++;
                }

                double fine = policy.calculateFine(overduedays);
                totfine += fine;

                String status = "ACTIVE";
                if (overdue > 0) {
                    status = "OVERDUE";
                }

                tablem.addRow(new Object[] { it.getTxnId(), it.getBook().getIsbn(), it.getBook().getTitle(),
                        it.getIssueDate(), it.getDueDate(), overduedays, String.format("₹%.2f", fine), status });
            }

            // update the summary text
            summarylbl.setText(
                    String.format("Summary: %d Loans, %d Overdue, ₹%.2f Fine", ll.size(), overduecount, totfine));
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public LoansPanel(User u) {
        this.u = u;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        String[] cols = { "Txn ID", "ISBN", "Title", "Issue Date", "Due Date", "Days Overdue", "Fine (₹)", "Status" };
        tablem = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        loant = new JTable(tablem);
        JScrollPane sp = new JScrollPane(loant);
        refbtn = new JButton("Refresh / Recalculate Fines");
        summarylbl = new JLabel("Summary: 0 Loans, 0 Overdue, ₹0.00 Fine");

        // add components to panel
        add(sp, BorderLayout.CENTER);
        add(summarylbl, BorderLayout.SOUTH);
        add(refbtn, BorderLayout.NORTH);

        refbtn.addActionListener(e -> loadloan());
        loadloan();
    }

    // test method
    public static void main(String[] args) {
        JFrame f = new JFrame("My Loans");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(new LoansPanel(new model.Student("Test Student", "student@test.com", 1, "1234567890", "pass123")));
        f.pack();
        f.setSize(750, 400);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
