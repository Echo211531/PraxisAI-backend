package com.zr.praxisai.model.dto.question;
import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

import static cn.dev33.satoken.dao.SaTokenDaoRedisJackson.DATE_TIME_PATTERN;

//ES分词搜索模块：动态数据数据库查询
@Data
public class QuestionDynamicFields {
    private Long id;
    private Integer likeCount;  // 点赞数(方便后续扩展)
    private Integer browseCount; // 浏览数(方便后续扩展)
    private Integer commentCount; //评论数(方便后续扩展)
    //更新时间
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date updateTime;
    // 其他动态字段...
}