package openccjavacli;

class ConsoleProgressBar {
    private static final char[] BLOCKS = {' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█'};
    private static final char[] SPINNER = {'|', '/', '-', '\\'};

    private final int width;
    private String lastRendered = "";
    private int spinIndex = 0;

    ConsoleProgressBar(int width) {
        this.width = Math.max(10, width);
    }

    void update(int current, int total) {
        if (total <= 0) {
            return;
        }

        int normalizedCurrent = Math.max(0, Math.min(current, total));
        double progress = (double) normalizedCurrent / total;

        // smoother percentage (no jitter near 100%)
        int percent = (int) (progress * 100.0);

        String rendered = renderLine(normalizedCurrent, total, progress, percent);

        // avoid unnecessary redraw (flicker reduction)
        if (rendered.equals(lastRendered)) {
            return;
        }
        lastRendered = rendered;

        // carriage return overwrite
        System.err.print('\r' + rendered);

        if (normalizedCurrent >= total) {
            System.err.println();
        }
    }

    private String renderLine(int current, int total, double progress, int percent) {
        // safe clamp
        int filledUnits = Math.min(width * 8, (int) (progress * width * 8));

        int fullBlocks = filledUnits / 8;
        int partialBlock = filledUnits % 8;

        StringBuilder bar = new StringBuilder(width + 2);
        bar.append('[');

        for (int i = 0; i < width; i++) {
            if (i < fullBlocks) {
                bar.append(BLOCKS[8]);
            } else if (i == fullBlocks && partialBlock > 0) {
                bar.append(BLOCKS[partialBlock]);
            } else {
                bar.append(BLOCKS[0]);
            }
        }

        bar.append(']');

        // decoupled spinner (stable animation)
        spinIndex = (spinIndex + 1) % SPINNER.length;
        char spinner = current >= total ? '✔' : SPINNER[spinIndex];

        // trailing spaces to clear leftovers (Windows safe)
        return String.format("%s %3d%% (%d/%d) %c   ", bar, percent, current, total, spinner);
    }
}