package com.example.tripminglematching.listener;

import com.example.tripminglematching.service.MatchingService;
import com.example.tripminglematching.service.MessagePublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final MatchingService matchingService;
    private static final String ADD_USER_PUBLISH = "pubsub:addUser";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final MessagePublisher messagePublisher;


    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String messageBody = new String(message.getBody());
        StringBuilder cleanedMessageBody = new StringBuilder();
        for (char ch : messageBody.toCharArray()) {
            if (ch != '\\') {
                cleanedMessageBody.append(ch);
            }
        }
        messageBody = cleanedMessageBody.substring(1, cleanedMessageBody.length()-1).toString();

        try {
            JsonNode jsonNode = objectMapper.readTree(messageBody);

            if (channel.equals(ADD_USER_PUBLISH)) {
                Long userPersonalityId = jsonNode.get("userPersonalityId").asLong();
                String messageId = jsonNode.get("messageId").toString();
                matchingService.addUser(userPersonalityId, messageId);
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

    }
}
