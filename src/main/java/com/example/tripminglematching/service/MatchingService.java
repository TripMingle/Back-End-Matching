package com.example.tripminglematching.service;

import com.example.tripminglematching.entity.UserPersonality;
import com.example.tripminglematching.exception.UserPersonalityNotFound;
import com.example.tripminglematching.repository.UserPersonalityRepository;
import com.example.tripminglematching.utils.PairDeserializer;
import com.example.tripminglematching.utils.PairSerializer;
import com.example.tripminglematching.utils.SimilarityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchingService {
    private final UserPersonalityRepository userPersonalityRepository;
    private final MessagePublisher messagePublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String USER_PREFERENCES_KEY = "userPreferences-"; // 선호도배열
    private static final String DELETED_BIT = "deletedBit-"; //이후 지워진게 있는지
    private static final Integer MAX_SIZE = 50;

    @PostConstruct
    void init(){
        SimpleModule module = new SimpleModule();
        module.addSerializer((Class<Pair<Long, Double>>) (Class<?>) Pair.class, new PairSerializer());
        module.addDeserializer((Class<Pair<Long, Double>>) (Class<?>) Pair.class, new PairDeserializer());
        mapper.registerModule(module);

        generateUserPreferences();
    }

    public void generateUserPreferences() {
        List<UserPersonality> users = userPersonalityRepository.findAll();
        Map<Long, List<Double>> userVectors = new HashMap<>();

        //유저의 특성을 userVectors에 저장
        users.forEach(userPersonality
                -> {userVectors.put(userPersonality.getId(),userPersonality.toFeatureVector());
        });

        users.forEach(userPersonality -> {
            List<Double> userVector = userVectors.get(userPersonality.getId());
            Map<Long,Double> similarityMap = new HashMap<>();

            for (Map.Entry<Long, List<Double>> entry : userVectors.entrySet()) {
                Long otherUserPersonalityId = entry.getKey();
                if (!otherUserPersonalityId.equals(userPersonality.getId())) {
                    double similarity = SimilarityUtils.cosineSimilarity(userVector, entry.getValue());
                    similarityMap.put(otherUserPersonalityId, similarity);
                }
            }

            List<Pair<Long, Double>> sortedPreferences = similarityMap.entrySet()
                    .stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(MAX_SIZE)
                    .map(entry -> Pair.of(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(sortedPreferences);
                redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + userPersonality.getId(), json);
                redisTemplate.opsForValue().set(DELETED_BIT + userPersonality.getId(), 0);
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }

    //유저추가
    public void addUserPersonality(Long userPersonalityId, String messageId) {
        UserPersonality newUserPersonality;
        try {
            newUserPersonality = userPersonalityRepository.findById(userPersonalityId)
                    .orElseThrow(() -> new UserPersonalityNotFound());
        }catch (Exception e){
            messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_ADD_USER_RES_PUBLISH, MessagePublisher.FAIL_TO_ADD_USER_PERSONALITY);
            e.printStackTrace();
            return;
        }

        List<Double> newUserVector = newUserPersonality.toFeatureVector();
        List<UserPersonality>userPersonalityList = userPersonalityRepository.findAll();
        List<Pair<Long, Double>> newUserPreferences = userPersonalityList.stream()
            .filter(userPersonality -> !(userPersonality.getId().equals(newUserPersonality.getId())))
            .map(userPersonality -> {
                List<Pair<Long, Double>> existingUserPreferences = new ArrayList<>();
                try {
                    String json = (String)redisTemplate.opsForValue()
                        .get(USER_PREFERENCES_KEY + userPersonality.getId());
                    existingUserPreferences = mapper.readValue(json, new TypeReference<List<Pair<Long, Double>>>() {});
                }
                catch (Exception e){
                    messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_ADD_USER_RES_PUBLISH, MessagePublisher.FAIL_TO_ADD_USER_PERSONALITY);
                    e.printStackTrace();
                }

                List<Double> userVector = userPersonality.toFeatureVector();
                double similarity = SimilarityUtils.cosineSimilarity(newUserVector, userVector);
                existingUserPreferences.add(Pair.of(newUserPersonality.getId(), similarity));
                existingUserPreferences = existingUserPreferences.stream()
                    .sorted((p1, p2) -> Double.compare(p2.getRight(), p1.getRight()))
                    .limit(MAX_SIZE)
                    .collect(Collectors.toList());

                try{
                    String json = mapper.writeValueAsString(existingUserPreferences);
                    redisTemplate.opsForValue().set(USER_PREFERENCES_KEY+userPersonality.getId(), json);
                } catch (Exception e){
                    messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_ADD_USER_RES_PUBLISH, MessagePublisher.FAIL_TO_ADD_USER_PERSONALITY);
                    e.printStackTrace();
                }

                return Pair.of(userPersonality.getId(), similarity);
        })
            .sorted((p1, p2) -> Double.compare(p2.getRight(), p1.getRight()))
            .limit(MAX_SIZE)
            .collect(Collectors.toList());

        try{
            String json = mapper.writeValueAsString(newUserPreferences);

            redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + newUserPersonality.getId(), json);
            redisTemplate.opsForValue().set(DELETED_BIT + newUserPersonality.getId(), 0);
            messagePublisher.userPersonalityResPublish(userPersonalityId,messageId, MessagePublisher.TOPIC_ADD_USER_RES_PUBLISH,MessagePublisher.ADD_USER_PERSONALITY_SUCCESS);
        }
        catch (Exception e){
            messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_ADD_USER_RES_PUBLISH, MessagePublisher.FAIL_TO_ADD_USER_PERSONALITY);
            e.printStackTrace();
        }

    }


    //재계산
    public void recalculateUserPersonality(Long userPersonalityId, String messageId){
        UserPersonality nowUserPersonality;
        try {
            nowUserPersonality = userPersonalityRepository.findById(userPersonalityId)
                .orElseThrow(() -> new UserPersonalityNotFound());
        }catch (Exception e){
            messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_RE_CALCULATE_USER_RES_PUBLISH, MessagePublisher.FAIL_TO_RE_CALCULATE_USER_PERSONALITY);
            e.printStackTrace();
            return;
        }
        List<Double> nowUserVector = nowUserPersonality.toFeatureVector();
        List<UserPersonality>userPersonalityList = userPersonalityRepository.findAll();

        List<Pair<Long, Double>> nowUserPreferences = userPersonalityList.stream()
            .filter(userPersonality -> !(userPersonality.getId().equals(nowUserPersonality.getId())))
            .map(userPersonality -> {
                List<Double> userVector = userPersonality.toFeatureVector();
                double similarity = SimilarityUtils.cosineSimilarity(nowUserVector, userVector);
                return Pair.of(userPersonality.getId(), similarity);
            })
            .sorted((p1, p2) -> Double.compare(p2.getRight(), p1.getRight()))
            .limit(MAX_SIZE)
            .collect(Collectors.toList());

        try{
            String json = mapper.writeValueAsString(nowUserPreferences);

            redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + nowUserPersonality.getId(), json);
            redisTemplate.opsForValue().set(DELETED_BIT + nowUserPersonality.getId(), 0);

            messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_RE_CALCULATE_USER_RES_PUBLISH, MessagePublisher.RE_CALCULATE_USER_PERSONALITY_SUCCESS);
        }
        catch (Exception e){
            messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_RE_CALCULATE_USER_RES_PUBLISH, MessagePublisher.FAIL_TO_RE_CALCULATE_USER_PERSONALITY);
            e.printStackTrace();
        }

    }



    //유저 삭제
    public void deleteUserPersonality(Long userId, String messageId){
        redisTemplate.delete(USER_PREFERENCES_KEY + userId);
        List<UserPersonality>userPersonalityList = userPersonalityRepository.findAll();
        userPersonalityList.forEach(userPersonality ->
            redisTemplate.opsForValue().set(DELETED_BIT+userPersonality.getId(),1)
        );
        messagePublisher.userPersonalityResPublish(userId,messageId,MessagePublisher.TOPIC_DELETE_USER_RES_PUBLISH, MessagePublisher.DELETE_USER_PERSONALITY_SUCCESS);
    }

}
