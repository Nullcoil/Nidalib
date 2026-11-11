package net.nullcoil.nidalib.worldgen.biome;

/**
 * Decides whether a spot that vanilla assigned to a parent biome
 * should become the sub-biome instead.
 */
@FunctionalInterface
public interface SubBiomeRule {
    boolean test(Context context);

    /**
     * Climate information about the spot being tested.
     *
     * @param parentDistance squared climate distance from the spot to the parent's target point
     * @param nextDistance   squared climate distance to the closest point of any other biome
     */
    record Context(long parentDistance, long nextDistance) {
        /**
         * How deep inside the parent this spot is: 0 at the parent's climate center,
         * approaching 1 at its border with another biome.
         */
        public double edgeRatio() {
            if (nextDistance <= 0) return 1.0;
            return Math.sqrt((double) parentDistance / (double) nextDistance);
        }
    }

    /**
     * Places the sub-biome in the core of the parent: every spot whose
     * {@link Context#edgeRatio()} is below {@code maxRatio}. Lower values give
     * a smaller core and a thicker ring of parent around it.
     * Around 0.4 to 0.6 is a good starting range.
     */
    static SubBiomeRule core(double maxRatio) {
        return context -> context.edgeRatio() < maxRatio;
    }
}
