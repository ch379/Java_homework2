package com.example.library.service;

import com.example.library.dao.LibraryDao;
import com.example.library.entity.*;
import com.example.library.orm.JDBCTool;
import com.example.library.util.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.*;
import static com.example.library.service.Validation.*;

public final class UserService {
    public User login(String name, String password) throws Exception {
        return Database.transaction(c -> {
            var row = LibraryDao.one(c,"SELECT id,role,password FROM user WHERE name=?",name);
            require(row!=null&&Passwords.matches(password,(String)row.get("password")),"用户名或密码不正确");
            User user = LibraryDao.user(c,row.get("id").toString(),row.get("role").toString());
            user.setLoginTime(LocalDateTime.now());
            LibraryDao.update(c,"UPDATE user SET loginTime=?,updateTime=? WHERE id=?",user.getLoginTime(),LocalDateTime.now(),user.getId());
            return user;
        });
    }
    public void register(JsonObject j) throws Exception {
        String name = required(j,"name","姓名",50);
        // Passwords are not trimmed: login and registration preserve the same characters.
        String password = j.has("password")&&j.get("password").isJsonPrimitive()?j.get("password").getAsString():"";
        require(password.length()>=6&&password.length()<=64,"密码长度须为6～64位");
        String phone = required(j,"phone","手机号",11);
        require(phone.matches("1[3-9]\\d{9}"),"请输入有效的11位手机号");
        String role = LibraryDao.roleName(required(j,"role","角色",20));
        User user = LibraryDao.userClass(role).getDeclaredConstructor().newInstance();
        user.setId(UUID.randomUUID().toString());user.setCreateTime(LocalDateTime.now());user.setUpdateTime(LocalDateTime.now());
        user.setName(name);user.setPassword(Passwords.hash(password));user.setPhone(phone);user.setRole(role);
        user.setMajor(required(j,"major","专业",100));
        if (user instanceof Student u) { u.setGrade(required(j,"grade","年级",30));u.setCollege(required(j,"college","学院",100)); }
        if (user instanceof Teacher u) { u.setCollege(required(j,"college","学院",100));u.setTitle(required(j,"title","职称",50)); }
        if (user instanceof Operator u) { u.setArea(required(j,"area","负责区域",100));u.setDuty(required(j,"duty","职务",50)); }
        if (user instanceof Admin u) { u.setPermissionLevel(integer(j,"permissionLevel",1,9));u.setEntryTime(date(required(j,"entryTime","入职日期",10),"入职日期")); }
        Database.transaction(c -> {
            // Serialize registration without changing the teacher's original schema.
            var lock = LibraryDao.one(c,"SELECT GET_LOCK('LibraryWeb2026.register',10) locked");
            require(lock!=null && ((Number)lock.get("locked")).intValue()==1,"注册繁忙，请稍后重试");
            try {
                require(LibraryDao.count(c,"SELECT COUNT(*) FROM user WHERE name=?",name)==0,"同名账号已存在");
                require(LibraryDao.count(c,"SELECT COUNT(*) FROM user WHERE phone=?",phone)==0,"手机号已注册");
                JDBCTool.save(user,c);
                // Commit before releasing the connection-scoped registration lock.
                c.commit();
            } finally { LibraryDao.rows(c,"SELECT RELEASE_LOCK('LibraryWeb2026.register')"); }
            return null;
        });
    }
    public List<Map<String,Object>> search(String keyword) throws Exception {
        return Database.read(c -> LibraryDao.rows(c,"SELECT id,name,role,phone,major,createTime FROM user WHERE name LIKE ? OR role LIKE ? OR phone LIKE ? OR major LIKE ? ORDER BY createTime DESC,id DESC", "%"+keyword+"%","%"+keyword+"%","%"+keyword+"%","%"+keyword+"%"));
    }
    public Map<String,Object> detail(String id) throws Exception {
        return Database.read(c -> {
            var row=LibraryDao.one(c,"SELECT id,name,role,phone,major,createTime,updateTime,loginTime,permissionLevel,entryTime,area,duty,grade,college,title FROM user WHERE id=?",id);
            require(row!=null,"用户不存在");return row;
        });
    }
}
