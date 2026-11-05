package com.example.library.entity;

import com.example.library.orm.*;
import java.time.*;
import java.util.List;

@Table("`user`")
public class Operator extends User {
    @Column("`area`")
    private String area;
    public String getArea() { return area; }
    public void setArea(String value) { this.area = value; }

    @Column("`duty`")
    private String duty;
    public String getDuty() { return duty; }
    public void setDuty(String value) { this.duty = value; }

    @Override public String[] hasPermission() { return new String[] {"borrow", "return", "manageBooks", "myLoans"}; }
}
