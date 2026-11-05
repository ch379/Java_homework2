package com.example.library.service;

import com.example.library.dao.LibraryDao;
import com.example.library.entity.*;
import com.example.library.orm.JDBCTool;
import com.example.library.util.Database;
import java.time.*;
import java.util.*;
import static com.example.library.service.Validation.*;

public final class LoanService {
    public String borrow(String bookId,User user) throws Exception {
        return Database.transaction(c -> {
            var book=BookService.lockBook(c,bookId);require("正常".equals(book.get("status")),"图书已废弃，不能借阅");
            var row=LibraryDao.one(c,"SELECT c.id FROM bookcopy c WHERE c.bookId=? AND c.isBorrowed=0 AND "+LibraryDao.ACTIVE_COPY+" ORDER BY c.id LIMIT 1 FOR UPDATE",bookId);
            require(row!=null,"暂无可借副本");
            BookCopy copy=new BookCopy();copy.setId(row.get("id").toString());
            Loan loan=BookService.initialize(new Loan());loan.setBookCopy(copy);loan.setUser(user);loan.setBorrowDate(LocalDateTime.now());
            int days=((Number)book.get("days")).intValue();require(days>0,"图书借阅期限异常，请联系操作员");
            loan.setDueDate(loan.getBorrowDate().plusDays(days));loan.setStatus("未还");JDBCTool.save(loan,c);
            LibraryDao.update(c,"UPDATE bookcopy SET isBorrowed=1,updateTime=? WHERE id=?",LocalDateTime.now(),copy.getId());return copy.getId();
        });
    }
    public void returnBook(String copyId,User user) throws Exception {
        Database.transaction(c -> {
            var ref=LibraryDao.one(c,"SELECT bookId FROM bookcopy WHERE id=?",copyId);require(ref!=null,"副本不存在");
            BookService.lockBook(c,ref.get("bookId").toString());
            var copy=LibraryDao.one(c,"SELECT id,isBorrowed FROM bookcopy WHERE id=? FOR UPDATE",copyId);
            require(BookService.isTrue(copy.get("isBorrowed")),"该副本已归还");
            var loan=LibraryDao.one(c,"SELECT id FROM loan WHERE bookCopyId=? AND userId=? AND returnDate IS NULL ORDER BY borrowDate DESC LIMIT 1 FOR UPDATE",copyId,user.getId());
            require(loan!=null,"未找到属于你的未还借阅记录");
            LibraryDao.update(c,"UPDATE loan SET returnDate=?,status='已还',updateTime=? WHERE id=?",LocalDateTime.now(),LocalDateTime.now(),loan.get("id"));
            LibraryDao.update(c,"UPDATE bookcopy SET isBorrowed=0,updateTime=? WHERE id=?",LocalDateTime.now(),copyId);return null;
        });
    }
    public List<Map<String,Object>> search(String userId,String keyword,String state,String start,String end) throws Exception {
        StringBuilder sql=new StringBuilder("SELECT l.id,l.bookCopyId,l.userId,b.id bookId,b.title,b.type,u.name,u.major,l.borrowDate,l.returnDate,l.dueDate,CASE WHEN l.returnDate IS NOT NULL THEN '已还' WHEN l.dueDate<NOW() THEN '超期' ELSE '未还' END status FROM loan l JOIN bookcopy c ON c.id=l.bookCopyId JOIN book b ON b.id=c.bookId JOIN user u ON u.id=l.userId WHERE 1=1");
        List<Object> args=new ArrayList<>();
        if(userId!=null) {sql.append(" AND l.userId=?");args.add(userId);}
        if(!keyword.isBlank()) {sql.append(" AND (u.name LIKE ? OR b.title LIKE ?)");args.add("%"+keyword+"%");args.add("%"+keyword+"%");}
        if("unreturned".equals(state)) sql.append(" AND l.returnDate IS NULL");
        else if("overdue".equals(state)) sql.append(" AND l.returnDate IS NULL AND l.dueDate<NOW()");
        else require(state.isBlank()||"all".equals(state),"未知借阅状态");
        LocalDate from=start.isBlank()?null:date(start,"开始日期"), to=end.isBlank()?null:date(end,"结束日期");
        require(from==null||to==null||!from.isAfter(to),"开始日期不能晚于结束日期");
        if(from!=null){sql.append(" AND l.borrowDate>=?");args.add(from.atStartOfDay());}
        if(to!=null){sql.append(" AND l.borrowDate<?");args.add(to.plusDays(1).atStartOfDay());}
        sql.append(" ORDER BY l.borrowDate DESC,l.id DESC");
        return Database.read(c->LibraryDao.rows(c,sql.toString(),args.toArray()));
    }
    public Map<String,Object> statistics() throws Exception {
        return Database.read(c->{
            var types=LibraryDao.rows(c,"SELECT b.type label,COUNT(l.id) total FROM book b LEFT JOIN bookcopy c ON c.bookId=b.id LEFT JOIN loan l ON l.bookCopyId=c.id GROUP BY b.type ORDER BY total DESC,b.type");
            var majors=LibraryDao.rows(c,"SELECT COALESCE(u.major,'未填写') label,COUNT(l.id) total FROM user u LEFT JOIN loan l ON l.userId=u.id GROUP BY u.major ORDER BY total DESC,u.major");
            return Map.of("types",types,"majors",majors);
        });
    }
}
