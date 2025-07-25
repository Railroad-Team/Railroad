package io.github.railroad.utility;

public class MathUtils {
    public static double clamp(double value, double min, double max) {
        return Math.clamp(value,min,max);
    }

    // Rounds To Nearest x
    // i.e. roundToNearest(25,4) gives 28 as 28 % 4 = 0

    public static int roundToNearest(double val, int x) {
        return Math.round((float)val / (float) x) * x;
    }
}
