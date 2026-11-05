package com.example.library.service;

import com.example.library.dao.LibraryDao;
import com.example.library.entity.*;
import com.example.library.orm.JDBCTool;
import com.example.library.util.Database;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.*;
import static com.example.library.service.Validation.*;

public final class RecommendationService {
    public Map<String,Object> recommend(JsonObject j,User user) throws Exception {
        String name=required(j,"bookName","书名",20),author=required(j,"author","作者",20),publisher=required(j,"publisher","出版社",25);
        double price=price(j);
        return Database.transaction(c->{
            var related=LibraryDao.rows(c,"SELECT id,title,author,status FROM book WHERE title LIKE ? ORDER BY createTime DESC","%"+name+"%");
            if(!related.isEmpty()) return Map.of("existing",true,"books",related);
            require(LibraryDao.count(c,"SELECT COUNT(*) FROM bookrecommend WHERE bookName=? AND userId=? AND status='待审核'",name,user.getId())==0,"你已推荐过这本书，正在等待审核");
            BookRecommend r=BookService.initialize(new BookRecommend());r.setUser(user);r.setBookName(name);r.setAuthor(author);r.setPublisher(publisher);r.setPrice(price);r.setStatus("待审核");
            JDBCTool.save(r,c);return Map.of("existing",false,"id",r.getId());
        });
    }
    public List<Map<String,Object>> list(User user) throws Exception {
        boolean admin=user instanceof Admin;
        return Database.read(c->LibraryDao.rows(c,"SELECT r.id,r.bookName,r.author,r.publisher,r.price,r.status,r.reason,r.createTime,u.name FROM bookrecommend r JOIN user u ON u.id=r.userId "+(admin?"":"WHERE r.userId=? ")+"ORDER BY r.createTime DESC,r.id DESC",admin?new Object[]{}:new Object[]{user.getId()}));
    }
    public void review(JsonObject j) throws Exception {
        String id=required(j,"id","推荐ID",36),decision=required(j,"decision","审核结果",20);
        require(Set.of("approve","reject").contains(decision),"未知审核操作");
        Database.transaction(c->{
            var rec=LibraryDao.one(c,"SELECT * FROM bookrecommend WHERE id=? FOR UPDATE",id);
            require(rec!=null,"推荐不存在");require("待审核".equals(rec.get("status")),"这条推荐已审核");
            if("reject".equals(decision)) {
                String reason=required(j,"reason","驳回理由",100);
                LibraryDao.update(c,"UPDATE bookrecommend SET status='已驳回',reason=?,updateTime=? WHERE id=?",reason,LocalDateTime.now(),id);
            } else {
                require(LibraryDao.count(c,"SELECT COUNT(*) FROM book WHERE title=?",rec.get("bookName"))==0,"图书已存在，不能重复入库");
                // Type, loan days and initial stock are completed by the administrator at approval.
                JsonObject book=j.deepCopy();book.addProperty("title",rec.get("bookName").toString());book.addProperty("author",rec.get("author").toString());
                book.addProperty("publisher",rec.get("publisher").toString());book.addProperty("price",((Number)rec.get("price")).doubleValue());
                new BookService().create(c,book);
                LibraryDao.update(c,"UPDATE bookrecommend SET status='已入库',reason=NULL,updateTime=? WHERE id=?",LocalDateTime.now(),id);
            }
            return null;
        });
    }
}
