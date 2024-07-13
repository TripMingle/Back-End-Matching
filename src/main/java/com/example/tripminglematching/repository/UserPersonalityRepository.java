package com.example.tripminglematching.repository;

import com.example.tripminglematching.entity.Board;
import com.example.tripminglematching.entity.User;
import com.example.tripminglematching.entity.UserPersonality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPersonalityRepository extends JpaRepository<UserPersonality,Long> {
    List<UserPersonality> findAll();

    List<UserPersonality> findByIdGreaterThan(Long minUserPersonalityId);

    Optional<UserPersonality> findByUser(User user);

    UserPersonality findByUserId(Long userId);
}
