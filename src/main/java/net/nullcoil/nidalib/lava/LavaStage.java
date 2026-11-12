package net.nullcoil.nidalib.lava;

/**
 * The lava heat scale, coolest to hottest. Each stage is the active lava texture run
 * through the same math as GIMP's Colors → Hue-Chroma tool, so values dialed in on the
 * vanilla texture in GIMP can be pasted straight in here:
 * <ul>
 *   <li>hue: degrees added to every pixel's hue (-180 to 180)</li>
 *   <li>chroma: added to every pixel's colorfulness (-100 to 100)</li>
 *   <li>lightness: added to every pixel's lightness (-100 to 100)</li>
 * </ul>
 * Like GIMP, this works in CIE LCh (D50), and colors that land outside sRGB are clipped.
 *
 * <p>Heat values sit between stages (e.g. 2.3 = 70% yellow, 30% white). Lava itself
 * crossfades the two neighbors; fire caught from lava uses the nearest one.
 */
public enum LavaStage {
    //       name         hue      chroma   lightness
    RED     ("red",      -15.00f,   0.00f,  -10.00f),
    VANILLA ("vanilla",    0.00f,   0.00f,    0.00f),
    YELLOW  ("yellow",    20.00f,   0.00f,   12.00f),
    WHITE   ("white",      0.00f, -60.00f,   35.00f),
    INFINITE("infinite", 220.00f, -45.00f,   40.00f),
    SOUL    ("soul",     145.61f,  64.97f,   17.20f); // dialed in on vanilla lava in GIMP for Soulscorch

    public final String id;
    private final float hue;
    private final float chroma;
    private final float lightness;

    LavaStage(String id, float hue, float chroma, float lightness) {
        this.id = id;
        this.hue = hue;
        this.chroma = chroma;
        this.lightness = lightness;
    }

    public static final int MAX = values().length - 1;

    /** The stage nearest to a heat value. */
    public static LavaStage nearest(float heat) {
        return values()[Math.max(0, Math.min(MAX, Math.round(heat)))];
    }

    /** Vanilla is drawn with the untouched active texture; every other stage gets a generated copy. */
    public boolean isGenerated() {
        return this != VANILLA;
    }

    /** Applies this stage's Hue-Chroma adjustment to one ARGB pixel. */
    public int apply(int argb) {
        int a = (argb >>> 24) & 0xFF;
        double[] lch = rgbToLch((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);

        double l = Math.max(0, Math.min(100, lch[0] + lightness));
        double c = Math.max(0, lch[1] + chroma);
        double h = lch[2] + hue;

        return (a << 24) | lchToRgb(l, c, h);
    }

    // ---- sRGB <-> CIE LCh(ab), D50 white (what GIMP/GEGL uses) ----

    private static final double XN = 0.96422, YN = 1.0, ZN = 0.82521;
    private static final double EPSILON = 216.0 / 24389.0, KAPPA = 24389.0 / 27.0;

    private static double[] rgbToLch(int r8, int g8, int b8) {
        double r = toLinear(r8 / 255.0), g = toLinear(g8 / 255.0), b = toLinear(b8 / 255.0);

        // Linear sRGB -> XYZ (D50, Bradford-adapted)
        double x = 0.4360747 * r + 0.3850649 * g + 0.1430804 * b;
        double y = 0.2225045 * r + 0.7168786 * g + 0.0606169 * b;
        double z = 0.0139322 * r + 0.0971045 * g + 0.7141733 * b;

        double fx = labF(x / XN), fy = labF(y / YN), fz = labF(z / ZN);
        double l = 116 * fy - 16;
        double aa = 500 * (fx - fy);
        double bb = 200 * (fy - fz);

        return new double[]{l, Math.hypot(aa, bb), Math.toDegrees(Math.atan2(bb, aa))};
    }

    private static int lchToRgb(double l, double c, double hDeg) {
        double h = Math.toRadians(hDeg);
        double aa = c * Math.cos(h), bb = c * Math.sin(h);

        double fy = (l + 16) / 116;
        double fx = fy + aa / 500;
        double fz = fy - bb / 200;

        double x = XN * labFInverse(fx);
        double y = YN * (l > KAPPA * EPSILON ? fy * fy * fy : l / KAPPA);
        double z = ZN * labFInverse(fz);

        // XYZ (D50) -> linear sRGB
        double r = 3.1338561 * x - 1.6168667 * y - 0.4906146 * z;
        double g = -0.9787684 * x + 1.9161415 * y + 0.0334540 * z;
        double b = 0.0719453 * x - 0.2289914 * y + 1.4052427 * z;

        return (to8bit(r) << 16) | (to8bit(g) << 8) | to8bit(b);
    }

    private static double labF(double t) {
        return t > EPSILON ? Math.cbrt(t) : (KAPPA * t + 16) / 116;
    }

    private static double labFInverse(double t) {
        double t3 = t * t * t;
        return t3 > EPSILON ? t3 : (116 * t - 16) / KAPPA;
    }

    private static double toLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int to8bit(double linear) {
        double c = Math.max(0, Math.min(1, linear)); // clip to sRGB, like GIMP
        double encoded = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.round(encoded * 255);
    }
}
