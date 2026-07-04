package com.gudu.xsd.modules.menu.prep;

/**
 * 备料状态（备菜模块）。枚举铁律：DB/JSON 存大写名，前端映射中文+色。
 * 参照 {@link com.gudu.xsd.modules.shopping.StockClassifier.Status} 范式。
 *
 * <ul>
 *   <li>{@link #PENDING} 待备（默认；menu_prep_status 无记录即视为此）；</li>
 *   <li>{@link #READY} ✓已备；</li>
 *   <li>{@link #THAWING} 化冻中；</li>
 *   <li>{@link #MARINATING} 腌制中。</li>
 * </ul>
 */
public enum PrepStatus { PENDING, READY, THAWING, MARINATING }
