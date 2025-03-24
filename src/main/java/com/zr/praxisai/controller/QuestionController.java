package com.zr.praxisai.controller;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zr.praxisai.common.BaseResponse;
import com.zr.praxisai.common.DeleteRequest;
import com.zr.praxisai.common.ErrorCode;
import com.zr.praxisai.common.ResultUtils;
import com.zr.praxisai.constant.UserConstant;
import com.zr.praxisai.exception.BusinessException;
import com.zr.praxisai.exception.ThrowUtils;
import com.zr.praxisai.manager.CounterManager;
import com.zr.praxisai.model.dto.question.*;
import com.zr.praxisai.model.entity.Question;
import com.zr.praxisai.model.entity.User;
import com.zr.praxisai.model.vo.QuestionVO;
import com.zr.praxisai.service.QuestionService;
import com.zr.praxisai.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 题目接口
 *
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;


    //创建题目
    @PostMapping("/add")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<String> tags = questionAddRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, true);
        // todo 填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    //删除题目
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // 更新题目（仅管理员可用）
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<String> tags = questionUpdateRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    //根据 id 获取题目（封装类）
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 检测和处置爬虫（可以自行扩展为 - 登录后才能获取到答案）
        // 获取题目时获取登录用户，如果登录用户为空，则直接返回题目信息
        // 否则，检测是否是爬虫，如果是爬虫，则直接返回错误
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            crawlerDetect(loginUser.getId());  //检测爬虫
        }
        // 友情提示，对于敏感的内容，可以再打印一些日志，记录用户访问的内容
        // 查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    // 仅是为了方便，才把这段代码写到这里
    @Resource
    private CounterManager counterManager;
    //检测loginUserId的爬虫
    private void crawlerDetect(long loginUserId) {
        // 调用多少次时告警
        final int WARN_COUNT = 10;
        // 调用多少次时封号
        final int BAN_COUNT = 20;
        // 拼接访问 key
        String key = String.format("user:access:%s", loginUserId);
        // 统计一分钟内访问次数，180 秒过期
        long count = counterManager.incrAndGetCounter(key, 1, TimeUnit.MINUTES, 180);
        // 是否封号
        if (count > BAN_COUNT) { //次数>阈值
            // 踢下线
            StpUtil.kickout(loginUserId);
            // 封号
            User updateUser = new User();
            updateUser.setId(loginUserId);
            updateUser.setUserRole("ban");
            userService.updateById(updateUser);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问次数过多，已被封号");
        }
        // 是否告警
        if (count == WARN_COUNT) {
            // 可以改为向管理员发送邮件通知
            throw new BusinessException(110, "警告：访问太频繁");
        }
    }

    //分页获取题目列表（仅管理员可用）
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
        return ResultUtils.success(questionPage);
    }

    //分页获取题目列表（封装类）
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    //分页获取当前登录用户创建的题目列表
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    //编辑题目（给用户使用）
    @PostMapping("/edit")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<String> tags = questionEditRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }
    // endregion

    //批量删除
    @PostMapping("/delete/batch")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchDeleteQuestions(@RequestBody QuestionBatchDeleteRequest questionBatchDeleteRequest) {
        ThrowUtils.throwIf(questionBatchDeleteRequest == null, ErrorCode.PARAMS_ERROR);
        questionService.batchDeleteQuestions(questionBatchDeleteRequest.getQuestionIdList());
        return ResultUtils.success(true);
    }

    //分页获取题目列表（封装类 - 限流版）
    //分页获取题目列表（封装类 - 限流版）
//    @PostMapping("/list/page/vo/sentinel")
//    public BaseResponse<Page<QuestionVO>> listQuestionVOByPageSentinel(@RequestBody QuestionQueryRequest questionQueryRequest,
//                                                                       HttpServletRequest request) {
//        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
//        long size = questionQueryRequest.getPageSize();
//        // 限制爬虫
//        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
//        // 基于 IP 限流
//        String remoteAddr = request.getRemoteAddr();
//        Entry entry = null;
//        try {
//            entry = SphU.entry(SentinelConstant.listQuestionVOByPage, EntryType.IN, 1, remoteAddr);
//            // 被保护的业务逻辑
//            // 查询数据库
//            Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
//            // 获取封装类
//            return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
//        } catch (Throwable ex) {
//            // 业务异常
//            if (!BlockException.isBlockException(ex)) {
//                Tracer.trace(ex);
//                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
//            }
//            // 降级操作
//            if (ex instanceof DegradeException) {
//                return handleFallback(questionQueryRequest, request, ex);
//            }
//            // 限流操作
//            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "访问过于频繁，请稍后再试");
//        } finally {
//            if (entry != null) {
//                entry.exit(1, remoteAddr);
//            }
//        }
//    }

    //listQuestionVOByPageSentinel 降级操作：直接返回本地数据（此处为了方便演示，写在同一个类中）
    public BaseResponse<Page<QuestionVO>> handleFallback(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                         HttpServletRequest request, Throwable ex) {
        // 可以返回本地数据或空数据
        return ResultUtils.success(null);
    }

    // 搜索题目，取消注释开启ES
    @PostMapping("/search/page/vo")
    public BaseResponse<Page<QuestionVO>> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        // todo 取消注释开启 ES（须先配置 ES）
        Page<Question> questionPage;
        try {
            // 优先查询ES(动静分离策略)
            questionPage = questionService.searchFromEs(questionQueryRequest);
        } catch (ElasticsearchException e) {
            // 查询数据库（作为没有 ES 的降级方案）
            log.error("ES查询失败，降级到数据库查询", e);
            questionPage = questionService.listQuestionByPage(questionQueryRequest);
        } catch (Exception e) {
            // 其他未知异常直接抛出
            log.error("搜索接口发生未知异常", e);
            throw e;
        }
        //返回数据给前端
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    //AI 生成题目（仅管理员可用）
    //AI 生成题目（仅管理员可用）
//    @PostMapping("/ai/generate/question")
//    @SaCheckRole(UserConstant.ADMIN_ROLE)
//    public BaseResponse<Boolean> aiGenerateQuestions(@RequestBody QuestionAIGenerateRequest questionAIGenerateRequest, HttpServletRequest request) {
//        String questionType = questionAIGenerateRequest.getQuestionType();
//        int number = questionAIGenerateRequest.getNumber();
//        // 校验参数
//        ThrowUtils.throwIf(StrUtil.isBlank(questionType), ErrorCode.PARAMS_ERROR, "题目类型不能为空");
//        ThrowUtils.throwIf(number <= 0, ErrorCode.PARAMS_ERROR, "题目数量必须大于 0");
//        // 获取当前登录用户
//        User loginUser = userService.getLoginUser(request);
//        // 调用 AI 生成题目服务
//        questionService.aiGenerateQuestions(questionType, number, loginUser);
//        // 返回结果
//        return ResultUtils.success(true);
//    }

}
