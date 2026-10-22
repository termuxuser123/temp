package ui;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import model.Notification;
import model.User;
import service.NotificationManager;

public class NotificationsPanel extends JPanel {
    private User u;
    private DefaultListModel<String> listModel;
    private JList<String> notifList;
    private JButton markBtn;
    private JButton refBtn;
    private Runnable onUpdate;

    public NotificationsPanel(User u, Runnable onUpdate) {
        this.u = u;
        this.onUpdate = onUpdate;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        listModel = new DefaultListModel<>();
        notifList = new JList<>(listModel);
        notifList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(notifList);

        markBtn = new JButton("Mark all read");
        refBtn = new JButton("Refresh");

        markBtn.addActionListener(e -> {
            if (u != null) {
                NotificationManager.markAllRead(u.getId());
                reload();
            }
        });

        refBtn.addActionListener(e -> reload());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(markBtn);
        top.add(refBtn);

        add(top, BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);

        reload();
    }

    public void reload() {
        if (u == null) return;
        listModel.clear();
        List<Notification> list = NotificationManager.getFor(u.getId());
        for (Notification n : list) {
            String msg = escapeHtml(n.getMessage());
            String time = (n.getCreatedAt() != null) ? n.getCreatedAt().toString().substring(0, 16) : "";
            if (!n.isRead()) {
                // unread in bold
                listModel.addElement("<html><b>" + n.getMessage() + "</b> <span style='color:gray;'>(" + time + ")</span></html>");
            } else {
                listModel.addElement("<html>" + msg + " <span style='color:gray;'>(" + time + ")</span></html>");
            }
        }
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
