package service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

import dao.BookDAO;
import dao.WaitlistDAO;
import dao.TransactionDAO;
import dao.FineDAO;
import dao.impl.BookDAOImpl;
import dao.impl.WaitlistDAOImpl;
import dao.impl.TransactionDAOImpl;
import dao.impl.FineDAOImpl;
import database.DBConnectionManager;
import model.*;

public class Library implements Borrowable, Searchable, Reportable {

    // DAOs
    private final BookDAO bookDAO = new BookDAOImpl();
    private final WaitlistDAO waitlistDAO = new WaitlistDAOImpl();
    private final TransactionDAO txnDAO = new TransactionDAOImpl();
    private final FineDAO fineDAO = new FineDAOImpl();

    private final List<LibraryItem> inventory = new ArrayList<>();
    private final Map<String, IssueTransaction> activeLoans = new HashMap<>();
    private final Map<Integer, Integer> userLoanCounts = new HashMap<>();
    private final FinePolicy standardPolicy = new StandardFinePolicy();
    private final FinePolicy facultyPolicy = new FacultyFinePolicy();
    private final WaitlistManager waitlistManager = new WaitlistManager();

    public Library() {
        try {
            this.inventory.addAll(bookDAO.findAll());
        } catch (SQLException e) {
            System.err.println("[LIBRARY INIT] Could not load books: " + e.getMessage());
        }
        try {
            for (IssueTransaction txn : txnDAO.findAllActive()) {
                if (txn.getUser() != null && txn.getBook() != null) {
                    activeLoans.put(getLoanKey(txn.getUser(), txn.getBook()), txn);
                    userLoanCounts.put(txn.getUser().getId(),
                            userLoanCounts.getOrDefault(txn.getUser().getId(), 0) + 1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[LIBRARY INIT] Could not load active loans: " + e.getMessage());
        }
    }


    public void addItem(LibraryItem item) {
        try {
            bookDAO.insert(item);
        } catch (SQLException e) {
            System.err.println("[LIBRARY] Failed to persist new book: " + e.getMessage());
            return; // don't leave a ghost book in memory
        }
        inventory.add(item);
        System.out.println("[CATALOG] Added: " + item.getTitle() + " (ISBN: " + item.getIsbn() + ")");
        waitlistManager.notifyBookArrived(item.getIsbn());
    }

    public WaitlistManager getWaitlistManager() {
        return waitlistManager;
    }

    public void removeItem(String isbn) throws SQLException {
        LibraryItem item = findInInventory(isbn);
        if (item != null && item.getAvailableCopies() < item.getTotalCopies()) {
            throw new IllegalStateException(
                "Cannot remove '" + item.getTitle() + "': it still has copies on loan.");        }
        waitlistDAO.removeAllForIsbn(isbn);   // waitlist rows reference the book, so first
        bookDAO.deleteByIsbn(isbn);
        inventory.removeIf(b -> b.getIsbn().equals(isbn));
    }

    private LibraryItem findInInventory(String isbn) {
        for (LibraryItem b : inventory) {
            if (b.getIsbn().equals(isbn)) return b;
        }
        return null;
    }

    private String getLoanKey(User user, LibraryItem item) {
        return user.getId() + "_" + item.getIsbn();
    }

    // ---------- issue ----------

    @Override
    public void issue(User user, LibraryItem item) {
        doIssue(user, item, null);
    }

    @Override
    public void issue(User user, LibraryItem item, int customDays) {
        if (customDays <= 0) {
            System.out.println("[ISSUE ERROR] Custom loan period must be at least 1 day.");
            return;
        }
        System.out.println("[CUSTOM LOAN] Issuing with custom period: " + customDays + " days.");
        doIssue(user, item, customDays);
    }

    private void doIssue(User user, LibraryItem requested, Integer customDays) {
        // always work on the inventory object so memory and DB stay in sync
        LibraryItem item = findInInventory(requested.getIsbn());
        if (item == null) {
            System.out.println("[ISSUE ERROR] Item (ISBN: " + requested.getIsbn() + ") is not in the catalog.");
            return;
        }

        if (!item.isIssuable()) {
            System.out.println("[ISSUE ERROR] '" + item.getTitle() + "' is a reference item and cannot be borrowed!");
            return;
        }

        String loanKey = getLoanKey(user, item);
        if (activeLoans.containsKey(loanKey)) {
            System.out.println("[ISSUE ERROR] " + user.getName() + " already has '" + item.getTitle() + "'.");
            return;
        }

        int currentLoans = userLoanCounts.getOrDefault(user.getId(), 0);
        if (currentLoans >= user.getMaxBookAllowed()) {
            System.out.println("[ISSUE ERROR] " + user.getName() + " reached max borrowing limit ("
                    + user.getMaxBookAllowed() + " books).");
            return;
        }

        if (!item.hasAvailableCopy()) {
            System.out.println("[OUT OF STOCK] No copies of '" + item.getTitle() + "' available.");
            waitlistManager.addToWaitlist(item.getIsbn(), item.getTitle(), user);
            return;
        }

        IssueTransaction txn = new IssueTransaction(user, item, LocalDate.now());
        if (customDays == null) {
            txn.process();
        } else {
            txn.process(customDays);
        }

        try {
            persistIssue(txn, item);
        } catch (SQLException e) {
            item.checkInCopies();   // undo the in-memory decrement
            System.out.println("[ISSUE ERROR] Could not save the loan: " + e.getMessage());
            return;
        }

        activeLoans.put(loanKey, txn);
        userLoanCounts.put(user.getId(), currentLoans + 1);
        waitlistManager.markFulfilled(item.getIsbn(), user.getId());   // NOTIFIED -> FULFILLED

        System.out.println("[ISSUE SUCCESS] Issued '" + item.getTitle() + "' to " + user.getName()
                + " | Due Date: " + txn.getDueDate());
    }

    private void persistIssue(IssueTransaction txn, LibraryItem item) throws SQLException {
        try (Connection con = DBConnectionManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                txnDAO.insertIssue(txn, con);   // also sets the DB id on txn
                bookDAO.update(item, con);      // writes the decremented available_copies
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }


    @Override
    public void returnItem(User user, LibraryItem requested) {
        LibraryItem item = findInInventory(requested.getIsbn());
        if (item == null) {
            System.out.println("[RETURN ERROR] Item (ISBN: " + requested.getIsbn() + ") is not in the catalog.");
            return;
        }

        String loanKey = getLoanKey(user, item);
        IssueTransaction issueTxn = activeLoans.get(loanKey);
        if (issueTxn == null) {
            try {
                for (IssueTransaction t : txnDAO.findActiveByUser(user.getId())) {
                    if (t.getBook() != null && t.getBook().getIsbn().equals(item.getIsbn())) {
                        issueTxn = t;
                        activeLoans.put(loanKey, issueTxn);
                        break;
                    }
                }
            } catch (SQLException ignored) {}
        }
        if (issueTxn == null) {
            System.out.println("[RETURN ERROR] No active loan found for " + user.getName()
                    + " and '" + item.getTitle() + "'.");
            return;
        }

        ReturnTransaction returnTxn = new ReturnTransaction(issueTxn, LocalDate.now());
        try {
            item.checkInCopies();
            if (issueTxn.getBook() != item) {
                issueTxn.getBook().checkInCopies();
            }
        } catch (IllegalStateException e) {
            System.out.println("[RETURN ERROR] " + e.getMessage());
            return;
        }

        // build the fine (only if the amount is above 0, e.g. faculty grace period)
        int overdueDays = returnTxn.getOverdueDays();
        double fineAmount = 0;
        FinePolicy policy = null;
        Fine fineRecord = null;
        if (overdueDays > 0) {
            policy = user.getRole().equalsIgnoreCase("FACULTY") ? facultyPolicy : standardPolicy;
            fineAmount = policy.calculateFine(overdueDays);
            if (fineAmount > 0) {
                fineRecord = new Fine(issueTxn.getTxnId(), BigDecimal.valueOf(fineAmount), false);
            }
        }

        try {
            persistReturn(returnTxn, item, fineRecord);
        } catch (SQLException e) {
            item.checkOutCopies();  // undo the in-memory increment
            System.out.println("[RETURN ERROR] Could not save the return: " + e.getMessage());
            return;
        }

        activeLoans.remove(loanKey);
        userLoanCounts.put(user.getId(), Math.max(0, userLoanCounts.getOrDefault(user.getId(), 1) - 1));

        System.out.println("[RETURN SUCCESS] '" + item.getTitle() + "' returned by " + user.getName());

        if (fineRecord != null) {
            System.out.println("[FINE APPLIED] " + overdueDays + " days overdue. Total Fine: ₹" + fineAmount
                    + " (Applied Policy: " + policy.getClass().getSimpleName() + ")");
        } else {
            System.out.println("[NO FINE] Book returned on time, early, or within the grace period.");
        }

        waitlistManager.notifyNextuser(item.getIsbn());   // after the commit
    }

    private void persistReturn(ReturnTransaction returnTxn, LibraryItem item, Fine fine) throws SQLException {
        try (Connection con = DBConnectionManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                txnDAO.insertReturn(returnTxn, con);
                bookDAO.update(item, con);      // incremented available_copies
                if (fine != null) {
                    fineDAO.insert(fine, con);
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }


    @Override
    public List<LibraryItem> search(String keyword) {
        List<LibraryItem> results = new ArrayList<>();
        String query = keyword.toLowerCase();

        for (LibraryItem item : inventory) {
            if (item.getTitle().toLowerCase().contains(query)
                    || item.getAuthor().toLowerCase().contains(query)
                    || item.getIsbn().toLowerCase().contains(query)) {
                results.add(item);
            }
        }
        return results;
    }

    @Override
    public String getShelfLocation(String isbn) {
        LibraryItem item = findInInventory(isbn);
        return (item != null) ? item.getShelfLocation() : "Item not found";
    }

    @Override
    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Library Summary & Audit Report ===\n");
        sb.append("Total Catalog Items: ").append(inventory.size()).append("\n");
        sb.append("Active Loans: ").append(activeLoans.size()).append("\n\n");

        sb.append("--- Active Loans Details ---\n");
        if (activeLoans.isEmpty()) {
            sb.append("No active loans at this time.\n");
        } else {
            LocalDate today = LocalDate.now();
            int idx = 1;
            for (IssueTransaction txn : activeLoans.values()) {
                String title = (txn.getBook() != null) ? txn.getBook().getTitle() : "Unknown";
                String isbn = (txn.getBook() != null) ? txn.getBook().getIsbn() : "N/A";
                String uname = (txn.getUser() != null) ? txn.getUser().getName() : "Unknown";
                String role = (txn.getUser() != null) ? txn.getUser().getRole() : "";
                LocalDate due = txn.getDueDate();
                long overdue = (due != null) ? java.time.temporal.ChronoUnit.DAYS.between(due, today) : 0;
                String status = (overdue > 0) ? ("OVERDUE (" + overdue + " days)") : "ACTIVE";

                sb.append(String.format("[%d] %s (ISBN: %s)\n", idx++, title, isbn));
                sb.append(String.format("    Borrower: %s (%s, ID: %d)\n",
                        uname, role, (txn.getUser() != null ? txn.getUser().getId() : 0)));
                sb.append(String.format("    Issue Date: %s | Due Date: %s | Status: %s\n\n",
                        txn.getIssueDate(), due, status));
            }
        }
        return sb.toString();
    }
}
