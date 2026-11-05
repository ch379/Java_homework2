package com.example.library.entity;

import com.example.library.orm.*;
import java.time.*;
import java.util.List;

@Table("`user`")
public abstract class User extends Base {
    @Column("`name`")
    private String name;
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }

    @Column("`password`")
    private String password;
    public String getPassword() { return password; }
    public void setPassword(String value) { this.password = value; }

    @Column("`phone`")
    private String phone;
    public String getPhone() { return phone; }
    public void setPhone(String value) { this.phone = value; }

    @Column("`role`")
    private String role;
    public String getRole() { return role; }
    public void setRole(String value) { this.role = value; }

    @Column("`major`")
    private String major;
    public String getMajor() { return major; }
    public void setMajor(String value) { this.major = value; }

    @Column("`loginTime`")
    private LocalDateTime loginTime;
    public LocalDateTime getLoginTime() { return loginTime; }
    public void setLoginTime(LocalDateTime value) { this.loginTime = value; }

    public abstract String[] hasPermission();
}
