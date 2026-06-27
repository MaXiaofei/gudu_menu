package com.gudu.xsd.modules.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ReviewSaveDTO {
    @NotNull
    private Long dishId;
    @NotNull @Min(1) @Max(5)
    private Integer starRating;
    private String text;
    private List<String> images;
    /** 维度分：dimensionId -> score(1-5) */
    private Map<Long, Integer> dimensionScores;
}
