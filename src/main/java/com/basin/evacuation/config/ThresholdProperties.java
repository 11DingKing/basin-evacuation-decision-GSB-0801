package com.basin.evacuation.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 四级决策阈值配置（evacuation.threshold.*），仅供 decision 模块的 ThresholdEvaluator 使用。
 */
@ConfigurationProperties(prefix = "evacuation.threshold")
public record ThresholdProperties(Rainfall rainfall, WaterLevel water) {

    /** 3 小时累计降水阈值，单位 mm */
    public record Rainfall(BigDecimal red, BigDecimal orange, BigDecimal yellow) {}

    /** 河道水位阈值，单位 m */
    public record WaterLevel(BigDecimal red, BigDecimal orange, BigDecimal yellow) {}
}
