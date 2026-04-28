package openccjavacli;

class ConsoleProgressBar {
    private static final char[] BLOCKS = {' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█'};
    private static final char[] SPINNER = {'|', '/', '-', '\\'};

    private final int width;
    private String lastRendered = "";

    ConsoleProgressBar(int width) {
        this.width = Math.max(10, width);
    }

    void update(int current, int total) {
        if (total <= 0) {
            return;
        }

        int normalizedCurrent = Math.max(0, Math.min(current, total));
        double progress = (double) normalizedCurrent / total;
        int percent = (int) Math.round(progress * 100.0);

        String rendered = renderLine(normalizedCurrent, total, progress, percent);
        if (rendered.equals(lastRendered)) {
            return;
        }
        lastRendered = rendered;

        System.err.print('\r' + rendered);
        if (normalizedCurrent >= total) {
            System.err.println();
        }
    }

    private String renderLine(int current, int total, double progress, int percent) {
        int filledUnits = (int) Math.floor(progress * width * 8);
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

        char spinner = current >= total ? '✔' : SPINNER[current % SPINNER.length];
        return String.format("%s %3d%% (%d/%d) %c", bar, percent, current, total, spinner);
    }
}
