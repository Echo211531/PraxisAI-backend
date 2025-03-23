package com.zr.praxisai.job.once;
import cn.hutool.core.collection.CollUtil;
import com.zr.praxisai.esdao.QuestionEsDao;
import com.zr.praxisai.model.dto.question.QuestionEsDTO;
import com.zr.praxisai.model.entity.Question;
import com.zr.praxisai.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全量同步题目到 es
 */
// todo 取消注释开启任务
@Component
@Slf4j
public class FullSyncQuestionToEs implements CommandLineRunner {

    @Resource
    private QuestionService questionService;
    @Resource
    private QuestionEsDao questionEsDao;
    //实现 CommandLineRunner 接口后，Spring Boot 在应用启动时会自动调用 run 方法
    @Override
    public void run(String... args) {
        // 全量从数据库中获取所有题目（数据量不大的情况下使用）
        List<Question> questionList = questionService.list();
        if (CollUtil.isEmpty(questionList)) {
            return;
        }
        // 转为 ES 请求实体类列表
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        // 分页批量插入到 ES
        final int pageSize = 500;  //定义每批次处理的数据量为 500 条
        int total = questionEsDTOList.size(); //题目总数
        log.info("全量同步开始, 总计 {}", total);
        for (int i = 0; i < total; i += pageSize) {
            // 注意同步的数据下标不能超过总数据量
            int end = Math.min(i + pageSize, total);  // 保证下标不越界
            log.info("sync from {} to {}", i, end);
            //每次循环保存 [i, end) 数据到es中
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("全量同步结束, 总计 {}", total);
    }
}
