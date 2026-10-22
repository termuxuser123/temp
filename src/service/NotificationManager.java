package service;

import dao.NotificationDAO;
import dao.impl.NotificationDAOImpl;
import model.Notification;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class NotificationManager {
    private static final NotificationDAO dao = new NotificationDAOImpl();

    public static void send(int userId, String message) {
        try {
            dao.send(userId, message);
        } catch (SQLException e) {
            System.err.println("[NOTIFICATION ERROR] Could not send notification: " + e.getMessage());
        }
    }

    public static List<Notification> getFor(int userId) {
        try {
            return dao.getFor(userId);
        } catch (SQLException e) {
            System.err.println("[NOTIFICATION ERROR] Could not retrieve notifications: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public static int unreadCount(int userId) {
        try {
            return dao.unreadCount(userId);
        } catch (SQLException e) {
            System.err.println("[NOTIFICATION ERROR] Could not get unread count: " + e.getMessage());
            return 0;
        }
    }

    public static void markAllRead(int userId) {
        try {
            dao.markAllRead(userId);
        } catch (SQLException e) {
            System.err.println("[NOTIFICATION ERROR] Could not mark all read: " + e.getMessage());
        }
    }
}
