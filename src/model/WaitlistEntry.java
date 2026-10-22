package model;

import java.time.LocalDate;

public class WaitlistEntry {
    private final String isbn;
    private final String title;
    private final String status;
    private final int position;
    private final LocalDate requestDate;

    public WaitlistEntry(String isbn, String title, String status, int position, LocalDate requestDate) {
        this.isbn = isbn;
        this.title = title;
        this.status = status;
        this.position = position;
        this.requestDate = requestDate;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public int getPosition() {
        return position;
    }

    public LocalDate getRequestDate() {
        return requestDate;
    }
}
