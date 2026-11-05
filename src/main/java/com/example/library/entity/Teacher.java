package com.example.library.entity;

import com.example.library.orm.*;
import java.time.*;
import java.util.List;

@Table("`user`")
public class Teacher extends User {
    @Column("`college`")
    private String college;
    public String getCollege() { return college; }
    public void setCollege(String value) { this.college = value; }

    @Column("`title`")
    private String title;
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }

    @Override public String[] hasPermission() { return new String[] {"borrow", "return", "myLoans", "recommend"}; }
}
