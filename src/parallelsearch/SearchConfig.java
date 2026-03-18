package parallelsearch;

/**
 * @author Akshay Ghildiyal
 */
public final class SearchConfig {

    private SearchConfig() {}

    // ─── Tunable settings ───────────────────────────────────────────────
    //
    // MAX_THREADS controls how many CPU cores the parallel search may use.
    //   0  → use ALL available cores (default)
    //   N  → use at most N cores (e.g. set to 2 to limit to two threads)
    //
    public static final int MAX_THREADS = 0;

    // Minimum slice size before the ForkJoin task stops subdividing.
    public static final int FORK_THRESHOLD = 10_000;

    // Maximum results shown in the GUI list (avoids UI lag on huge result sets).
    public static final int MAX_DISPLAY_RESULTS = 10_000;

    // Debounce delay (ms) for live-as-you-type searching.
    public static final int SEARCH_DEBOUNCE_MS = 250;

    // Path to the CSV data file (generated on first run if missing).
    public static final String DATA_FILE = "data/strings.csv";

    // ────────────────────────────────────────────────────────────────────

    public static int getThreadCount() {
        int available = Runtime.getRuntime().availableProcessors();
        if (MAX_THREADS > 0 && MAX_THREADS < available) {
            return MAX_THREADS;
        }
        return available;
    }
}
