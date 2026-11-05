package com.example.library.filter;

import com.example.library.util.Json;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

@WebFilter("/api/*")
public class ApiFilter implements Filter {
    @Override public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain) throws IOException,ServletException {
        HttpServletRequest request=(HttpServletRequest)req;HttpServletResponse response=(HttpServletResponse)res;
        request.setCharacterEncoding("UTF-8");response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("X-Frame-Options","DENY");
        if ("POST".equals(request.getMethod())) {
            HttpSession session=request.getSession(false);
            String actual=request.getHeader("X-CSRF-Token");
            if(session==null||actual==null||!actual.equals(session.getAttribute("csrf"))) {
                response.setStatus(403);response.setContentType("application/json");response.getWriter().write(Json.GSON.toJson(Map.of("ok",false,"message","页面会话已失效，请刷新后重试")));return;
            }
        }
        chain.doFilter(req,res);
    }
}
