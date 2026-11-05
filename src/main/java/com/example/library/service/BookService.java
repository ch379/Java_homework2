package com.example.library.service;

import com.example.library.dao.LibraryDao;
import com.example.library.entity.*;
import com.example.library.orm.JDBCTool;
import com.example.library.util.Database;
import com.google.gson.JsonObject;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.example.library.service.Validation.*;

public final class BookService {
    public static <T extends Base> T initialize(T entity) {
        entity.setId(UUID.randomUUID().toString());entity.setCreateTime(LocalDateTime.now());entity.setUpdateTime(entity.getCreateTime());return entity;
    }
    public List<Map<String,Object>> search(String keyword, boolean availableOnly) throws Exception {
        return Database.read(c -> LibraryDao.rows(c,"SELECT b.id,b.title,b.author,b.type,b.publisher,b.days,b.price,b.stock,b.status,b.introduction,b.createTime,"
                +"(SELECT COUNT(*) FROM bookcopy c WHERE c.bookId=b.id AND "+LibraryDao.ACTIVE_COPY+") actualStock,"
                +"(SELECT COUNT(*) FROM bookcopy c WHERE c.bookId=b.id AND c.isBorrowed=0 AND "+LibraryDao.ACTIVE_COPY+") available "
                +"FROM book b WHERE (b.title LIKE ? OR b.author LIKE ? OR b.type LIKE ? OR b.publisher LIKE ?) "
                +(availableOnly?"AND b.status='正常' AND EXISTS (SELECT 1 FROM bookcopy c WHERE c.bookId=b.id AND c.isBorrowed=0 AND "+LibraryDao.ACTIVE_COPY+") ":"")
                +"ORDER BY b.createTime DESC,b.id DESC","%"+keyword+"%","%"+keyword+"%","%"+keyword+"%","%"+keyword+"%"));
    }
    public List<Map<String,Object>> copies(String id) throws Exception {
        return Database.read(c -> LibraryDao.rows(c,"SELECT c.id,c.createTime,c.isBorrowed,"
                +"(SELECT COUNT(*) FROM loan l WHERE l.bookCopyId=c.id) historyCount FROM bookcopy c WHERE c.bookId=? AND "+LibraryDao.ACTIVE_COPY+" ORDER BY c.createTime DESC,c.id DESC",id));
    }
    public Book fields(JsonObject j) {
        Book b=new Book();b.setTitle(required(j,"title","书名",30));b.setAuthor(required(j,"author","作者",30));
        b.setType(required(j,"type","类型",10));b.setPublisher(required(j,"publisher","出版社",20));
        b.setDays(integer(j,"days",1,365));b.setPrice(price(j));return b;
    }
    public Book create(Connection c, JsonObject j) throws Exception {
        Book b=initialize(fields(j));b.setStock(integer(j,"stock",0,1000));b.setStatus("正常");
        b.setIntroduction(text(j,"introduction"));require(b.getIntroduction().length()<=512,"简介最多512字");
        JDBCTool.save(b,c);
        for (int i=0;i<b.getStock();i++) {
            BookCopy copy=initialize(new BookCopy());copy.setBook(b);copy.setBorrowed(false);JDBCTool.save(copy,c);
        }
        return b;
    }
    public String add(JsonObject j) throws Exception { return Database.transaction(c -> create(c,j).getId()); }
    public void edit(JsonObject j) throws Exception {
        Book changes=fields(j);String id=required(j,"id","图书ID",36);
        Database.transaction(c -> {
            lockBook(c,id);LibraryDao.update(c,"UPDATE book SET title=?,author=?,type=?,publisher=?,days=?,price=?,updateTime=? WHERE id=?",
                    changes.getTitle(),changes.getAuthor(),changes.getType(),changes.getPublisher(),changes.getDays(),changes.getPrice(),LocalDateTime.now(),id);
            return null;
        });
    }
    public static Map<String,Object> lockBook(Connection c,String id) throws Exception {
        var row=LibraryDao.one(c,"SELECT id,status,stock,days FROM book WHERE id=? FOR UPDATE",id);
        require(row!=null,"图书不存在");return row;
    }
    public void discard(String id) throws Exception {
        Database.transaction(c -> {
            lockBook(c,id);
            require(LibraryDao.count(c,"SELECT COUNT(*) FROM bookcopy c WHERE c.bookId=? AND c.isBorrowed=1 AND "+LibraryDao.ACTIVE_COPY,id)==0,"仍有副本外借，全部归还后才能废弃");
            LibraryDao.update(c,"UPDATE book SET status='已废弃',updateTime=? WHERE id=?",LocalDateTime.now(),id);return null;
        });
    }
    public void addCopy(String id) throws Exception {
        Database.transaction(c -> {
            var book=lockBook(c,id);require("正常".equals(book.get("status")),"已废弃图书不能新增副本");
            Book ref=new Book();ref.setId(id);BookCopy copy=initialize(new BookCopy());copy.setBook(ref);copy.setBorrowed(false);JDBCTool.save(copy,c);
            LibraryDao.update(c,"UPDATE book SET stock=stock+1,updateTime=? WHERE id=?",LocalDateTime.now(),id);return null;
        });
    }
    public void deleteCopy(String id) throws Exception {
        Database.transaction(c -> {
            var ref=LibraryDao.one(c,"SELECT bookId FROM bookcopy WHERE id=?",id);require(ref!=null,"副本不存在");
            String bookId=ref.get("bookId").toString();lockBook(c,bookId);
            var copy=LibraryDao.one(c,"SELECT c.id,c.isBorrowed FROM bookcopy c WHERE c.id=? AND "+LibraryDao.ACTIVE_COPY+" FOR UPDATE",id);
            require(copy!=null,"副本已删除");require(!isTrue(copy.get("isBorrowed")),"借出中的副本不能删除");
            // Logical deletion in the existing log table preserves Loan foreign keys and history.
            LibraryDao.update(c,"INSERT INTO log (id,createTime,updateTime,type,message) VALUES (?,?,?,'副本删除',?)",UUID.randomUUID().toString(),LocalDateTime.now(),LocalDateTime.now(),id);
            LibraryDao.update(c,"UPDATE book SET stock=GREATEST(stock-1,0),updateTime=? WHERE id=?",LocalDateTime.now(),bookId);return null;
        });
    }
    public static boolean isTrue(Object value) { return Boolean.TRUE.equals(value)||(value instanceof Number n&&n.intValue()!=0); }
}
