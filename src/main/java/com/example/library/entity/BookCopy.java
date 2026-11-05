package com.example.library.entity;

import com.example.library.orm.*;
import java.time.*;
import java.util.List;

@Table("`bookcopy`")
public class BookCopy extends Base {
    @Column("`bookId`")
    private Book book;
    public Book getBook() { return book; }
    public void setBook(Book value) { this.book = value; }

    @Column("`isBorrowed`")
    private boolean isBorrowed;
    public boolean isBorrowed() { return isBorrowed; }
    public void setBorrowed(boolean value) { this.isBorrowed = value; }

}
