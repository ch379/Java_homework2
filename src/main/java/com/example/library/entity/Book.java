package com.example.library.entity;

import com.example.library.orm.*;
import java.time.*;
import java.util.List;

@Table("`book`")
public class Book extends Base {
    @Column("`title`")
    private String title;
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }

    @Column("`author`")
    private String author;
    public String getAuthor() { return author; }
    public void setAuthor(String value) { this.author = value; }

    @Column("`type`")
    private String type;
    public String getType() { return type; }
    public void setType(String value) { this.type = value; }

    @Column("`publisher`")
    private String publisher;
    public String getPublisher() { return publisher; }
    public void setPublisher(String value) { this.publisher = value; }

    @Column("`days`")
    private Integer days;
    public Integer getDays() { return days; }
    public void setDays(Integer value) { this.days = value; }

    @Column("`price`")
    private Double price;
    public Double getPrice() { return price; }
    public void setPrice(Double value) { this.price = value; }

    @Column("`stock`")
    private Integer stock;
    public Integer getStock() { return stock; }
    public void setStock(Integer value) { this.stock = value; }

    @Column("`status`")
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }

    @Column("`introduction`")
    private String introduction;
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String value) { this.introduction = value; }

    private List<BookCopy> bookCopies;
    public List<BookCopy> getBookCopies() { return bookCopies; }
    public void setBookCopies(List<BookCopy> value) { this.bookCopies = value; }

}
