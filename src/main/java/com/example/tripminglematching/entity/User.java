package com.example.tripminglematching.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String loginType;

    @Column(nullable = false)
    private String oauthId;

    @Column(nullable = false, unique = true)
    private String nickName;

    @Column(nullable = false)
    private String ageRange;

    @Column(nullable = false)
    private String gender;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String nationality;

    @Column(nullable = true)
    private String selfIntroduction;

    @Column(nullable = false, unique = true)
    private String phoneNumber;

    @Builder(toBuilder = true)
    public User(String email, String password, String role, String loginType, String oauthId, String nickName, String ageRange, String gender, String name, String nationality, String selfIntroduction, String phoneNumber) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.loginType = loginType;
        this.oauthId = oauthId;
        this.nickName = nickName;
        this.ageRange = ageRange;
        this.gender = gender;
        this.name = name;
        this.nationality = nationality;
        this.selfIntroduction = selfIntroduction;
        this.phoneNumber = phoneNumber;
    }

}
