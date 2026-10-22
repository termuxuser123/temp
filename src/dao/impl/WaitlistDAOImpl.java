package dao.impl;

import dao.WaitlistDAO;
import database.DBConnectionManager;
import model.User;
import model.WaitlistEntry;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WaitlistDAOImpl implements WaitlistDAO {

    @Override
    public void add(String isbn, User user) throws SQLException {
        add(isbn, null, user);
    }

    @Override
    public void add(String isbn, String title, User user) throws SQLException {
        try (Connection con = DBConnectionManager.getConnection()) {
            String sql = "INSERT INTO waitlist (isbn, title, user_id, request_date, status) VALUES (?, ?, ?, NOW(6), 'WAITING') "
                       + "ON DUPLICATE KEY UPDATE title = COALESCE(VALUES(title), title), status = 'WAITING', request_date = NOW(6)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, isbn);
                ps.setString(2, title);
                ps.setInt(3, user.getId());
                ps.executeUpdate();
            } catch (SQLException e) {
                // Fallback for schema without title/status columns
                String fallback = "INSERT INTO waitlist (isbn, user_id, request_date) VALUES (?, ?, NOW(6)) ON DUPLICATE KEY UPDATE request_date = NOW(6)";
                try (PreparedStatement fps = con.prepareStatement(fallback)) {
                    fps.setString(1, isbn);
                    fps.setInt(2, user.getId());
                    fps.executeUpdate();
                }
            }
        }
    }

    @Override
    public void add(String isbn, User user, Connection con) throws SQLException {
        String sql = "INSERT INTO waitlist (isbn, user_id, request_date, status) VALUES (?, ?, NOW(6), 'WAITING') "
                   + "ON DUPLICATE KEY UPDATE status = 'WAITING', request_date = NOW(6)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbn);
            ps.setInt(2, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            String fallback = "INSERT INTO waitlist (isbn, user_id, request_date) VALUES (?, ?, NOW(6)) ON DUPLICATE KEY UPDATE request_date = NOW(6)";
            try (PreparedStatement fps = con.prepareStatement(fallback)) {
                fps.setString(1, isbn);
                fps.setInt(2, user.getId());
                fps.executeUpdate();
            }
        }
    }

    @Override
    public User popNext(String isbn) throws SQLException {
        try (Connection con = DBConnectionManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                User next = popNext(isbn, con);
                con.commit();
                return next;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    @Override
    public User popNext(String isbn, Connection con) throws SQLException {
        String select = "SELECT u.* FROM waitlist w JOIN users u ON w.user_id = u.user_id "
                + "WHERE w.isbn = ? AND (w.status IS NULL OR w.status = 'WAITING') "
                + "ORDER BY w.request_date ASC, w.user_id ASC LIMIT 1 FOR UPDATE";
        String update = "UPDATE waitlist SET status = 'NOTIFIED' WHERE isbn = ? AND user_id = ?";

        User next = null;
        try (PreparedStatement ps = con.prepareStatement(select)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) next = buildUserFromResultSet(rs);
            }
        } catch (SQLException e) {
            // Fallback without status column
            String fallbackSelect = "SELECT u.* FROM waitlist w JOIN users u ON w.user_id = u.user_id "
                    + "WHERE w.isbn = ? ORDER BY w.request_date ASC LIMIT 1 FOR UPDATE";
            try (PreparedStatement ps = con.prepareStatement(fallbackSelect)) {
                ps.setString(1, isbn);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) next = buildUserFromResultSet(rs);
                }
            }
            if (next != null) {
                String delete = "DELETE FROM waitlist WHERE isbn = ? AND user_id = ?";
                try (PreparedStatement del = con.prepareStatement(delete)) {
                    del.setString(1, isbn);
                    del.setInt(2, next.getId());
                    del.executeUpdate();
                }
            }
            return next;
        }

        if (next == null) return null;

        try (PreparedStatement upd = con.prepareStatement(update)) {
            upd.setString(1, isbn);
            upd.setInt(2, next.getId());
            upd.executeUpdate();
        }
        return next;
    }

    @Override
    public void leaveWaitlist(String isbn, int userId) throws SQLException {
        String sql = "UPDATE waitlist SET status = 'CANCELLED' WHERE isbn = ? AND user_id = ?";
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbn);
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                remove(isbn, userId);
            }
        } catch (SQLException e) {
            remove(isbn, userId);
        }
    }

    @Override
    public void markFulfilled(String isbn, int userId) throws SQLException {
        String sql = "UPDATE waitlist SET status = 'FULFILLED' WHERE isbn = ? AND user_id = ? AND status = 'NOTIFIED'";
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbn);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    @Override
    public void remove(String isbn, int userId) throws SQLException {
        String sql = "DELETE FROM waitlist WHERE isbn = ? AND user_id = ?";
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbn);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    @Override
    public int size(String isbn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM waitlist WHERE isbn = ? AND (status IS NULL OR status = 'WAITING')";
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            String fallback = "SELECT COUNT(*) FROM waitlist WHERE isbn = ?";
            try (Connection con = DBConnectionManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(fallback)) {
                ps.setString(1, isbn);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        }
    }

    @Override
    public int availableCopies(String isbn) throws SQLException {
        String sql = "SELECT available_copies FROM books WHERE isbn = ?";
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public int notifiedCount(String isbn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM waitlist WHERE isbn = ? AND status = 'NOTIFIED'";
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            return 0;   // schema without status column
        }
    }

    @Override
    public List<User> findByIsbn(String isbn) throws SQLException {
        String sql = "SELECT u.* FROM waitlist w JOIN users u ON w.user_id = u.user_id "
                   + "WHERE w.isbn = ? AND (w.status IS NULL OR w.status = 'WAITING') "
                   + "ORDER BY w.request_date ASC, w.user_id ASC";
        List<User> list = new ArrayList<>();
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(buildUserFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            String fallback = "SELECT u.* FROM waitlist w JOIN users u ON w.user_id = u.user_id "
                    + "WHERE w.isbn = ? ORDER BY w.request_date ASC";
            try (Connection con = DBConnectionManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(fallback)) {
                ps.setString(1, isbn);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(buildUserFromResultSet(rs));
                    }
                }
            }
        }
        return list;
    }

    @Override
    public List<WaitlistEntry> getMyWaitlist(int userId) throws SQLException {
        String sql = "SELECT w.isbn, COALESCE(w.title, b.title, w.isbn) AS display_title, "
                   + "COALESCE(w.status, 'WAITING') AS entry_status, w.request_date "
                   + "FROM waitlist w "
                   + "LEFT JOIN books b ON w.isbn = b.isbn "
                   + "WHERE w.user_id = ? AND (w.status IS NULL OR w.status IN ('WAITING', 'NOTIFIED')) "
                   + "ORDER BY w.request_date DESC";
        List<WaitlistEntry> list = new ArrayList<>();
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String isbn = rs.getString("isbn");
                    String title = rs.getString("display_title");
                    String status = rs.getString("entry_status");
                    Date d = rs.getDate("request_date");
                    LocalDate reqDate = (d != null) ? d.toLocalDate() : LocalDate.now();

                    int pos = 0;
                    if ("WAITING".equalsIgnoreCase(status)) {
                        List<User> queued = findByIsbn(isbn);
                        for (int i = 0; i < queued.size(); i++) {
                            if (queued.get(i).getId() == userId) {
                                pos = i + 1;
                                break;
                            }
                        }
                        if (pos == 0) pos = 1;
                    }
                    list.add(new WaitlistEntry(isbn, title, status, pos, reqDate));
                }
            }
        } catch (SQLException e) {
            // Minimal schema fallback
            String fallback = "SELECT w.isbn, b.title, w.request_date FROM waitlist w "
                    + "LEFT JOIN books b ON w.isbn = b.isbn WHERE w.user_id = ?";
            try (Connection con = DBConnectionManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(fallback)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String isbn = rs.getString("isbn");
                        String title = rs.getString("title");
                        if (title == null) title = isbn;
                        Date d = rs.getDate("request_date");
                        LocalDate reqDate = (d != null) ? d.toLocalDate() : LocalDate.now();
                        list.add(new WaitlistEntry(isbn, title, "WAITING", 1, reqDate));
                    }
                }
            }
        }
        return list;
    }

    @Override
    public String getBookTitle(String isbn) throws SQLException {
        String sql = "SELECT title FROM books WHERE isbn = ?";
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("title");
            }
        }
        // check waitlist title
        String wsql = "SELECT title FROM waitlist WHERE isbn = ? AND title IS NOT NULL LIMIT 1";
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(wsql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("title");
            }
        } catch (SQLException ignored) {}
        return isbn;
    }

    @Override
    public void removeAllForIsbn(String isbn) throws SQLException {
        String sql = "DELETE FROM waitlist WHERE isbn = ?";
        try (Connection con = DBConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, isbn);
            ps.executeUpdate();
        }
    }

    private User buildUserFromResultSet(ResultSet rs) throws SQLException {
        String name = rs.getString("name");
        String email = rs.getString("email");
        int id = rs.getInt("user_id");
        String role = rs.getString("role");
        String contact = rs.getString("contact");
        if (contact == null || !contact.matches("\\d{7,15}")) {
            contact = "9876543210";
        }
        String hash = rs.getString("password_hash");
        switch (role) {
            case "STUDENT":
                return new model.Student(name, email, id, contact, hash);
            case "FACULTY":
                return new model.Faculty(name, email, id, contact, hash);
            case "ADMIN":
                return new model.Admin(name, email, id, contact, hash);
            default:
                throw new SQLException("Unknown role: " + role);
        }
    }
}
