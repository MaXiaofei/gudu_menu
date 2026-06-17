package com.yanhuo.xsd.modules.menu;

import lombok.Data;

import java.util.List;

@Data
public class MenuSaveDTO {

    private Menu menu;

    private List<MenuDish> dishes;
}
