package com.zr.praxisai.blackfilter;


import com.zr.praxisai.utils.NetUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 全局 IP 黑名单过滤请求拦截器
 */
//声明一个过滤器，拦截所有的 HTTP 请求
@WebFilter(urlPatterns = "/*", filterName = "blackIpFilter")
public class BlackIpFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        //获取客户端IP地址
        String ipAddress = NetUtils.getIpAddress((HttpServletRequest) servletRequest);
        //判断IP地址是否在黑名单中(布隆过滤器中)
        if (BlackIpUtils.isBlackIp(ipAddress)) {
            //如果在，则返回错误信息
            servletResponse.setContentType("text/json;charset=UTF-8");
            servletResponse.getWriter().write("{\"errorCode\":\"-1\",\"errorMsg\":\"黑名单IP，禁止访问\"}");
            return;
        }
        //放行请求
        filterChain.doFilter(servletRequest, servletResponse);
    }
}