package com.pj.sso;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.sso.config.SaSsoServerConfig;
import cn.dev33.satoken.sso.processor.SaSsoServerProcessor;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pj.common.DeviceUtils;
import com.pj.common.User;
import com.pj.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

//SSO Server端 Controller
@RestController
public class SsoServerController {

	//处理所有SSO相关请求：拆分式路由

	// SSO-Server：统一认证地址，接受参数：redirect=授权重定向地址
	// 作用: 用户未登录，重定向到登陆页面
	//      已登录，生成Ticket重定向回客户端（已登录内部是通过redis查看用户id来判断的）
	@RequestMapping("/sso/auth")
	public Object ssoAuth() {
		return SaSsoServerProcessor.instance.ssoAuth();
	}


	// SSO-Server：RestAPI 登录接口，账号密码登录接口，接受参数：name、pwd
	// 作用: 处理登录表单提交，调用doLoginHandle进行验证
	//      验证成功后生成SSO票据并返回给客户端
	@RequestMapping("/sso/doLogin")
	public Object ssoDoLogin() {
		return SaSsoServerProcessor.instance.ssoDoLogin();
	}
	/**
	 * 盐值，混淆密码
	 */
	public static final String SALT = "zr";

	@Resource
	private UserService userService;

	// 配置SSO相关参数 
	@Autowired
	private void configSso(SaSsoServerConfig ssoServer) {
		// 自定义API地址，用于修改统一认证中心的地址
		//SaSsoServerProcessor.instance.ssoServerTemplate.apiName.ssoAuth = "/sso/auth2";

		// 配置：未登录时返回的View
		ssoServer.notLoginView = () -> {
			return new ModelAndView("sa-login.html");
		};
		
		// 配置：登录处理函数  参数:账号密码
		ssoServer.doLoginHandle = (name, pwd) -> {
			// 1. 校验
			if (StringUtils.isAnyBlank(name, pwd)) {
				return SaResult.error("参数为空");
			}
			if (name.length() < 4) {
				return SaResult.error( "账号错误");
			}
			if (pwd.length() < 8) {
				return SaResult.error("密码错误");
			}
			// 2. 加密
			String encryptPassword = DigestUtils.md5DigestAsHex((SALT + pwd).getBytes());
			// 查询用户是否存在
			QueryWrapper<User> queryWrapper = new QueryWrapper<>();
			queryWrapper.eq("userAccount", name);
			queryWrapper.eq("userPassword", encryptPassword);
			User user = userService.getUserInfo(queryWrapper);
			// 用户不存在
			if (user == null) {
				// 登录失败，重定向到 /sso/auth 并携带错误参数
				return "redirect:/sso/auth?error=用户不存在或密码错误";
			}
			// 获取当前请求
			HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
			String device = DeviceUtils.getRequestDevice(request);
			// 使用 Sa-Token 登录，并指定设备，同端登录互斥
			StpUtil.login(user.getId(), device);
			StpUtil.getSession().set("user_login", user);
			return SaResult.ok("登录成功！").setData(StpUtil.getTokenValue());
		};

	}

	// 在 SsoServerController 或全局配置中添加
	@Bean
	public SaServletFilter saServletFilter() {
		return new SaServletFilter()
				.setBeforeAuth(obj -> {
					// 设置响应头防止缓存
					SaHolder.getResponse().setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
					SaHolder.getResponse().setHeader("Pragma", "no-cache");
					SaHolder.getResponse().setHeader("Expires", "0");
				});
	}
}
