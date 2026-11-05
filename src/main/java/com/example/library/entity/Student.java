package com.example.library.entity;

import com.example.library.orm.*;
import java.time.*;
import java.util.List;

@Table("`user`")
public class Student extends User {
    @Column("`grade`")
    private String grade;
    public String getGrade() { return grade; }
    public void setGrade(String value) { this.grade = value; }

    @Column("`college`")
    private String college;
    public String getCollege() { return college; }
    public void setCollege(String value) { this.college = value; }

    @Override public String[] hasPermission() { return new String[] {"borrow", "return", "myLoans"}; }
}
