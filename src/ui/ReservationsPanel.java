package ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import model.User;
import model.WaitlistEntry;
import service.Library;
import service.WaitlistManager;

public class ReservationsPanel extends JPanel {
    private User u;
    private Library lib;
    private DefaultTableModel tablem;
    private JTable waitlistt;
    private JButton leavebtn;
    private JButton refbtn;
    private JLabel statuslbl;
    private WaitlistManager waitlistManager = new WaitlistManager();

    public void reload() {
        if (u == null) return;
        tablem.setRowCount(0);
        List<WaitlistEntry> list = waitlistManager.getMyWaitlist(u.getId());
        for (WaitlistEntry e : list) {
            String posStr = (e.getPosition() > 0) ? String.valueOf(e.getPosition()) : "-";
            String statusDisp = "NOTIFIED".equalsIgnoreCase(e.getStatus()) ? "AVAILABLE" : e.getStatus();
            tablem.addRow(new Object[] { e.getIsbn(), e.getTitle(), posStr, statusDisp });
        }
        statuslbl.setText("Total Entries: " + list.size());
    }

    public ReservationsPanel() {
        this(null, new Library());
    }

    public ReservationsPanel(User u, Library lib) {
        this.u = u;
        this.lib = lib;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] cols = { "ISBN", "Title", "Position", "Status" };
        tablem = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        waitlistt = new JTable(tablem);
        JScrollPane sp = new JScrollPane(waitlistt);

        leavebtn = new JButton("Leave waitlist");
        refbtn = new JButton("Refresh");
        statuslbl = new JLabel("Your active waitlist entries");

        leavebtn.addActionListener(e -> {
            int r = waitlistt.getSelectedRow();
            if (r == -1) {
                JOptionPane.showMessageDialog(this, "Please select an entry to leave the waitlist.");
                return;
            }
            String isbn = (String) tablem.getValueAt(r, 0);
            String title = (String) tablem.getValueAt(r, 1);
            int opt = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to leave the waitlist for '" + title + "'?",
                    "Leave Waitlist", JOptionPane.YES_NO_OPTION);
            if (opt == JOptionPane.YES_OPTION) {
                waitlistManager.leaveWaitlist(isbn, u.getId());
                JOptionPane.showMessageDialog(this, "Left waitlist for '" + title + "'.");
                reload();
            }
        });

        refbtn.addActionListener(e -> reload());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(leavebtn);
        top.add(refbtn);

        add(top, BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);
        add(statuslbl, BorderLayout.SOUTH);

        reload();
    }
}
