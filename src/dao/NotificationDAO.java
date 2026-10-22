package dao;

import java.sql.SQLException;
import java.util.List;
import model.Notification;

public interface NotificationDAO {
    void send(int userId, String message) throws SQLException;
    List<Notification> getFor(int userId) throws SQLException;
    int unreadCount(int userId) throws SQLException;
    void markAllRead(int userId) throws SQLException;
}
