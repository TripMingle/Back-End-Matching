package com.example.tripminglematching.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserPersonality {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private double gender; // 성별, 자동생성
    private double vegan; // 비건 여부
    private double islam; // 이슬람 신앙
    private double hindu; // 힌두 신앙
    private double smoking; // 흡연 여부
    private double budget; // 예산 범위
    private double accommodationFlexibility; // 숙소 선택 성향
    private double foodFlexibility; // 음식 선택 성향
    private double activity; // 선호 활동 - 액티비티
    private double photo; // 선호 활동 - 인스타사진
    private double foodExploration; // 선호 활동 - 맛집탐방
    private double adventure; // 선호 활동 - 탐험
    private double personality; // 성격
    private double schedule; // 일정 계획 성향
    private double drink; //음주 성향
    private double ageRange; // 나이대, 자동생성

    public List<Double> toFeatureVector() {
        return Arrays.asList(
            (gender-3.0) * 12.0,
            (vegan-3.0) * 12.0,
            (islam-3.0) * 12.0,
            (hindu-3.0) * 12.0,
            (smoking-3.0) * 8.0,
            (budget-3.0) * 10.0,
            (accommodationFlexibility-3.0) * 7.0,
            (foodFlexibility-3.0) * 7.0,
            (activity-3.0) * 9.0,
            (photo -3.0) * 9.0,
            (foodExploration-3.0) * 9.0,
            (adventure-3.0) * 9.0,
            (personality-3.0) * 9.0,
            (schedule-3.0) * 7.0,
            (drink-3.0) * 12.0,
            (ageRange-3.0) * 12.0
        );
    }

}
