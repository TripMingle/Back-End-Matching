package com.example.tripminglematching.listener;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.example.tripminglematching.service.MatchingService;
import com.example.tripminglematching.service.MessagePublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.annotation.PostConstruct;
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
    public static final String MATCHING_USER = "pubsub:matching";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final MessagePublisher messagePublisher;

    @PostConstruct
    private void init() {
        objectMapper.registerModule(new JavaTimeModule());
    }

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

                case(MATCHING_USER):
                    Long userId = jsonNode.get("userId").asLong();
                    messageId = jsonNode.get("messageId").toString();
                    String countryName = jsonNode.get("countryName").toString().substring(1, jsonNode.get("countryName").toString().length()-1);
                    JsonNode startDateNode = jsonNode.get("startDate");
                    JsonNode endDateNode = jsonNode.get("endDate");
                    LocalDate startDate = LocalDate.of(startDateNode.get(0).asInt(), startDateNode.get(1).asInt(), startDateNode.get(2).asInt());
                    LocalDate endDate = LocalDate.of(endDateNode.get(0).asInt(), endDateNode.get(1).asInt(), endDateNode.get(2).asInt());
                    matchingService.matchUserAndBoard(userId,messageId,countryName,startDate,endDate);
                    break;
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

    }
}
