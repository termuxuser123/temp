package service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import dao.WaitlistDAO;
import dao.impl.WaitlistDAOImpl;
import model.User;
import model.WaitlistEntry;

public class WaitlistManager {
    private final WaitlistDAO waitlistDAO = new WaitlistDAOImpl();

    // add user to the waitlist for a book (default title)
    public boolean addToWaitlist(String isbn, User user) {
        return addToWaitlist(isbn, null, user);
    }

    // add user to the waitlist with title, without duplicate-check loop
    public boolean addToWaitlist(String isbn, String title, User user) {
        try {
            waitlistDAO.add(isbn, title, user);
            System.out.println("[WAITLIST] " + user.getName() + " added to waitlist for ISBN: "
                    + isbn + " (Position: " + waitlistDAO.size(isbn) + ")");
            return true;
        } catch (SQLException e) {
            System.out.println("[WAITLIST ERROR] Could not update waitlist: " + e.getMessage());
            return false;
        }
    }

    // notify waiting users, one per free copy (copies already reserved for NOTIFIED users don't count)
    public List<User> notifyNextuser(String isbn) {
        List<User> notified = new ArrayList<>();
        try {
            int free = waitlistDAO.availableCopies(isbn) - waitlistDAO.notifiedCount(isbn);
            if (free <= 0) return notified;
            String title = waitlistDAO.getBookTitle(isbn);
            while (free-- > 0) {
                User nextUser = waitlistDAO.popNext(isbn);
                if (nextUser == null) break;
                System.out.println("[WAITLIST NOTIFICATION] A copy of book (ISBN: " + isbn
                        + ") is now reserved for: " + nextUser.getName()
                        + " (" + nextUser.getMail() + ")");
                NotificationManager.send(nextUser.getId(), "A copy of " + title + " is now available. Please issue it soon.");
                notified.add(nextUser);
            }
        } catch (SQLException e) {
            System.out.println("[WAITLIST ERROR] Could not read waitlist: " + e.getMessage());
        }
        return notified;
    }

    // called when an admin adds a book or copy
    public List<User> notifyBookArrived(String isbn) {
        return notifyNextuser(isbn);
    }

    // called when a user issues the book: a NOTIFIED entry becomes FULFILLED
    public void markFulfilled(String isbn, int userId) {
        try {
            waitlistDAO.markFulfilled(isbn, userId);
        } catch (SQLException e) {
            System.out.println("[WAITLIST ERROR] Could not mark entry fulfilled: " + e.getMessage());
        }
    }

    // cancel / leave waitlist
    public void leaveWaitlist(String isbn, int userId) {
        try {
            waitlistDAO.leaveWaitlist(isbn, userId);
            System.out.println("[WAITLIST] User " + userId + " left waitlist for ISBN: " + isbn);
            notifyNextuser(isbn);   // a reserved copy may have just been freed up
        } catch (SQLException e) {
            System.out.println("[WAITLIST ERROR] Could not leave waitlist: " + e.getMessage());
        }
    }

    // get waitlist for user
    public List<WaitlistEntry> getMyWaitlist(int userId) {
        try {
            return waitlistDAO.getMyWaitlist(userId);
        } catch (SQLException e) {
            System.out.println("[WAITLIST ERROR] Could not get user waitlist: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public int getWaitlistSize(String isbn) {
        try {
            return waitlistDAO.size(isbn);
        } catch (SQLException e) {
            System.out.println("[WAITLIST ERROR] " + e.getMessage());
            return 0;
        }
    }
}