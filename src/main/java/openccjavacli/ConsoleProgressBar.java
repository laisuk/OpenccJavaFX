package openccjavacli;

class ConsoleProgressBar {
    private final int width;
    private int lastPercent = -1;

    ConsoleProgressBar(int width) {
        this.width = width;
    }

    void update(int current, int total) {
        if (total <= 0) {
            return;
        }

        int percent = current * 100 / total;
        if (percent == lastPercent) {
            return; // avoid noisy updates
        }
        lastPercent = percent;

        int filled = percent * width / 100;
        StringBuilder sb = new StringBuilder();
        sb.append('\r'); // carriage return: overwrite same line
        sb.append("[");
        for (int i = 0; i < width; i++) {
            sb.append(i < filled ? '=' : ' ');
        }
        sb.append("] ");
        if (percent < 100) {
            sb.append(String.format("%3d%%", percent));
        } else {
            sb.append("100%");
        }

        System.err.print(sb);

        if (percent == 100) {
            System.err.println(); // move to next line at the end
        }
    }
}
