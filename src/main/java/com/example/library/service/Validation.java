package com.example.library.service;

import com.google.gson.JsonObject;
import java.time.*;

public final class Validation {
    public static String text(JsonObject j, String key) {
        if (!j.has(key) || j.get(key).isJsonNull()) return "";
        if (!j.get(key).isJsonPrimitive() || !j.get(key).getAsJsonPrimitive().isString()) throw new IllegalArgumentException("字段格式不正确：" + key);
        return j.get(key).getAsString().strip();
    }
    public static String required(JsonObject j, String key, String label, int max) {
        String v = text(j,key); require(!v.isBlank(),label+"不能为空");require(v.length()<=max,label+"最多"+max+"个字符");return v;
    }
    public static int integer(JsonObject j, String key, int min, int max) {
        try { int n = Integer.parseInt(j.get(key).getAsString()); require(n>=min&&n<=max,key+"范围应为"+min+"～"+max);return n; }
        catch (NumberFormatException | NullPointerException e) { throw new IllegalArgumentException(key+"必须是整数"); }
    }
    public static double price(JsonObject j) {
        try { double n=j.get("price").getAsDouble();require(Double.isFinite(n)&&n>=0&&n<=1000000,"价格须为0～1000000之间的数字");return n; }
        catch (NumberFormatException | NullPointerException e) { throw new IllegalArgumentException("价格格式不正确"); }
    }
    public static LocalDate date(String value, String label) {
        try { return LocalDate.parse(value); } catch (Exception e) { throw new IllegalArgumentException(label+"格式应为 yyyy-MM-dd"); }
    }
    public static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private Validation() {}
}
