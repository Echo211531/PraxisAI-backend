package com.zr.praxisai.job.cycle;
import cn.hutool.core.collection.CollUtil;
import com.zr.praxisai.esdao.QuestionEsDao;
import com.zr.praxisai.mapper.QuestionMapper;
import com.zr.praxisai.model.dto.question.QuestionEsDTO;
import com.zr.praxisai.model.entity.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 增量同步题目到 es
 */
// todo 取消注释开启任务
@Component
@Slf4j
public class IncSyncQuestionToEs {

    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private QuestionEsDao questionEsDao;

    //每分钟执行一次
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        // 查询最近 5 分钟内的数据
        long FIVE_MINUTES = 5 * 60 * 1000L;
        Date fiveMinutesAgoDate = new Date(new Date().getTime() - FIVE_MINUTES);
        // 查询最近 5 分钟内的所有数据列表
        List<Question> questionList = questionMapper.listQuestionWithDelete(fiveMinutesAgoDate);
        if (CollUtil.isEmpty(questionList)) {
            log.info("no inc question");
            return;
        }
        //转换成ES请求实体类列表
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("增量同步开始, 总计 {}", total);
        //同理增量同步到ES中
        for (int i = 0; i < total; i += pageSize) {
            int end = Math.min(i + pageSize, total);
            log.info("sync from {} to {}", i, end);
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("增量同步结束, 总计 {}", total);
    }
}