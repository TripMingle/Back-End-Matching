package com.example.tripminglematching.listener;

import com.example.tripminglematching.service.MatchingService;
import com.example.tripminglematching.service.MessagePublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisMessageSubscriber implements MessageListener {

    private final MatchingService matchingService;
    public static final String ADD_USER_PUBLISH = "pubsub:addUser";
    public static final String RE_CALCULATE_USER_PUBLISH = "pubsub:reCalculateUser";
    public static final String DELETE_USER_PUBLISH = "pubsub:deleteUser";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final MessagePublisher messagePublisher;


    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        log.info("sub : "+channel);
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
            String messageId="";
            Long userPersonalityId=0L;

            switch (channel) {
                case(ADD_USER_PUBLISH):
                    userPersonalityId = jsonNode.get("userPersonalityId").asLong();
                    messageId = jsonNode.get("messageId").toString();
                    matchingService.addUserPersonality(userPersonalityId, messageId);
                    break;

                case(RE_CALCULATE_USER_PUBLISH):
                    userPersonalityId = jsonNode.get("userPersonalityId").asLong();
                    messageId = jsonNode.get("messageId").toString();
                    matchingService.recalculateUserPersonality(userPersonalityId,messageId);
                    break;

                case(DELETE_USER_PUBLISH):
                    userPersonalityId = jsonNode.get("userPersonalityId").asLong();
                    messageId = jsonNode.get("messageId").toString();
                    matchingService.deleteUserPersonality(userPersonalityId, messageId);
                    break;
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

    }
}
