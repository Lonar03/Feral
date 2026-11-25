package keystrokesmod.utility.shader.effects;

public class Animation {
    public long start;
    public float last;
    public float value;
    public float duration;

    public Animation(float duration) {
        this.duration = duration;
    }

    public void start() {
        this.last = 0.0F;
        this.start = System.currentTimeMillis();
    }

    public float getValue(float from, float to, int mode) {
        if (this.last == to) {
            return this.last;
        }

        float t = (float) (System.currentTimeMillis() - this.start) / this.duration;
        switch (mode) {
            case 1:
                t = t < 0.5F ? 4.0F * t * t * t : (t - 1.0F) * (2.0F * t - 2.0F) * (2.0F * t - 2.0F) + 1.0F;
                break;
            case 2:
                t = (float) (1.0 - Math.pow(1.0F - t, 5.0));
                break;
            case 3:
                t = this.bounce(t);
        }

        float v = from + t * (to - from);
        if (to < v) {
            v = to;
        }

        if (v == to) {
            this.last = v;
        }

        this.value = v;
        return this.value;
    }

    public int getValueInt(int from, int to, int mode) {
        return Math.round(this.getValue(from, to, mode));
    }

    public float bounce(float f) {
        double n = 7.5625;
        double d = 2.75;
        if (f < 1.0 / d) {
            return (float) (n * f * f);
        } else if (f < 2.0 / d) {
            f = (float) (f - 1.5 / d);
            return (float) (n * f * f + 0.75);
        } else if (f < 2.5 / d) {
            f = (float) (f - 2.25 / d);
            return (float) (n * f * f + 0.9375);
        } else {
            f = (float) (f - 2.625 / d);
            return (float) (n * f * f + 0.984375);
        }
    }
}
