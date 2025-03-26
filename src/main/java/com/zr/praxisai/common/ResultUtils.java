package com.zr.praxisai.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zr.praxisai.model.vo.QuestionBankVO;
import org.apache.poi.ss.formula.functions.T;

/**
 * 返回工具类
 *
 */
public class ResultUtils {

    //成功
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }
    // 失败1
    public static BaseResponse<?> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }
    //失败2
    public static BaseResponse<?> error(int code, String message) {
        return new BaseResponse<>(code, null, message);
    }
    //失败3								//错误码
    public static BaseResponse<?> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }

    public static BaseResponse<Page<QuestionBankVO>> error(ErrorCode errorCode, Object o, String message) {
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }
}
