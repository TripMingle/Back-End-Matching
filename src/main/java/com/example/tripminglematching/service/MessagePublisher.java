package com.example.tripminglematching.service;

import com.example.tripminglematching.dto.AddUserResDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ADD_USER_RES_PUBLISH = "pubsub:addUserRes";
    private static final String FAIL_TO_ADD_USER_PERSONALITY = "fail to add user personality";
    private static final String ADD_USER_PERSONALITY_SUCCESS = "add user personality success";

    public MessagePublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    public void addUserResPublish(Long userPersonalityId, String messageId) {
        try {
            AddUserResDTO addUserResDTO = new AddUserResDTO();
            addUserResDTO.setMessage(ADD_USER_PERSONALITY_SUCCESS);
            addUserResDTO.setMessageId(messageId);
            addUserResDTO.setUserPersonalityId(userPersonalityId);

            // JSON 객체 생성
            String jsonMessage = objectMapper.writeValueAsString(addUserResDTO);

            // JSON 메시지를 Redis에 발행
            redisTemplate.convertAndSend(ADD_USER_RES_PUBLISH, jsonMessage);
            System.out.println("Published message: " + jsonMessage + " to topic: " + ADD_USER_RES_PUBLISH);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void addUserFailResPublish(Long userPersonalityId, String messageId) {
        try {
            AddUserResDTO addUserResDTO = new AddUserResDTO();
            addUserResDTO.setMessage(FAIL_TO_ADD_USER_PERSONALITY);
            addUserResDTO.setUserPersonalityId(userPersonalityId);
            addUserResDTO.setMessageId(messageId);

            // JSON 객체 생성
            String jsonMessage = objectMapper.writeValueAsString(addUserResDTO);

            // JSON 메시지를 Redis에 발행
            redisTemplate.convertAndSend(ADD_USER_RES_PUBLISH, jsonMessage);
            System.out.println("Published message: " + jsonMessage + " to topic: " + ADD_USER_RES_PUBLISH);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}