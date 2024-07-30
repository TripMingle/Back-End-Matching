package com.example.tripminglematching.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Where(clause = "is_deleted = false")
public class Board extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String title;
    private String content;

    private String continent;
    private String countryName;

    private String imageUrl;

    //여행 타입 list
    private String tripType;

    private double preferGender; // 선호 성별
    private double preferSmoking; // 선호 흡연타입
    private double preferBudget; // 선호 활동 - 예산
    private double preferPhoto; // 선호 활동 - 사진
    private double preferDrink; // 선호 활동 - 음주

    //인원수
    private int currentCount;
    private int maxCount;

    private LocalDate startDate;
    private LocalDate endDate;

    private String language;

    private int commentCount;
    private int likeCount;
    private int bookMarkCount;

}
