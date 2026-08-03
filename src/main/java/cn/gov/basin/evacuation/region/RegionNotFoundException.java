package cn.gov.basin.evacuation.region;

public class RegionNotFoundException extends RuntimeException {
    public RegionNotFoundException(String code) {
        super("Region not found: " + code);
    }
}
