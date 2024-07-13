package com.example.tripminglematching.repository;

import java.time.LocalDate;
import java.util.List;

import com.example.tripminglematching.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardRepository extends JpaRepository<Board,Long> {
	@Query("SELECT b FROM Board b WHERE b.countryName = :countryName AND b.startDate <= :endDate AND b.endDate >= :startDate")
	List<Board> findBoardsByCountryNameAndDateRange(
		@Param("countryName") String countryName,
		@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate
	);

	List<Board> findAllByCountryName(String countryName);

	List<Board> findBoardsByCountryName(String countryName);
}
