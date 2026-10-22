package dao;

import java.sql.SQLException;
import java.util.List;
import model.User;
import model.WaitlistEntry;
import java.sql.Connection;

public interface WaitlistDAO {
    void add(String isbn, User user) throws SQLException;
    void add(String isbn, String title, User user) throws SQLException;
    void add(String isbn, User user, Connection con) throws SQLException;

    User popNext(String isbn) throws SQLException;
    User popNext(String isbn, Connection con) throws SQLException;

    void remove(String isbn, int userId) throws SQLException;
    void leaveWaitlist(String isbn, int userId) throws SQLException;
    void markFulfilled(String isbn, int userId) throws SQLException;

    int size(String isbn) throws SQLException;
    int availableCopies(String isbn) throws SQLException;
    int notifiedCount(String isbn) throws SQLException;

    List<User> findByIsbn(String isbn) throws SQLException;
    List<WaitlistEntry> getMyWaitlist(int userId) throws SQLException;
    String getBookTitle(String isbn) throws SQLException;

    void removeAllForIsbn(String isbn) throws SQLException;
}
