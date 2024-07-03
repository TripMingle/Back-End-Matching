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
public class MatchingService {
    private final UserPersonalityRepository userPersonalityRepository;
    private final MessagePublisher messagePublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ALL_USER_PREFERENCES_KEY = "allUserPreferences";
    private static final String USER_PREFERENCES_KEY = "userPreferences-";


    @PostConstruct
    public void generateUserPreferences() {
        List<UserPersonality> users = userPersonalityRepository.findAll();
        Map<Long, List<Double>> userVectors = new HashMap<>();

        //유저의 특성을 userVectors에 저장
        users.forEach(userPersonality
                -> userVectors.put(userPersonality.getUser().getId(),userPersonality.toFeatureVector()));


        Map<Long,List<Pair<Long,Double>>> userPreferences = new HashMap<>();


        users.forEach(userPersonality -> {
            Long userId = userPersonality.getUser().getId();
            List<Double> userVector = userVectors.get(userId);
            Map<Long,Double> similarityMap = new HashMap<>();

            for (Map.Entry<Long, List<Double>> entry : userVectors.entrySet()) {
                Long otherUserId = entry.getKey();
                if (!otherUserId.equals(userId)) {
                    double similarity = SimilarityUtils.cosineSimilarity(userVector, entry.getValue());
                    similarityMap.put(otherUserId, similarity);
                }
            }

            List<Pair<Long, Double>> sortedPreferences = similarityMap.entrySet()
                    .stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .map(entry -> Pair.of(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            userPreferences.put(userId, sortedPreferences);

            List<Long> sortedPreferenceIds = sortedPreferences.stream()
                    .map(Pair::getLeft)
                    .collect(Collectors.toList());

            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(sortedPreferenceIds);
                redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + userId, json);

            } catch (Exception e) {
                e.printStackTrace();
            }

        });


        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(userPreferences);
            redisTemplate.opsForValue().set(ALL_USER_PREFERENCES_KEY, json);

            System.out.println("Stored JSON: " + json);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void printUserPreferences(Map<Long, List<Pair<Long, Double>>> userPreferences) {
        for (Map.Entry<Long, List<Pair<Long, Double>>> entry : userPreferences.entrySet()) {
            Long userId = entry.getKey();
            List<Pair<Long, Double>> preferences = entry.getValue();

            System.out.println("User ID: " + userId);
            for (Pair<Long, Double> preference : preferences) {
                System.out.println("    Other User ID: " + preference.getLeft() + ", Similarity: " + preference.getRight());
            }
        }
    }

    @Transactional
    public void addUser(Long userPersonalityId, String messageId) {
        UserPersonality nowUserPersonality = userPersonalityRepository.findById(userPersonalityId)
                .orElseThrow(() -> new UserPersonalityNotFound());
        List<Double> newUserVector = nowUserPersonality.toFeatureVector();
        Map<Long,List<Pair<Long,Double>>> userPreferences = new HashMap<>();
        List<Pair<Long, Double>> newUserPreferences = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            module.addSerializer((Class<Pair<Long, Double>>) (Class<?>) Pair.class, new PairSerializer());
            module.addDeserializer((Class<Pair<Long, Double>>) (Class<?>) Pair.class, new PairDeserializer());
            mapper.registerModule(module);

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

        userPreferences.entrySet().forEach(entry->{
            Long userId = entry.getKey();
            List<Pair<Long, Double>> preferences = entry.getValue();
            UserPersonality userPersonality = userPersonalityRepository.findById(userId)
                    .orElseThrow(() -> new UserPersonalityNotFound());
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
                ObjectMapper mapper = new ObjectMapper();
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

        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(sortedPreferenceIds);
            redisTemplate.opsForValue().set(USER_PREFERENCES_KEY + nowUserPersonality.getUser().getId(), json);
            System.out.println("Stored JSON: " + json);

        } catch (Exception e) {
            messagePublisher.addUserFailResPublish(userPersonalityId,messageId);
            e.printStackTrace();
        }


        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(userPreferences);
            redisTemplate.opsForValue().set(ALL_USER_PREFERENCES_KEY, json);
            messagePublisher.addUserResPublish(userPersonalityId,messageId);
            System.out.println("Stored JSON: " + json);
        } catch (Exception e) {
            messagePublisher.addUserFailResPublish(userPersonalityId,messageId);
            e.printStackTrace();
        }

    }

}
