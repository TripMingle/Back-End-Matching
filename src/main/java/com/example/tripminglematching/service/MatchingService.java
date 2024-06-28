package com.example.tripminglematching.service;

import com.example.tripminglematching.entity.UserPersonality;
import com.example.tripminglematching.repository.UserPersonalityRepository;
import com.example.tripminglematching.utils.SimilarityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchingService {
    private final UserPersonalityRepository userPersonalityRepository;
    private final RedisTemplate<String, Object> redisTemplate;
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

                System.out.println("Stored JSON: " + json);

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
}
