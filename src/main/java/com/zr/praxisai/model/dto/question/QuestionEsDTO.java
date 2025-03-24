package com.zr.praxisai.model.dto.question;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zr.praxisai.model.entity.Question;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

// todo 取消注释开启 ES（须先配置 ES）
@Document(indexName = "question")
@Data
public class QuestionEsDTO implements Serializable {
    //日期格式
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    //id
    @Id
    private Long id;
    //静态字段:题目标题
    private String title;
    //静态字段：题目内容
    private String content;
    //静态字段：答案
    private String answer;
    //静态字段：标签列表
    private List<String> tags;
    //创建用户 id
    private Long userId;
    //所属题库 id
    private Long questionBankId;
    //创建时间
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date createTime;
    //是否删除
    private Integer isDelete;

    //移除动态字段...

    private static final long serialVersionUID = 1L;

    //对象转包装类
    public static QuestionEsDTO objToDto(Question question) {
        if (question == null) {
            return null;
        }
        QuestionEsDTO questionEsDTO = new QuestionEsDTO();
        BeanUtils.copyProperties(question, questionEsDTO);
        String tagsStr = question.getTags();
        if (StrUtil.isNotBlank(tagsStr)) {
            questionEsDTO.setTags(JSONUtil.toList(JSONUtil.parseArray(tagsStr), String.class));
        }
        return questionEsDTO;
    }
    //包装类转对象
    public static Question dtoToObj(QuestionEsDTO questionEsDTO) {
        if (questionEsDTO == null) {
            return null;
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionEsDTO, question);
        List<String> tagList = questionEsDTO.getTags();
        if (CollUtil.isNotEmpty(tagList)) {
            question.setTags(JSONUtil.toJsonStr(tagList));
        }
        return question;
    }
}