package com.example.library.entity;

import com.example.library.orm.*;
import java.time.*;
import java.util.List;

@Table("`loan`")
public class Loan extends Base {
    @Column("`bookCopyId`")
    private BookCopy bookCopy;
    public BookCopy getBookCopy() { return bookCopy; }
    public void setBookCopy(BookCopy value) { this.bookCopy = value; }

    @Column("`userId`")
    private User user;
    public User getUser() { return user; }
    public void setUser(User value) { this.user = value; }

    @Column("`borrowDate`")
    private LocalDateTime borrowDate;
    public LocalDateTime getBorrowDate() { return borrowDate; }
    public void setBorrowDate(LocalDateTime value) { this.borrowDate = value; }

    @Column("`returnDate`")
    private LocalDateTime returnDate;
    public LocalDateTime getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDateTime value) { this.returnDate = value; }

    @Column("`dueDate`")
    private LocalDateTime dueDate;
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime value) { this.dueDate = value; }

    @Column("`status`")
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }

}
