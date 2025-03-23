package com.pj.service;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pj.common.User;


import javax.servlet.http.HttpServletRequest;



/**
 * 用户服务
 *
 */
public interface UserService extends IService<User> {

    //查询用户信息
    User getUserInfo(QueryWrapper<User> queryWrapper);



}
