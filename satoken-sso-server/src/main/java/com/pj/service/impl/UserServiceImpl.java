package com.pj.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pj.common.User;
import com.pj.mapper.UserMapper;
import com.pj.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;



/**
 * 用户服务实现
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    public User getUserInfo(QueryWrapper<User> queryWrapper){
        return this.baseMapper.selectOne(queryWrapper);
    }

}






