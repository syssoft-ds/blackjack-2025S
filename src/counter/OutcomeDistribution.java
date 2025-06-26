package counter;

public record OutcomeDistribution(double p17, double p18, double p19, double p20, double p21, double pBust) {
    
    public OutcomeDistribution add(OutcomeDistribution other, double weight) {
        return new OutcomeDistribution(
            this.p17 + other.p17 * weight,
            this.p18 + other.p18 * weight,
            this.p19 + other.p19 * weight,
            this.p20 + other.p20 * weight,
            this.p21 + other.p21 * weight,
            this.pBust + other.pBust * weight
        );
    }

    public static OutcomeDistribution zero()
    {
        return new OutcomeDistribution(0, 0, 0, 0, 0, 0);
    }
}