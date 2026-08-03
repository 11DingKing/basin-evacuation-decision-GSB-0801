package gov.basin.evac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Externalised decision thresholds and upstream freshness policy. This is the only place
 * decision tuning lives; the threshold domain module reads these values so that operators
 * can adjust behaviour without code changes and controllers never encode rules.
 */
@ConfigurationProperties(prefix = "basin.decision")
public class DecisionProperties {

    @NestedConfigurationProperty
    private Thresholds thresholds = new Thresholds();

    @NestedConfigurationProperty
    private Upstream upstream = new Upstream();

    public Thresholds getThresholds() {
        return thresholds;
    }

    public void setThresholds(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    public Upstream getUpstream() {
        return upstream;
    }

    public void setUpstream(Upstream upstream) {
        this.upstream = upstream;
    }

    public static class Thresholds {
        @NestedConfigurationProperty
        private Ladder rainfall3hMm = new Ladder();
        @NestedConfigurationProperty
        private Ladder riverLevelM = new Ladder();

        public Ladder getRainfall3hMm() {
            return rainfall3hMm;
        }

        public void setRainfall3hMm(Ladder rainfall3hMm) {
            this.rainfall3hMm = rainfall3hMm;
        }

        public Ladder getRiverLevelM() {
            return riverLevelM;
        }

        public void setRiverLevelM(Ladder riverLevelM) {
            this.riverLevelM = riverLevelM;
        }
    }

    /**
     * Ascending thresholds mapping a scalar reading to the escalating decision levels.
     */
    public static class Ladder {
        private double watch;
        private double movePrepare;
        private double moveNow;

        public double getWatch() {
            return watch;
        }

        public void setWatch(double watch) {
            this.watch = watch;
        }

        public double getMovePrepare() {
            return movePrepare;
        }

        public void setMovePrepare(double movePrepare) {
            this.movePrepare = movePrepare;
        }

        public double getMoveNow() {
            return moveNow;
        }

        public void setMoveNow(double moveNow) {
            this.moveNow = moveNow;
        }
    }

    public static class Upstream {
        private long maxEvidenceAgeMinutes = 30;

        public long getMaxEvidenceAgeMinutes() {
            return maxEvidenceAgeMinutes;
        }

        public void setMaxEvidenceAgeMinutes(long maxEvidenceAgeMinutes) {
            this.maxEvidenceAgeMinutes = maxEvidenceAgeMinutes;
        }
    }
}
