package com.example.library.entity;

import com.example.library.orm.*;
import java.time.*;
import java.util.List;

@Table("`bookrecommend`")
public class BookRecommend extends Base {
    @Column("`userId`")
    private User user;
    public User getUser() { return user; }
    public void setUser(User value) { this.user = value; }

    @Column("`bookName`")
    private String bookName;
    public String getBookName() { return bookName; }
    public void setBookName(String value) { this.bookName = value; }

    @Column("`author`")
    private String author;
    public String getAuthor() { return author; }
    public void setAuthor(String value) { this.author = value; }

    @Column("`publisher`")
    private String publisher;
    public String getPublisher() { return publisher; }
    public void setPublisher(String value) { this.publisher = value; }

    @Column("`price`")
    private Double price;
    public Double getPrice() { return price; }
    public void setPrice(Double value) { this.price = value; }

    @Column("`status`")
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }

    @Column("`reason`")
    private String reason;
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }

}
