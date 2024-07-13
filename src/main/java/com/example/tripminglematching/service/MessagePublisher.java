package com.example.tripminglematching.service;

import java.util.List;

import com.example.tripminglematching.dto.MatchingResDTO;
import com.example.tripminglematching.dto.UserPersonalityResDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class MessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    //topic
    public static final String TOPIC_ADD_USER_RES_PUBLISH = "pubsub:addUserRes";
    public static final String TOPIC_RE_CALCULATE_USER_RES_PUBLISH = "pubsub:reCalculateUserRes";
    public static final String TOPIC_DELETE_USER_RES_PUBLISH = "pubsub:deleteUserRes";
    public static final String TOPIC_MATCHING = "pubsub:matchingRes";


    //message
    public static final String ADD_USER_PERSONALITY_SUCCESS = "add user personality success";
    public static final String FAIL_TO_ADD_USER_PERSONALITY = "fail to add user personality";
    public static final String RE_CALCULATE_USER_PERSONALITY_SUCCESS = "recalculate user personality success";
    public static final String FAIL_TO_RE_CALCULATE_USER_PERSONALITY = "fail to recalculate user personality";
    public static final String DELETE_USER_PERSONALITY_SUCCESS = "delete user personality success";
    public static final String FAIL_TO_DELETE_USER_PERSONALITY = "fail to delete user personality";
    public static final String MATCHING_SUCCESS = "matching success";
    public static final String FAIL_TO_MATCHING = "fail to matching";

    @PostConstruct
    private void init() {
        objectMapper.registerModule(new JavaTimeModule());
    }


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

    public void matchingResPublish(List<Long> result, String messageId, String channel, String message){
        try {
            MatchingResDTO matchingResDTO = new MatchingResDTO();
            matchingResDTO.setBoardId(result);
            matchingResDTO.setMessageId(messageId);
            matchingResDTO.setMessage(message);

            // JSON 객체 생성
            String jsonMessage = objectMapper.writeValueAsString(matchingResDTO);

            // JSON 메시지를 Redis에 발행
            redisTemplate.convertAndSend(channel, jsonMessage);
            System.out.println("Published message: " + jsonMessage + " to topic: " + channel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}