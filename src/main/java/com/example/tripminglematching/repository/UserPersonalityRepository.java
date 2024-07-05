package com.example.tripminglematching.repository;

import com.example.tripminglematching.entity.UserPersonality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPersonalityRepository extends JpaRepository<UserPersonality,Long> {
    List<UserPersonality> findAll();

    List<UserPersonality> findByIdGreaterThan(Long minUserPersonalityId);
}
