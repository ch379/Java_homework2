package com.example.library.entity;

import com.example.library.orm.*;
import java.time.*;
import java.util.List;

public class Base {
    @Id
    @Column("`id`")
    private String id;
    public String getId() { return id; }
    public void setId(String value) { this.id = value; }

    @Column("`createTime`")
    private LocalDateTime createTime;
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { this.createTime = value; }

    @Column("`updateTime`")
    private LocalDateTime updateTime;
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime value) { this.updateTime = value; }

}
