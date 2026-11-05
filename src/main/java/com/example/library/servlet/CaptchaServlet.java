package com.example.library.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.SecureRandom;
import javax.imageio.ImageIO;

@WebServlet("/api/captcha")
public class CaptchaServlet extends HttpServlet {
    private static final SecureRandom RANDOM=new SecureRandom();
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws IOException {
        String code=String.valueOf(10000+RANDOM.nextInt(90000));
        HttpSession session=request.getSession();session.setAttribute("captcha",code);session.setAttribute("captchaTime",System.currentTimeMillis());
        BufferedImage image=new BufferedImage(150,48,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(239,244,240));g.fillRect(0,0,150,48);
        for(int i=0;i<10;i++){g.setColor(new Color(150+RANDOM.nextInt(80),170+RANDOM.nextInt(60),170));g.drawLine(RANDOM.nextInt(150),RANDOM.nextInt(48),RANDOM.nextInt(150),RANDOM.nextInt(48));}
        g.setFont(new Font(Font.MONOSPACED,Font.BOLD,29));
        for(int i=0;i<code.length();i++){g.setColor(new Color(25+RANDOM.nextInt(50),60+RANDOM.nextInt(70),65+RANDOM.nextInt(50)));g.drawString(code.substring(i,i+1),12+i*26,34+RANDOM.nextInt(5));}
        g.dispose();response.setContentType("image/png");response.setHeader("Cache-Control","no-store");ImageIO.write(image,"png",response.getOutputStream());
    }
}
