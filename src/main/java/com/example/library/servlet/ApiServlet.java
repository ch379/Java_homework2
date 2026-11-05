package com.example.library.servlet;

import com.example.library.dao.LibraryDao;
import com.example.library.entity.*;
import com.example.library.service.*;
import com.example.library.util.*;
import com.google.gson.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import static com.example.library.service.Validation.*;

@WebServlet("/api/*")
public class ApiServlet extends HttpServlet {
    private final UserService users=new UserService();
    private final BookService books=new BookService();
    private final LoanService loans=new LoanService();
    private final RecommendationService recommendations=new RecommendationService();
    private static class HttpError extends RuntimeException {
        final int status;HttpError(int status,String message){super(message);this.status=status;}
    }
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws IOException { process(request,response); }
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response) throws IOException { process(request,response); }
    private void process(HttpServletRequest req,HttpServletResponse res) throws IOException {
        res.setContentType("application/json");
        try { Object result=route(req);res.getWriter().write(Json.GSON.toJson(Map.of("ok",true,"data",result==null?Map.of():result))); }
        catch(HttpError e) {error(res,e.status,e.getMessage());}
        catch(IllegalArgumentException e) {error(res,400,e.getMessage()==null?"请求格式不正确":e.getMessage());}
        catch(Exception e) {getServletContext().log("Library API failure: "+req.getPathInfo(),e);error(res,500,"服务暂时不可用，请检查数据库连接或联系管理员");}
    }
    private static void error(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.getWriter().write(Json.GSON.toJson(Map.of("ok",false,"message",message)));
    }
    private Object route(HttpServletRequest req) throws Exception {
        String path=req.getPathInfo()==null?"":req.getPathInfo();
        boolean post="POST".equals(req.getMethod());
        if(!post&&"/session".equals(path)) {
            HttpSession session=req.getSession();if(session.getAttribute("csrf")==null)session.setAttribute("csrf",UUID.randomUUID().toString());
            Map<String,Object> data=new LinkedHashMap<>();data.put("csrf",session.getAttribute("csrf"));
            User user=(User)session.getAttribute("user");data.put("user",user==null?null:LibraryDao.publicUser(user));return data;
        }
        if(post&&"/register".equals(path)){users.register(body(req));return Map.of("message","注册成功，请登录");}
        if(post&&"/login".equals(path)) {
            JsonObject j=body(req);HttpSession session=req.getSession();
            String expected=(String)session.getAttribute("captcha");Long time=(Long)session.getAttribute("captchaTime");session.removeAttribute("captcha");
            require(expected!=null&&time!=null&&System.currentTimeMillis()-time<120000&&expected.equals(text(j,"captcha")),"验证码错误或已过期，请刷新验证码");
            String password=j.has("password")?j.get("password").getAsString():"";
            User user=users.login(required(j,"name","用户名",50),password);req.changeSessionId();session.setAttribute("user",user);return LibraryDao.publicUser(user);
        }
        User user=current(req);
        if(post&&"/logout".equals(path)){req.getSession().invalidate();return null;}
        if(!post) return switch(path) {
            case "/books" -> books.search(param(req,"keyword"),"true".equals(param(req,"available")));
            case "/copies" -> {permit(user,"manageBooks");yield books.copies(param(req,"bookId"));}
            case "/users" -> {permit(user,"users");yield users.search(param(req,"keyword"));}
            case "/users/detail" -> {permit(user,"users");yield users.detail(param(req,"id"));}
            case "/my-loans" -> loans.search(user.getId(),param(req,"keyword"),param(req,"state"),param(req,"start"),param(req,"end"));
            case "/loans" -> {permit(user,"loans");yield loans.search(null,param(req,"keyword"),param(req,"state"),param(req,"start"),param(req,"end"));}
            case "/statistics" -> {permit(user,"statistics");yield loans.statistics();}
            case "/recommendations" -> {permitAny(user,"recommend","recommendations");yield recommendations.list(user);}
            default -> throw new HttpError(404,"接口不存在");
        };
        JsonObject j=body(req);
        return switch(path) {
            case "/books/add" -> {permit(user,"manageBooks");yield Map.of("id",books.add(j));}
            case "/books/edit" -> {permit(user,"manageBooks");books.edit(j);yield null;}
            case "/books/discard" -> {permit(user,"manageBooks");books.discard(required(j,"id","图书ID",36));yield null;}
            case "/copies/add" -> {permit(user,"manageBooks");books.addCopy(required(j,"bookId","图书ID",36));yield null;}
            case "/copies/delete" -> {permit(user,"manageBooks");books.deleteCopy(required(j,"id","副本ID",36));yield null;}
            case "/borrow" -> {permit(user,"borrow");yield Map.of("copyId",loans.borrow(required(j,"bookId","图书ID",36),user));}
            case "/return" -> {permit(user,"return");loans.returnBook(required(j,"copyId","副本ID",36),user);yield null;}
            case "/recommend" -> {permit(user,"recommend");yield recommendations.recommend(j,user);}
            case "/recommendations/review" -> {permit(user,"recommendations");recommendations.review(j);yield null;}
            default -> throw new HttpError(404,"接口不存在");
        };
    }
    private static User current(HttpServletRequest req) {
        HttpSession session=req.getSession(false);User user=session==null?null:(User)session.getAttribute("user");
        if(user==null)throw new HttpError(401,"请先登录");return user;
    }
    private static void permit(User user,String permission) {
        if(!Arrays.asList(user.hasPermission()).contains(permission))throw new HttpError(403,"当前角色无权执行此操作");
    }
    private static void permitAny(User user,String a,String b) {
        var permissions=Arrays.asList(user.hasPermission());if(!permissions.contains(a)&&!permissions.contains(b))throw new HttpError(403,"当前角色无权执行此操作");
    }
    private static String param(HttpServletRequest req,String name) {String value=req.getParameter(name);return value==null?"":value.strip();}
    private static JsonObject body(HttpServletRequest req) throws IOException {
        require(req.getContentType()!=null&&req.getContentType().toLowerCase(Locale.ROOT).startsWith("application/json"),"请求必须使用JSON格式");
        char[] data=new char[65537];int total=0,n;Reader reader=req.getReader();
        while(total<data.length&&(n=reader.read(data,total,data.length-total))!=-1)total+=n;
        require(total<=65536,"请求内容过长");
        try {JsonElement element=JsonParser.parseString(new String(data,0,total));require(element.isJsonObject(),"请求必须是JSON对象");return element.getAsJsonObject();}
        catch(JsonParseException e){throw new IllegalArgumentException("JSON格式不正确");}
    }
}
