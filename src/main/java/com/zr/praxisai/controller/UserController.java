package com.zr.praxisai.controller;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zr.praxisai.common.BaseResponse;
import com.zr.praxisai.common.DeleteRequest;
import com.zr.praxisai.common.ErrorCode;
import com.zr.praxisai.common.ResultUtils;
import com.zr.praxisai.constant.UserConstant;
import com.zr.praxisai.exception.BusinessException;
import com.zr.praxisai.exception.ThrowUtils;
import com.zr.praxisai.model.dto.user.*;
import com.zr.praxisai.model.entity.User;
import com.zr.praxisai.model.vo.LoginUserVO;
import com.zr.praxisai.model.vo.UserVO;
import com.zr.praxisai.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static com.zr.praxisai.service.impl.UserServiceImpl.SALT;


//用户接口
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    //用户注册
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            return null;
        }
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

//    // 用户登录
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    //用户注销
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    //获取当前登录用户
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(user));
    }

    //创建用户
    @PostMapping("/add")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest, HttpServletRequest request) {
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        String defaultPassword = "12345678";
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + defaultPassword).getBytes());
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    //删除用户
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    //更新用户
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest,
                                            HttpServletRequest request) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    //编辑用户信息（支持用户和管理员）
    @PostMapping("/edit")
    public BaseResponse<Boolean> editUser(@RequestBody UserEditRequest userEditRequest, HttpServletRequest request) {
        if (userEditRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 构建更新对象
        User user = new User();
        BeanUtils.copyProperties(userEditRequest, user);
        // 如果是用户编辑自己，强制设置 ID 为当前用户 ID（防止越权修改）
        user.setId(loginUser.getId());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    //根据 id 获取用户（仅管理员）
    @GetMapping("/get")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    //根据 id 获取包装类
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id, HttpServletRequest request) {
        BaseResponse<User> response = getUserById(id, request);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    //根据 id 获取包装类
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<User>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest,
                                                   HttpServletRequest request) {
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        return ResultUtils.success(userPage);
    }

    //分页获取用户封装列表
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest,
                                                       HttpServletRequest request) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
        List<UserVO> userVO = userService.getUserVO(userPage.getRecords());
        userVOPage.setRecords(userVO);
        return ResultUtils.success(userVOPage);
    }

    //更新个人信息
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateMyRequest userUpdateMyRequest,
                                              HttpServletRequest request) {
        if (userUpdateMyRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        User user = new User();
        BeanUtils.copyProperties(userUpdateMyRequest, user);
        user.setId(loginUser.getId());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    //添加用户签到记录
    @PostMapping("/add/sign_in")
    public BaseResponse<Boolean> addUserSignIn(HttpServletRequest request) {
        // 必须要登录才能签到
        User loginUser = userService.getLoginUser(request);
        boolean result = userService.addUserSignIn(loginUser.getId());
        return ResultUtils.success(result);
    }

    //获取用户签到记录
    @GetMapping("/get/sign_in")
    public BaseResponse<List<Integer>> getUserSignInRecord(Integer year, HttpServletRequest request) {
        // 必须要登录才能获取
        User loginUser = userService.getLoginUser(request);
        List<Integer> userSignInRecord = userService.getUserSignInRecord(loginUser.getId(), year);
        return ResultUtils.success(userSignInRecord);
    }

}
