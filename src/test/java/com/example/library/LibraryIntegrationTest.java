package com.example.library;

import com.example.library.dao.LibraryDao;
import com.example.library.entity.*;
import com.example.library.orm.JDBCTool;
import com.example.library.service.*;
import com.example.library.util.Database;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Run only against a separate, restored test database; original rows are read, never modified. */
@EnabledIfEnvironmentVariable(named="LIBRARY_INTEGRATION",matches="true")
class LibraryIntegrationTest {
    private final BookService books=new BookService();
    private final LoanService loans=new LoanService();
    private final UserService users=new UserService();
    private final RecommendationService recommendations=new RecommendationService();
    private final List<String> bookIds=new ArrayList<>(),userIds=new ArrayList<>();
    private String suffix;
    @BeforeEach void setup() {
        assertTrue(System.getenv("LIBRARY_DB_URL").contains("libraryweb2026_test"),"Integration tests require the dedicated test database");
        suffix=UUID.randomUUID().toString().substring(0,8);
    }
    private JsonObject book(int stock) {
        JsonObject j=new JsonObject();j.addProperty("title","集成测试"+suffix);j.addProperty("author","测试作者");j.addProperty("type","计算机");
        j.addProperty("publisher","测试出版社");j.addProperty("days",7);j.addProperty("price",25.5);j.addProperty("stock",stock);return j;
    }
    private String addBook(int stock)throws Exception {String id=books.add(book(stock));bookIds.add(id);return id;}
    private User user(String role)throws Exception {
        JsonObject j=new JsonObject();j.addProperty("name","测试"+suffix+userIds.size());j.addProperty("password","Test-password-123");
        String digits=String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        j.addProperty("phone","139"+digits.substring(0,8));j.addProperty("role",role);j.addProperty("major","测试专业");
        j.addProperty("grade","2026级");j.addProperty("college","测试学院");j.addProperty("title","讲师");j.addProperty("area","A区");j.addProperty("duty","馆员");j.addProperty("permissionLevel",1);j.addProperty("entryTime","2026-01-01");
        users.register(j);
        userIds.add(Database.read(c->LibraryDao.one(c,"SELECT id FROM user WHERE name=?",j.get("name").getAsString()).get("id").toString()));
        return users.login(j.get("name").getAsString(),"Test-password-123");
    }
    @AfterEach void cleanup()throws Exception {
        Database.transaction(c->{
            for(String userId:userIds){
                LibraryDao.update(c,"DELETE FROM loan WHERE userId=?",userId);LibraryDao.update(c,"DELETE FROM bookrecommend WHERE userId=?",userId);
            }
            for(String bookId:bookIds){
                LibraryDao.update(c,"DELETE FROM loan WHERE bookCopyId IN (SELECT id FROM bookcopy WHERE bookId=?)",bookId);
                LibraryDao.update(c,"DELETE FROM log WHERE type='副本删除' AND message IN (SELECT id FROM bookcopy WHERE bookId=?)",bookId);
                LibraryDao.update(c,"DELETE FROM bookcopy WHERE bookId=?",bookId);LibraryDao.update(c,"DELETE FROM book WHERE id=?",bookId);
            }
            for(String userId:userIds)LibraryDao.update(c,"DELETE FROM user WHERE id=?",userId);return null;
        });
    }
    @Test void inheritedFieldsAndMysqlDatesRoundTripThroughHomeworkOneOrm()throws Exception {
        Student u=(Student)user("学生");
        Database.transaction(c->{
            Student loaded=JDBCTool.getOneById(u.getId(),Student.class,c);
            assertEquals(u.getName(),loaded.getName());assertNotNull(loaded.getCreateTime());assertEquals("2026级",loaded.getGrade());
            loaded.setCollege("新学院");loaded.setUpdateTime(LocalDateTime.now());assertEquals(1,JDBCTool.update(loaded,c));
            assertEquals("新学院",JDBCTool.getOneById(u.getId(),Student.class,c).getCollege());return null;
        });
        Admin admin=(Admin)user("管理员");assertEquals(LocalDate.of(2026,1,1),admin.getEntryTime());
    }
    @Test void addEditAndCopyOperationsMaintainStock()throws Exception {
        String id=addBook(2);assertEquals(2,books.copies(id).size());
        JsonObject edit=book(999);edit.addProperty("id",id);edit.addProperty("title","修改后"+suffix);edit.addProperty("status","已废弃");books.edit(edit);
        var row=books.search("修改后"+suffix,false).getFirst();assertEquals(2,((Number)row.get("stock")).intValue());assertEquals("正常",row.get("status"));
        books.addCopy(id);assertEquals(3,books.copies(id).size());
        books.deleteCopy(books.copies(id).getFirst().get("id").toString());assertEquals(2,books.copies(id).size());
        assertEquals(2,((Number)books.search("修改后"+suffix,false).getFirst().get("stock")).intValue());
    }
    @Test void borrowedCopiesCannotBeDeletedOrDiscardedAndOtherUsersCannotReturnThem()throws Exception {
        String id=addBook(1);User owner=user("学生"),other=user("教师");String copy=loans.borrow(id,owner);
        assertThrows(IllegalArgumentException.class,()->books.deleteCopy(copy));assertThrows(IllegalArgumentException.class,()->books.discard(id));
        assertThrows(IllegalArgumentException.class,()->loans.returnBook(copy,other));assertThrows(IllegalArgumentException.class,()->loans.borrow(id,other));
        var records=loans.search(owner.getId(),"","unreturned","","");assertEquals(1,records.size());
        LocalDateTime borrowed=LocalDateTime.parse(records.getFirst().get("borrowDate").toString());
        assertEquals(borrowed.plusDays(7),LocalDateTime.parse(records.getFirst().get("dueDate").toString()));
        loans.returnBook(copy,owner);assertTrue(loans.search(owner.getId(),"","unreturned","","").isEmpty());
        assertThrows(IllegalArgumentException.class,()->loans.returnBook(copy,owner));
        books.deleteCopy(copy);assertTrue(books.copies(id).isEmpty());assertEquals(1,loans.search(owner.getId(),"","all","","").size());
        books.discard(id);assertThrows(IllegalArgumentException.class,()->books.addCopy(id));
    }
    @Test void concurrentRequestsCannotBorrowTheSameCopyTwice()throws Exception {
        String id=addBook(1);User user=user("学生");AtomicInteger successes=new AtomicInteger();CountDownLatch start=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures=new ArrayList<>();for(int i=0;i<2;i++)futures.add(executor.submit(()->{try{start.await();loans.borrow(id,user);successes.incrementAndGet();}catch(IllegalArgumentException expected){}catch(Exception e){throw new RuntimeException(e);}}));
            start.countDown();for(Future<?> future:futures)future.get(15,TimeUnit.SECONDS);
        }
        assertEquals(1,successes.get());assertEquals(1,loans.search(user.getId(),"","all","","").size());
    }
    @Test void recommendationsDetectExistingBooksApproveOnceAndKeepRejectionReason()throws Exception {
        String id=addBook(1);User teacher=user("教师");JsonObject rec=new JsonObject();rec.addProperty("bookName","集成测试"+suffix);rec.addProperty("author","作者");rec.addProperty("publisher","出版社");rec.addProperty("price",30);
        assertEquals(true,recommendations.recommend(rec,teacher).get("existing"));
        rec.addProperty("bookName","推荐"+suffix);var result=recommendations.recommend(rec,teacher);assertEquals(false,result.get("existing"));
        assertThrows(IllegalArgumentException.class,()->recommendations.recommend(rec,teacher));
        JsonObject review=new JsonObject();review.addProperty("id",result.get("id").toString());review.addProperty("decision","approve");review.addProperty("type","文学");review.addProperty("days",14);review.addProperty("stock",2);
        recommendations.review(review);var added=books.search("推荐"+suffix,false).getFirst();bookIds.add(added.get("id").toString());assertEquals(2,books.copies(added.get("id").toString()).size());
        assertThrows(IllegalArgumentException.class,()->recommendations.review(review));
        rec.addProperty("bookName","驳回"+suffix);var rejected=recommendations.recommend(rec,teacher);review.addProperty("id",rejected.get("id").toString());review.addProperty("decision","reject");review.addProperty("reason","");
        assertThrows(IllegalArgumentException.class,()->recommendations.review(review));review.addProperty("reason","暂不符合采购方向");recommendations.review(review);
        assertTrue(recommendations.list(teacher).stream().anyMatch(r->"暂不符合采购方向".equals(r.get("reason"))));
    }
    @Test void userDuplicatesAndInvalidPhonesAreRejected()throws Exception {
        User original=user("学生");JsonObject j=new JsonObject();j.addProperty("name",original.getName());j.addProperty("phone",original.getPhone());j.addProperty("password","Test-password-123");j.addProperty("role","学生");j.addProperty("major","专业");j.addProperty("grade","年级");j.addProperty("college","学院");
        assertThrows(IllegalArgumentException.class,()->users.register(j));j.addProperty("name","其他"+suffix);assertThrows(IllegalArgumentException.class,()->users.register(j));
        j.addProperty("phone","123");assertThrows(IllegalArgumentException.class,()->users.register(j));assertFalse(users.detail(original.getId()).containsKey("password"));
    }
    @Test void dateQueriesAreInclusiveAndStatisticsIncludeZeroCountCategories()throws Exception {
        String id=addBook(1);User user=user("学生");loans.borrow(id,user);String today=LocalDate.now().toString();
        assertEquals(1,loans.search(user.getId(),suffix,"all",today,today).size());
        assertTrue(loans.search(user.getId(),"","all",LocalDate.now().minusDays(2).toString(),LocalDate.now().minusDays(1).toString()).isEmpty());
        assertThrows(IllegalArgumentException.class,()->loans.search(user.getId(),"","all","2026-12-01","2026-01-01"));
        var stats=loans.statistics();assertFalse(((List<?>)stats.get("types")).isEmpty());
        assertEquals(9,((List<?>)stats.get("types")).size());
    }
}
