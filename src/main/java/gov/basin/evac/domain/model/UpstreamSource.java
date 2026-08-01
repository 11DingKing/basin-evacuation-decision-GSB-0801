package gov.basin.evac.domain.model;

/**
 * The four upstream data feeds that produce a risk snapshot. Rainfall and river level are
 * quantitative and required for a risk judgement; hazard-point and road status are qualitative.
 */
public enum UpstreamSource {
    RAINFALL,
    RIVER_LEVEL,
    HAZARD,
    ROAD
}
