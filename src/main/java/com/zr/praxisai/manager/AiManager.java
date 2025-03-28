package com.zr.praxisai.manager;
import cn.hutool.core.collection.CollUtil;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import com.zr.praxisai.common.ErrorCode;
import com.zr.praxisai.exception.BusinessException;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用的 AI 调用类
 */
//参考官网API文档编写即可
@Service
public class AiManager {

    @Resource
    private ArkService aiService;

    private final String DEFAULT_MODEL = "deepseek-v3-25034";  //官网查看model-id

    ///调用 AI 接口，仅根据用户输入的内容返回响应
    public String doChat(String systemPrompt,String userPrompt) {
        return doChat(systemPrompt, userPrompt, DEFAULT_MODEL);
    }


    ///调用 AI 接口，获取响应字符串
    public String doChat(String systemPrompt, String userPrompt, String model) {
        // 构造消息列表
        final List<ChatMessage> messages = new ArrayList<>();
        //创建两个消息对象
            //1.系统角色消息,用于设置上下文或指导 AI 的行为
            //2.用户角色消息,表示用户的输入内容
        final ChatMessage systemMessage = ChatMessage.builder().
                role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().
                role(ChatMessageRole.USER).content(userPrompt).build();
        //加到消息列表中
        messages.add(systemMessage);
        messages.add(userMessage);

        return doChat(messages, model);

    }

     ///调用 AI 接口，获取响应字符串（允许传入自定义的消息列表，使用默认模型）
    public String doChat(List<ChatMessage> messages) {
        return doChat(messages, DEFAULT_MODEL);
    }

    ///调用 AI 接口，获取响应字符串（允许传入自定义的消息列表）
    public String doChat(List<ChatMessage> messages, String model) {
        // 构造请求
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
//                .model("deepseek-v3-241226")
                .model(model)
                .messages(messages)  //消息列表,包含系统提示和用户输入
                .build();
        // 调用接口发送请求,得到响应列表
        List<ChatCompletionChoice> choices = aiService.
                createChatCompletion(chatCompletionRequest).getChoices();
        if (CollUtil.isNotEmpty(choices)) {
            //返回消息内容
            return (String) choices.get(0).getMessage().getContent();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用失败，没有返回结果");
//        // shutdown service after all requests is finished
//        aiService.shutdownExecutor();
    }
}

