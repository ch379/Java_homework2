package com.example.library.entity;

import com.example.library.orm.*;
import java.time.*;
import java.util.List;

@Table("`user`")
public class Admin extends User {
    @Column("`permissionLevel`")
    private Integer permissionLevel;
    public Integer getPermissionLevel() { return permissionLevel; }
    public void setPermissionLevel(Integer value) { this.permissionLevel = value; }

    @Column("`entryTime`")
    private LocalDate entryTime;
    public LocalDate getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDate value) { this.entryTime = value; }

    @Override public String[] hasPermission() { return new String[] {"borrow", "return", "users", "recommendations", "loans", "statistics"}; }
}
