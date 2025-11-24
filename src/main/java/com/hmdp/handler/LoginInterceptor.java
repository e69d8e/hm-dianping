package com.hmdp.handler;

import com.hmdp.utils.UserHolder;
import lombok.Data;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Data
public class LoginInterceptor implements HandlerInterceptor {


    /**
     * 请求处理之后进行调用，但是在视图被渲染之前（Controller方法调用之后）
     */
//    @Override
//    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
//        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
//    }

    /**
     * 请求处理之前进行调用（Controller方法调用之前）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 获取 session
//        HttpSession session = request.getSession();
//        // 获取 session 中的用户
//        Object user = session.getAttribute("user");
//        // 判断是否存在
//        if (user == null) {
//            // 不存在则拦截
//            response.setStatus(401);
//            return false;
//        }
//        // 存在则放行 保存到 ThreadLocal
//        UserHolder.saveUser((UserDTO) user);
//        return true;

        // 判断是否需要拦截
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }

    /**
     * 请求处理之后，视图被渲染之后进行调用，一般用于资源清理
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
