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
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final String MAX_USER_COUNT = "maxUserCount-"; //당시 가장 큰 id
    private static final String DELETED_BIT = "deletedBit-"; //이후 지워진게 있는지
    private static final String CURRENT_MAX_USER_COUNT = "currentMaxUserCount"; //현재 가장 큰 id
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
        AtomicLong maxUserPersonalityId = new AtomicLong(0);

        //유저의 특성을 userVectors에 저장
        users.forEach(userPersonality
                -> {userVectors.put(userPersonality.getId(),userPersonality.toFeatureVector());
                maxUserPersonalityId.updateAndGet(value -> Math.max(value, userPersonality.getId()));
        });

        users.forEach(userPersonality -> {
            Long userId = userPersonality.getUser().getId();
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
                Long max = maxUserPersonalityId.get();
                redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + userId, json);
                redisTemplate.opsForValue().set(MAX_USER_COUNT + userId, max);
                redisTemplate.opsForValue().set(DELETED_BIT + userId, 0);

                /*
                redisTemplate.execute((RedisCallback<Object>) connection -> {
                    connection.multi();
                    Long max = maxUserPersonalityId.get();
                    connection.set((USER_PREFERENCES_KEY + userId).getBytes(), json.getBytes());
                    connection.set((MAX_USER_COUNT + userId).getBytes(), Long.toString(max).getBytes());
                    connection.set((DELETED_BIT + userId).getBytes(), "0".getBytes());
                    connection.exec();
                    return null;
                });
                */

            } catch (Exception e) {
                e.printStackTrace();
            }

        });

        redisTemplate.opsForValue().set(CURRENT_MAX_USER_COUNT, maxUserPersonalityId.get());

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
                List<Double> userVector = userPersonality.toFeatureVector();
                double similarity = SimilarityUtils.cosineSimilarity(newUserVector, userVector);
                return Pair.of(userPersonality.getId(), similarity);
        })
            .sorted((p1, p2) -> Double.compare(p2.getRight(), p1.getRight()))
            .limit(MAX_SIZE)
            .collect(Collectors.toList());

        try{
            String json = mapper.writeValueAsString(newUserPreferences);

            redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + newUserPersonality.getUser().getId(), json);
            redisTemplate.opsForValue().set(MAX_USER_COUNT + newUserPersonality.getUser().getId(), newUserPersonality.getId());
            redisTemplate.opsForValue().set(DELETED_BIT + newUserPersonality.getUser().getId(), 0);

            Object currentMaxString =  redisTemplate.opsForValue().get(CURRENT_MAX_USER_COUNT);
            if (currentMaxString != null) {
                Long currentMax = Long.parseLong(currentMaxString.toString());
                Long newMax = Math.max(currentMax, newUserPersonality.getId());
                redisTemplate.opsForValue().set(CURRENT_MAX_USER_COUNT, newMax);
            } else {
                redisTemplate.opsForValue().set(CURRENT_MAX_USER_COUNT, newUserPersonality.getId());
            }




            /*
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.multi();
                connection.set((USER_PREFERENCES_KEY + newUserPersonality.getUser().getId()).getBytes(), json.getBytes());
                connection.set((MAX_USER_COUNT + newUserPersonality.getUser().getId()).getBytes(), String.valueOf(newUserPersonality.getId()).getBytes());
                connection.set((DELETED_BIT + newUserPersonality.getUser().getId()).getBytes(), "0".getBytes());
                byte[] currentMaxBytes = connection.get(CURRENT_MAX_USER_COUNT.getBytes());
                if (currentMaxBytes != null) {
                    Long currentMax = Long.parseLong(new String(currentMaxBytes));
                    Long newMax = Math.max(currentMax, newUserPersonality.getId());
                    connection.set(CURRENT_MAX_USER_COUNT.getBytes(), newMax.toString().getBytes());
                } else {
                    connection.set(CURRENT_MAX_USER_COUNT.getBytes(), newUserPersonality.getId().toString().getBytes());
                }
                connection.exec();
                return null;
            });

             */


            messagePublisher.userPersonalityResPublish(userPersonalityId,messageId, MessagePublisher.TOPIC_ADD_USER_RES_PUBLISH,MessagePublisher.ADD_USER_PERSONALITY_SUCCESS);
        }
        catch (Exception e){
            messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_ADD_USER_RES_PUBLISH, MessagePublisher.FAIL_TO_ADD_USER_PERSONALITY);
            e.printStackTrace();
        }

    }


    //모두 재계산 (deleted 활성화시)
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
        AtomicLong maxUserPersonalityId = new AtomicLong(nowUserPersonality.getId());

        List<Pair<Long, Double>> nowUserPreferences = userPersonalityList.stream()
            .filter(userPersonality -> !(userPersonality.getId().equals(nowUserPersonality.getId())))
            .map(userPersonality -> {
                maxUserPersonalityId.updateAndGet(value -> Math.max(value, userPersonality.getId()));
                List<Double> userVector = userPersonality.toFeatureVector();
                double similarity = SimilarityUtils.cosineSimilarity(nowUserVector, userVector);
                return Pair.of(userPersonality.getId(), similarity);
            })
            .sorted((p1, p2) -> Double.compare(p2.getRight(), p1.getRight()))
            .limit(MAX_SIZE)
            .collect(Collectors.toList());

        try{
            String json = mapper.writeValueAsString(nowUserPreferences);

            redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + nowUserPersonality.getUser().getId(), json);
            redisTemplate.opsForValue().set(MAX_USER_COUNT + nowUserPersonality.getUser().getId(), nowUserPersonality.getId());
            redisTemplate.opsForValue().set(DELETED_BIT + nowUserPersonality.getUser().getId(), 0);

            // 트랜잭션 외부에서 CURRENT_MAX_USER_COUNT 값을 읽고 업데이트
            Object currentMaxString = redisTemplate.opsForValue().get(CURRENT_MAX_USER_COUNT);
            if (currentMaxString != null) {
                Long currentMax = Long.parseLong(currentMaxString.toString());
                Long newMax = Math.max(currentMax, maxUserPersonalityId.get());
                redisTemplate.opsForValue().set(CURRENT_MAX_USER_COUNT, newMax);
            } else {
                redisTemplate.opsForValue().set(CURRENT_MAX_USER_COUNT, maxUserPersonalityId.get());
            }

            /*

            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.multi();
                connection.set((USER_PREFERENCES_KEY + nowUserPersonal.getId()).getBytes(), json.getBytes());
                connection.set((MAX_USER_COUNT + nowUserPersonality.getUser().getId()).getBytes(), String.valueOf(nowUserPersonality.getId()).getBytes());
                connection.set((DELETED_BIT + nowUserPersonality.getUser().getId()).getBytes(), "0".getBytes());
                connection.exec();
                byte[] currentMaxBytes = connection.get(CURRENT_MAX_USER_COUNT.getBytes());
                if (currentMaxBytes != null) {
                    Long currentMax = Long.parseLong(new String(currentMaxBytes));
                    Long newMax = Math.max(currentMax, maxUserPersonalityId.get());
                    connection.set(CURRENT_MAX_USER_COUNT.getBytes(), newMax.toString().getBytes());
                } else {
                    connection.set(CURRENT_MAX_USER_COUNT.getBytes(), Long.toString(maxUserPersonalityId.get()).getBytes());
                }
                return null;
            });

             */

            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.multi();
                connection.set(
                    redisTemplate.getStringSerializer().serialize(USER_PREFERENCES_KEY + nowUserPersonality.getUser().getId()),
                    redisTemplate.getStringSerializer().serialize(json)
                );
                connection.set(
                    redisTemplate.getStringSerializer().serialize(MAX_USER_COUNT + nowUserPersonality.getUser().getId()),
                    redisTemplate.getStringSerializer().serialize(String.valueOf(nowUserPersonality.getId()))
                );
                connection.set(
                    redisTemplate.getStringSerializer().serialize(DELETED_BIT + nowUserPersonality.getUser().getId()),
                    redisTemplate.getStringSerializer().serialize("0")
                );
                connection.exec();

                byte[] currentMaxBytes = connection.get(redisTemplate.getStringSerializer().serialize(CURRENT_MAX_USER_COUNT));
                if (currentMaxBytes != null) {
                    Long currentMax = Long.parseLong(redisTemplate.getStringSerializer().deserialize(currentMaxBytes));
                    Long newMax = Math.max(currentMax, maxUserPersonalityId.get());
                    connection.set(
                        redisTemplate.getStringSerializer().serialize(CURRENT_MAX_USER_COUNT),
                        redisTemplate.getStringSerializer().serialize(newMax.toString())
                    );
                } else {
                    connection.set(
                        redisTemplate.getStringSerializer().serialize(CURRENT_MAX_USER_COUNT),
                        redisTemplate.getStringSerializer().serialize(maxUserPersonalityId.toString())
                    );
                }
                return null;
            });

            messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_RE_CALCULATE_USER_RES_PUBLISH, MessagePublisher.RE_CALCULATE_USER_PERSONALITY_SUCCESS);
        }
        catch (Exception e){
            messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_RE_CALCULATE_USER_RES_PUBLISH, MessagePublisher.FAIL_TO_RE_CALCULATE_USER_PERSONALITY);
            e.printStackTrace();
        }

    }


    //일부 재계산 (max_user_count < current_max_user_count)
    public void recalculateUserPartialUserPersonality(Long userPersonalityId, Long minUserPersonalityId, String messageId){
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
        AtomicLong maxUserPersonalityId = new AtomicLong(nowUserPersonality.getId());

        List<Pair<Long, Double>> nowUserPreferences = new ArrayList<>();
        try {
            String json = (String) redisTemplate.opsForValue().get(USER_PREFERENCES_KEY + nowUserPersonality.getUser().getId());
            if (json != null) {
                nowUserPreferences = mapper.readValue(json, new TypeReference<List<Pair<Long, Double>>>() {});
            } else {
                messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_RE_CALCULATE_USER_RES_PUBLISH, MessagePublisher.FAIL_TO_RE_CALCULATE_USER_PERSONALITY);
                throw new UserPersonalityNotFound();
            }
        } catch (Exception e) {
            e.printStackTrace();
            messagePublisher.userPersonalityResPublish(userPersonalityId, messageId, MessagePublisher.TOPIC_RE_CALCULATE_USER_RES_PUBLISH, MessagePublisher.FAIL_TO_RE_CALCULATE_USER_PERSONALITY);
            throw new RuntimeException();
        }

        List<UserPersonality>userPersonalityList = userPersonalityRepository.findByIdGreaterThan(minUserPersonalityId);

        List<Pair<Long, Double>> newPreferences = userPersonalityList.stream()
            .filter(userPersonality -> !userPersonality.getId().equals(nowUserPersonality.getId()))
            .map(userPersonality -> {
                maxUserPersonalityId.updateAndGet(value -> Math.max(value, userPersonality.getId()));
                List<Double> userVector = userPersonality.toFeatureVector();
                double similarity = SimilarityUtils.cosineSimilarity(nowUserVector, userVector);
                return Pair.of(userPersonality.getId(), similarity);
            })
            .collect(Collectors.toList());

        nowUserPreferences.addAll(newPreferences);

        nowUserPreferences = nowUserPreferences.stream()
            .sorted((p1, p2) -> Double.compare(p2.getRight(), p1.getRight())) // 유사도 기준 내림차순 정렬
            .limit(MAX_SIZE) // 상위 MAX_SIZE 개수로 제한
            .collect(Collectors.toList());

        try{
            String json = mapper.writeValueAsString(nowUserPreferences);

            redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + nowUserPersonality.getUser().getId(), json);
            redisTemplate.opsForValue().set(MAX_USER_COUNT + nowUserPersonality.getUser().getId(), nowUserPersonality.getId());
            redisTemplate.opsForValue().set(DELETED_BIT + nowUserPersonality.getUser().getId(), 0);

            // CURRENT_MAX_USER_COUNT 값을 읽고 업데이트
            Object currentMaxString = redisTemplate.opsForValue().get(CURRENT_MAX_USER_COUNT);
            if (currentMaxString != null) {
                Long currentMax = Long.parseLong(currentMaxString.toString());
                Long newMax = Math.max(currentMax, maxUserPersonalityId.get());
                redisTemplate.opsForValue().set(CURRENT_MAX_USER_COUNT, newMax);
            } else {
                redisTemplate.opsForValue().set(CURRENT_MAX_USER_COUNT, maxUserPersonalityId.get());
            }

            /*
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.multi();
                connection.set((USER_PREFERENCES_KEY + nowUserPersonality.getUser().getId()).getBytes(), json.getBytes());
                connection.set((MAX_USER_COUNT + nowUserPersonality.getUser().getId()).getBytes(), String.valueOf(nowUserPersonality.getId()).getBytes());
                connection.set((DELETED_BIT + nowUserPersonality.getUser().getId()).getBytes(), "0".getBytes());
                connection.exec();
                byte[] currentMaxBytes = connection.get(CURRENT_MAX_USER_COUNT.getBytes());
                if (currentMaxBytes != null) {
                    Long currentMax = Long.parseLong(new String(currentMaxBytes));
                    Long newMax = Math.max(currentMax, maxUserPersonalityId.get());
                    connection.set(CURRENT_MAX_USER_COUNT.getBytes(), newMax.toString().getBytes());
                } else {
                    connection.set(CURRENT_MAX_USER_COUNT.getBytes(), maxUserPersonalityId.toString().getBytes());
                }
                return null;
            });

             */
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


    /*
    @Transactional
    public void exAddUser(Long userPersonalityId, String messageId) {
        UserPersonality nowUserPersonality;
        try {
            nowUserPersonality = userPersonalityRepository.findById(userPersonalityId)
                .orElseThrow(() -> new UserPersonalityNotFound());
        }catch (Exception e){
            System.out.println("exception!!!!!!!" + e);
            messagePublisher.addUserFailResPublish(userPersonalityId, messageId);
            e.printStackTrace();
            return;
        }
        List<Double> newUserVector = nowUserPersonality.toFeatureVector();

        Map<Long,List<Pair<Long,Double>>> userPreferences = new HashMap<>();
        List<Pair<Long, Double>> newUserPreferences = new ArrayList<>();
        try {


            String json = (String) redisTemplate.opsForValue().get(ALL_USER_PREFERENCES_KEY);

            if (json != null) {
                userPreferences = mapper.readValue(json, new TypeReference<Map<Long, List<Pair<Long, Double>>>>() {});
            } else {
                messagePublisher.addUserFailResPublish(userPersonalityId, messageId);
                throw new UserPersonalityNotFound();
            }

        } catch (Exception e) {
            e.printStackTrace();
            messagePublisher.addUserFailResPublish(userPersonalityId, messageId);
            throw new RuntimeException();
        }

        final AtomicBoolean flag = new AtomicBoolean(false);

        userPreferences.entrySet().forEach(entry->{
            if(flag.get())return;
            Long userId = entry.getKey();
            List<Pair<Long, Double>> preferences = entry.getValue();
            UserPersonality userPersonality;
            try {
                userPersonality = userPersonalityRepository.findById(userId)
                    .orElseThrow(() -> new UserPersonalityNotFound());
            }catch (Exception e){
                System.out.println("exception!!!!!!!" + e);
                messagePublisher.addUserFailResPublish(userPersonalityId, messageId);
                flag.set(true);
                e.printStackTrace();
                return;
            }

            List<Double> userVector = userPersonality.toFeatureVector();
            double similarity = SimilarityUtils.cosineSimilarity(userVector,newUserVector);
            //기존 유저들의 선호도 배열에 새 유저와의 유사도 넣고 정렬
            preferences.add(Pair.of(nowUserPersonality.getUser().getId(), similarity));
            preferences.sort((p1, p2) -> Double.compare(p2.getRight(), p1.getRight()));

            //redis에 캐싱
            List<Long> sortedPreferenceIds = preferences.stream()
                .map(Pair::getLeft)
                .collect(Collectors.toList());
            try {
                //ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(sortedPreferenceIds);
                redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + userId, json);
                System.out.println("Stored JSON: " + json);

            } catch (Exception e) {
                messagePublisher.addUserFailResPublish(userPersonalityId,messageId);
                e.printStackTrace();
            }

            //새 유저의 배열에 추가
            newUserPreferences.add(Pair.of(userId, similarity));
        });

        //새 유저의 선호도 배열 정렬
        newUserPreferences.sort((p1, p2) -> Double.compare(p2.getRight(), p1.getRight()));

        //새 유저의 선호도 배열을 userPreferences에 추가
        userPreferences.put(nowUserPersonality.getUser().getId(), newUserPreferences);

        List<Long> sortedPreferenceIds = newUserPreferences.stream()
            .map(Pair::getLeft)
            .collect(Collectors.toList());
        if(flag.get())return;
        try {
            //ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(sortedPreferenceIds);
            redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + nowUserPersonality.getUser().getId(), json);
            System.out.println("Stored JSON: " + json);

        } catch (Exception e) {
            messagePublisher.addUserFailResPublish(userPersonalityId,messageId);
            e.printStackTrace();
        }


        try {
            //ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(userPreferences);
            redisTemplate.opsForValue().set(ALL_USER_PREFERENCES_KEY, json);
            messagePublisher.userPersonalityResPublish(userPersonalityId,messageId);
            System.out.println("Stored JSON: " + json);
        } catch (Exception e) {
            messagePublisher.addUserFailResPublish(userPersonalityId,messageId);
            e.printStackTrace();
        }

    }
    */
}
