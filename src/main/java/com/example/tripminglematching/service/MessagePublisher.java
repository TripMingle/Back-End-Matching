package com.example.tripminglematching.service;

import com.example.tripminglematching.dto.UserPersonalityResDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    //topic
    public static final String TOPIC_ADD_USER_RES_PUBLISH = "pubsub:addUserRes";
    public static final String TOPIC_RE_CALCULATE_USER_RES_PUBLISH = "pubsub:reCalculateUserRes";
    public static final String TOPIC_DELETE_USER_RES_PUBLISH = "pubsub:deleteUserRes";


    //message
    public static final String ADD_USER_PERSONALITY_SUCCESS = "add user personality success";
    public static final String FAIL_TO_ADD_USER_PERSONALITY = "fail to add user personality";
    public static final String RE_CALCULATE_USER_PERSONALITY_SUCCESS = "recalculate user personality success";
    public static final String FAIL_TO_RE_CALCULATE_USER_PERSONALITY = "fail to recalculate user personality";
    public static final String DELETE_USER_PERSONALITY_SUCCESS = "delete user personality success";
    public static final String FAIL_TO_DELETE_USER_PERSONALITY = "tail to delete user personality";

    public MessagePublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    public void userPersonalityResPublish(Long userPersonalityId, String messageId, String channel, String message) {
        try {
            UserPersonalityResDTO userPersonalityResDTO = new UserPersonalityResDTO();
            userPersonalityResDTO.setMessage(message);
            userPersonalityResDTO.setMessageId(messageId);
            userPersonalityResDTO.setUserPersonalityId(userPersonalityId);

            // JSON 객체 생성
            String jsonMessage = objectMapper.writeValueAsString(userPersonalityResDTO);

            // JSON 메시지를 Redis에 발행
            redisTemplate.convertAndSend(channel, jsonMessage);
            System.out.println("Published message: " + jsonMessage + " to topic: " + channel);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }



}