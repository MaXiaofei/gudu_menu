package com.yanhuo.xsd.modules.dish;

import lombok.Data;

import java.util.List;

@Data
public class DishSaveDTO {

    private Dish dish;

    private List<DishStep> steps;
}
