package com.zr.praxisai.springai.config;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class CommonConfiguration {
    //开启会话记忆
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }
    //注册向量库
    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }


    @Bean
    public ChatClient pdfChatClient(OpenAiChatModel model, ChatMemory chatMemory, VectorStore vectorStore) {
        return ChatClient
                .builder(model)
                .defaultSystem("你是一位严厉的程序员面试官，而我是候选人，来应聘 %s 的 %s 岗位，面试难度为 %s。请你向我依次提出问题（最多 20 个问题），我也会依次回复。在这期间请完全保持真人面试官的口吻，比如适当引导学员、或者表达出你对学员回答的态度。\n" +
                        "必须满足如下要求：\n" +
                        "1. 当学员回复 “开始” 时，你要正式开始面试\n" +
                        "2. 当学员表示希望 “结束面试” 时，你要结束面试\n" +
                        "3. 此外，当你觉得这场面试可以结束时（比如候选人回答结果较差、不满足工作年限的招聘需求、或者候选人态度不礼貌），必须主动提出面试结束，不用继续询问更多问题了。并且要在回复中包含字符串【面试结束】\n" +
                        "4. 面试结束后，应该给出候选人整场面试的表现和总结。")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory),  // 会话记忆
                        new QuestionAnswerAdvisor(
                                vectorStore,    // 向量库
                                SearchRequest.builder()  // 向量检索的请求参数
                                        .similarityThreshold(0.6)   // 相似度阈值
                                        .topK(2)     // 返回的文档片段数量
                                        .build()
                        )
                )
                .build();
    }

}
