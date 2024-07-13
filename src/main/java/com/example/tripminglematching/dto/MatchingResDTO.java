package com.example.tripminglematching.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MatchingResDTO {
	private String message;
	private List<Long> boardId;
	private String messageId;
}
