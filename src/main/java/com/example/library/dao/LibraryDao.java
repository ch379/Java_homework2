package com.example.library.dao;

import com.example.library.entity.*;
import com.example.library.orm.JDBCTool;
import java.sql.*;
import java.util.*;

/** All JDBC access goes through the ORM tool reused from homework 1. */
public final class LibraryDao {
    public static final String ACTIVE_COPY = "NOT EXISTS (SELECT 1 FROM log deleted WHERE deleted.type='副本删除' AND deleted.message=c.id)";
    public static List<Map<String,Object>> rows(Connection c, String sql, Object... args) throws SQLException {
        return JDBCTool.queryRows(sql, Arrays.asList(args), c);
    }
    public static Map<String,Object> one(Connection c, String sql, Object... args) throws SQLException {
        var result = rows(c, sql, args); return result.isEmpty() ? null : result.getFirst();
    }
    public static long count(Connection c, String sql, Object... args) throws SQLException {
        return ((Number) one(c, sql, args).values().iterator().next()).longValue();
    }
    public static int update(Connection c, String sql, Object... args) throws SQLException {
        return JDBCTool.executeUpdate(sql, Arrays.asList(args), c);
    }
    public static Class<? extends User> userClass(String role) {
        return switch (role) {
            case "管理员", "Admin", "admin" -> Admin.class;
            case "操作员", "Operator" -> Operator.class;
            case "学生", "Student" -> Student.class;
            case "教师", "Teacher" -> Teacher.class;
            default -> throw new IllegalArgumentException("未知角色");
        };
    }
    public static String roleName(String role) {
        Class<? extends User> type = userClass(role);
        if (type == Admin.class) return "管理员";
        if (type == Operator.class) return "操作员";
        if (type == Teacher.class) return "教师";
        return "学生";
    }
    public static User user(Connection c, String id, String role) throws SQLException {
        return JDBCTool.getOneById(id, userClass(role), c);
    }
    public static Map<String,Object> publicUser(User u) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("id",u.getId());result.put("name",u.getName());result.put("role",u.getRole());
        result.put("phone",u.getPhone());result.put("major",u.getMajor());result.put("loginTime",u.getLoginTime());
        result.put("permissions",u.hasPermission());return result;
    }
    private LibraryDao() {}
}
