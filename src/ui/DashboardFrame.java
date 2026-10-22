package ui;

import javax.swing.*;
import java.awt.*;
import model.User;
import service.Library;
import service.NotificationManager;

public class DashboardFrame extends JFrame {
    private User u;
    private Library lib;

    public DashboardFrame(User u, Library lib) {
        super("Library System");
        this.u = u;
        this.lib = lib;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // check if user is admin
        boolean isadmin = "ADMIN".equalsIgnoreCase(u.getRole());

        // top bar with welcome msg and logout
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // show role or borrowing limit
        String info = "";
        if (isadmin) {
            info = " | Admin";
        } else {
            info = " | Limit: " + u.getMaxBookAllowed() + " books";
        }

        JLabel lbl = new JLabel("Welcome, " + u.getName() + " (" + u.getRole() + ")" + info);
        JButton btn = new JButton("Logout");

        // back to login
        btn.addActionListener(e -> {
            dispose();
            new LoginFrame();
        });

        top.add(lbl, BorderLayout.WEST);
        top.add(btn, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // tabs for different sections
        JTabbedPane tp = new JTabbedPane();

        ReservationsPanel waitlistPanel = new ReservationsPanel(u, lib);

        // update notification badge title
        Runnable updateBadge = () -> {
            int count = NotificationManager.unreadCount(u.getId());
            for (int i = 0; i < tp.getTabCount(); i++) {
                if (tp.getTitleAt(i).startsWith("Notifications")) {
                    tp.setTitleAt(i, (count > 0) ? "Notifications (" + count + ")" : "Notifications");
                    break;
                }
            }
        };

        NotificationsPanel notifPanel = new NotificationsPanel(u, updateBadge);

        BookSearchPanel searchPanel = new BookSearchPanel(u, lib, waitlistPanel::reload);
        tp.addTab("Search Books", searchPanel);

        LoansPanel loansPanel = isadmin ? null : new LoansPanel(u);

        if (isadmin) {
            // admin tabs
            tp.addTab("Manage Books", new ManageBooksPanel(lib));
            tp.addTab("Reports", new ReportPanel(lib));
            tp.addTab("Pending Registrations", new PendingRegistrationsPanel()); 
        } else {
            // borrower tabs
            tp.addTab("Borrow / Return", new IssueReturnPanel(u, lib, lib));
            tp.addTab("My Loans", loansPanel);
            tp.addTab("My Waitlist", waitlistPanel);
        }

        int unread = NotificationManager.unreadCount(u.getId());
        String notifTabTitle = (unread > 0) ? "Notifications (" + unread + ")" : "Notifications";
        tp.addTab(notifTabTitle, notifPanel);

        // refresh tabs when user switches to them
        tp.addChangeListener(e -> {
            int sel = tp.getSelectedIndex();
            if (sel != -1) {
                String title = tp.getTitleAt(sel);
                if (title.startsWith("Notifications")) {
                    notifPanel.reload();
                } else if ("My Waitlist".equals(title)) {
                    waitlistPanel.reload();
                } else if ("Search Books".equals(title)) {
                    searchPanel.reload();
                } else if ("My Loans".equals(title) && loansPanel != null) {
                    loansPanel.loadloan();
                }
            }
        });

        add(tp, BorderLayout.CENTER);

        setSize(850, 520);
        setLocationRelativeTo(null);
        setVisible(true);

        // popup after login if unread count > 0
        if (unread > 0) {
            JOptionPane.showMessageDialog(this,
                    "You have " + unread + " unread notification(s)!\nA reserved book may now be available.",
                    "Notifications", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // for testing directly
    public static void main(String[] args) {
        model.User tu = new model.Student("Test Student", "student@test.com", 1, "1234567890", "pass123");
        new DashboardFrame(tu, new Library());
    }
}
