package cn.gov.basin.evacuation.domain.threshold;

import java.math.BigDecimal;

public final class ThresholdConfig {

    public static final BigDecimal RAIN_L4 = new BigDecimal("100.00");
    public static final BigDecimal RAIN_L3 = new BigDecimal("80.00");
    public static final BigDecimal RAIN_L2 = new BigDecimal("50.00");

    public static final BigDecimal WATER_L4 = new BigDecimal("6.00");
    public static final BigDecimal WATER_L3 = new BigDecimal("5.50");
    public static final BigDecimal WATER_L2 = new BigDecimal("5.00");

    private ThresholdConfig() {
    }
}
