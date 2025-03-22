package com.zr.praxisai.controller;
import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.dev33.satoken.sso.template.SaSsoUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import org.springframework.web.bind.annotation.*;


//前后台分离架构下集成SSO所需的代码 （SSO-Client端）
@RestController
public class H5Controller {

	// 当前是否登录
	@GetMapping("/sso/isLogin")
	public Object isLogin() {
		return SaResult.data(StpUtil.isLogin());
	}

	// 返回SSO认证中心登录地址 
	@GetMapping("/sso/getSsoAuthUrl")
	public SaResult getSsoAuthUrl(String clientLoginUrl) {
		String serverAuthUrl = SaSsoUtil.buildServerAuthUrl(clientLoginUrl, "");
		return SaResult.data(serverAuthUrl);
	}
	
	// 根据ticket进行登录
	@PostMapping("/sso/doLoginByTicket")
	public SaResult doLoginByTicket(String ticket) {
		SaCheckTicketResult ctr = SaSsoClientProcessor.instance.checkTicket(ticket, "/api/sso/doLoginByTicket");
		StpUtil.login(ctr.loginId, ctr.remainSessionTimeout);
		return SaResult.data(StpUtil.getTokenValue());
	}

	// 全局异常拦截 
	@ExceptionHandler
	public SaResult handlerException(Exception e) {
		e.printStackTrace(); 
		return SaResult.error(e.getMessage());
	}
	
}
