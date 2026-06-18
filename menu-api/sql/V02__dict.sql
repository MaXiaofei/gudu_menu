-- 通用字典表 + 8 类种子
-- cuisine 菜系 / tag 菜品标签 / category 菜品分类 / menu_type 菜单种类
-- audience 特殊人群 / unit 计量单位 / purchase_category 采购品类 / role 角色标签
CREATE TABLE IF NOT EXISTS sys_dict (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  dict_group VARCHAR(32) NOT NULL,
  name       VARCHAR(64) NOT NULL,
  sort       INT DEFAULT 0,
  UNIQUE KEY uk_group_name (dict_group, name)
);

INSERT INTO sys_dict(dict_group, name) VALUES
 ('cuisine','鲁菜'),('cuisine','川菜'),('cuisine','粤菜'),
 ('tag','家常'),('tag','快手菜'),('tag','下饭'),('tag','清淡'),
 ('category','热菜'),('category','凉菜'),('category','汤羹'),('category','甜品'),
 ('menu_type','日常'),('menu_type','家宴'),('menu_type','节日'),('menu_type','宝宝餐'),
 ('audience','高血压'),('audience','高血糖'),('audience','高血脂'),('audience','宝宝辅食'),
 ('unit','g'),('unit','ml'),('unit','个'),('unit','把'),
 ('purchase_category','蔬菜'),('purchase_category','畜禽肉'),('purchase_category','水产海鲜'),
 ('purchase_category','蛋类'),('purchase_category','豆制品'),('purchase_category','乳制品'),
 ('purchase_category','调味料'),('purchase_category','水果'),
 ('role','掌勺'),('role','备菜'),('role','普通成员');
