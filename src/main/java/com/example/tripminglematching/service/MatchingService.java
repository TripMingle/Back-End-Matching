package com.example.tripminglematching.service;

import com.example.tripminglematching.entity.Board;
import com.example.tripminglematching.entity.User;
import com.example.tripminglematching.entity.UserPersonality;
import com.example.tripminglematching.exception.UserPersonalityNotFound;
import com.example.tripminglematching.repository.BoardRepository;
import com.example.tripminglematching.repository.UserPersonalityRepository;
import com.example.tripminglematching.repository.UserRepository;
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

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchingService {
    private final UserPersonalityRepository userPersonalityRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final MessagePublisher messagePublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String USER_PREFERENCES_KEY = "userPreferences-"; // 선호도배열
    private static final String DELETED_BIT = "deletedBit-"; //이후 지워진게 있는지
    private static final Integer MAX_SIZE = 50;
    private final Integer MAX_RECOMMENDATIONS = 10;

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

    public void matchUserAndBoard(Long userId, String messageId, String countryName, LocalDate startDate, LocalDate endDate){
        //조건으로 게시물 조회
        List<Board> boards = boardRepository.findBoardsByCountryNameAndDateRange(countryName,startDate,endDate);

        UserPersonality myUserPersonality = userPersonalityRepository.findByUserId(userId);
        userId = myUserPersonality.getId();

        //게시물마다 유저성향을 조회한 뒤 유저와 함쳐 성향벡터 만들기
        Map<Long, Pair<Long, List<Double>>> boardPreferVector = boards.stream()
            .collect(Collectors.toMap(
                Board::getId,
                board -> Pair.of(board.getUser().getId(), calculateBoardVector(board, messageId))
            ));

        //모든유저 조회
        List<UserPersonality> userPersonalities = userPersonalityRepository.findAll();
        Map<Long, Pair<Long, List<Double>>> userPreferVector = userPersonalities.stream()
            .collect(Collectors.toMap(
                userPersonality -> userPersonality.getId(),
                userPersonality -> Pair.of(userPersonality.getUser().getId(), userPersonality.toFeatureVector())
            ));

        //양측 유저의 선호도 배열 제작
        Map<Long, Queue<Long>> userPreferQueue = userPersonalities.stream()
            .collect(Collectors.toMap(
                userPersonality -> userPersonality.getId(),
                userPersonality -> {
                    Long nowUserId = userPersonality.getId();
                    List<Double> userVector = userPreferVector.get(nowUserId).getRight();

                    // 미리 유사도를 계산해서 저장
                    Map<Long, Double> similarityMap = boardPreferVector.entrySet().stream()
                        .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> {
                                Long boardAuthor = entry.getValue().getLeft();
                                return boardAuthor.equals(nowUserId) ? -100.0 :
                                    SimilarityUtils.cosineSimilarity(userVector, entry.getValue().getRight());
                            }
                        ));

                    return similarityMap.entrySet().stream()
                        .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toCollection(LinkedList::new));
                }
            ));

        // 게시물의 선호도 리스트 생성
        Map<Long, Map<Long, Integer>> boardPreferList = boards.stream()
            .collect(Collectors.toMap(
                Board::getId,
                board -> {
                    List<Double> boardVector = boardPreferVector.get(board.getId()).getRight();
                    Long boardAuthor = board.getUser().getId();

                    // 미리 유사도를 계산해서 저장
                    Map<Long, Double> similarityMap = userPreferVector.entrySet().stream()
                        .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> {
                                Long nowUserId = entry.getKey();
                                return boardAuthor.equals(nowUserId) ? -100.0 :
                                    SimilarityUtils.cosineSimilarity(boardVector, entry.getValue().getRight());
                            }
                        ));

                    List<Long> sortedUsers = similarityMap.entrySet().stream()
                        .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

                    // 유저 ID를 키로, 인덱스를 값으로 하는 맵 생성
                    Map<Long, Integer> userIndexMap = new HashMap<>();
                    for (int i = 0; i < sortedUsers.size(); i++) {
                        userIndexMap.put(sortedUsers.get(i), i);
                    }
                    return userIndexMap;
                }
            ));

        // 매칭과 프로포즈 초기화
        Map<Long, List<Long>> userMatches = new HashMap<>();
        Map<Long, PriorityQueue<Map.Entry<Integer, Long>>> boardMatches = new HashMap<>(); // boardID -> PriorityQueue of (preferenceIndex, userID)
        Queue<Long> freeUsers = new LinkedList<>(userPersonalities.stream().map(UserPersonality::getId).collect(Collectors.toList()));

        int numUsers = userPersonalities.size();
        int numBoards = boards.size();
        int userMatchingCount = Math.min(5, numBoards);
        if(numBoards==0)numBoards++;
        int maxMatchesPerBoard = (numUsers * userMatchingCount)%numBoards == 0
            ? (numUsers * userMatchingCount)/numBoards : (numUsers * userMatchingCount)/numBoards + 1;


        // 게일-섀플리 알고리즘
        while (!freeUsers.isEmpty()) {
            Long freeUserId = freeUsers.poll();
            Queue<Long> userPreferences = userPreferQueue.getOrDefault(freeUserId, new LinkedList<>());

            userMatches.putIfAbsent(freeUserId, new ArrayList<>());

            while (!userPreferences.isEmpty() && userMatches.get(freeUserId).size() < userMatchingCount) {
                Long preferredBoardId = userPreferences.poll();

                // 현재 매칭된 유저 리스트를 가져옴 (없으면 새로운 PriorityQueue 생성)
                PriorityQueue<Map.Entry<Integer, Long>> currentMatches = boardMatches
                    .computeIfAbsent(preferredBoardId, k -> new PriorityQueue<>((entry1, entry2) -> Integer.compare(entry2.getKey(), entry1.getKey())));

                // 유저의 현재 보드에 대한 선호도 인덱스 가져오기
                int userPreferenceIndex = boardPreferList.get(preferredBoardId).get(freeUserId);

                if (currentMatches.size() < maxMatchesPerBoard) {
                    // 보드에 공간이 있으면 유저 매칭
                    currentMatches.add(new AbstractMap.SimpleEntry<>(userPreferenceIndex, freeUserId));
                    boardMatches.put(preferredBoardId, currentMatches);
                    userMatches.get(freeUserId).add(preferredBoardId);
                    break;
                } else {
                    // 보드가 가득 찬 경우, 현재 매칭된 유저 중 덜 선호되는 유저와 비교
                    Map.Entry<Integer, Long> leastPreferredUser = currentMatches.peek();

                    if (userPreferenceIndex < leastPreferredUser.getKey()) {
                        // 새로운 유저가 더 선호되는 경우, 덜 선호되는 유저 교체
                        currentMatches.poll(); // 덜 선호되는 유저 제거
                        currentMatches.add(new AbstractMap.SimpleEntry<>(userPreferenceIndex, freeUserId));
                        boardMatches.put(preferredBoardId, currentMatches);
                        userMatches.get(leastPreferredUser.getValue()).remove(preferredBoardId);
                        userMatches.get(freeUserId).add(preferredBoardId);
                        freeUsers.add(leastPreferredUser.getValue()); // 교체된 유저는 다시 자유 유저가 됨
                        break;
                    }
                }

            }

            if (userMatches.get(freeUserId).size() < userMatchingCount && !userPreferences.isEmpty()) {
                freeUsers.add(freeUserId);
            }
        }

        for (Map.Entry<Long, List<Long>> entry : userMatches.entrySet()) {
            Long userId2 = entry.getKey();
            List<Long> boardIds = entry.getValue();
        }

        for (Map.Entry<Long, List<Long>> entry : userMatches.entrySet()) {
            Long userId2 = entry.getKey();
            List<Long> boardIds = entry.getValue();
            System.out.println("UserID: " + userId2 + ", BoardIDs: " + boardIds);
        }

        //userId를 통해 특정 유저의 게시물선호도배열 publish
        messagePublisher.matchingResPublish(userMatches.get(userId), messageId, MessagePublisher.TOPIC_MATCHING ,MessagePublisher.MATCHING_SUCCESS);
    }

    private List<Double> calculateBoardVector (Board board, String messageId){
        Optional<UserPersonality> userPersonality = userPersonalityRepository.findByUser(board.getUser());
        if (!userPersonality.isPresent()) {
            messagePublisher.matchingResPublish(null, messageId, MessagePublisher.TOPIC_MATCHING ,MessagePublisher.FAIL_TO_MATCHING);
            throw new UserPersonalityNotFound();
        }
        return boardPrefer(userPersonality.get().toFeatureVector(), board);
    }

    private List<Double> boardPrefer(List<Double> userVector, Board board){
        userVector.set(0, userVector.get(0) + (board.getPreferGender()-3.0) * 24.0);
        userVector.set(4, userVector.get(4) + (board.getPreferSmoking()-3.0) * 16.0);
        userVector.set(9,  userVector.get(9) + (board.getPreferInstagramPicture()-3.0) * 9.0);
        userVector.set(14,  userVector.get(14) + (board.getPreferShopping()-3.0) * 10.0);
        userVector.set(15,  userVector.get(15) + (board.getPreferDrink()-3.0) * 24.0);
        return userVector;
    }

}
