package parallelsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Parallel in-memory search engine backed by a {@link ForkJoinPool}.
 * <p>
 * Work is divided recursively via {@link SearchTask} (a {@link RecursiveTask})
 * until each slice is smaller than {@link SearchConfig#FORK_THRESHOLD}, at
 * which point a sequential scan is performed.  The pool parallelism is
 * controlled by {@link SearchConfig#MAX_THREADS}.
 * @author Akshay Ghildiyal
 */
public class SearchEngine {

    private final String[] data;
    private final ForkJoinPool pool;
    private final int threadCount;

    public SearchEngine(String[] data) {
        this.data = data;
        this.threadCount = SearchConfig.getThreadCount();
        this.pool = new ForkJoinPool(threadCount);
    }

    public int getThreadCount() {
        return threadCount;
    }

    public int getDataSize() {
        return data.length;
    }

    /**
     * Performs a case-insensitive substring search across all loaded strings.
     *
     * @return a {@link SearchResult} containing matched strings and timing info
     */
    public SearchResult search(String query) {
        if (query == null || query.isEmpty()) {
            return new SearchResult(Collections.emptyList(), 0, 0);
        }

        String lowerQuery = query.toLowerCase();
        long startNanos = System.nanoTime();

        SearchTask task = new SearchTask(data, 0, data.length, lowerQuery);
        List<String> matches = pool.invoke(task);

        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        return new SearchResult(matches, matches.size(), elapsedMs);
    }

    public void shutdown() {
        pool.shutdown();
    }


    private static class SearchTask extends RecursiveTask<List<String>> {

        private final String[] data;
        private final int from;
        private final int to;
        private final String query;

        SearchTask(String[] data, int from, int to, String query) {
            this.data = data;
            this.from = from;
            this.to = to;
            this.query = query;
        }

        @Override
        protected List<String> compute() {
            int size = to - from;

            if (size <= SearchConfig.FORK_THRESHOLD) {
                List<String> result = new ArrayList<>();
                for (int i = from; i < to; i++) {
                    if (data[i].toLowerCase().contains(query)) {
                        result.add(data[i]);
                    }
                }
                return result;
            }

            int mid = from + size / 2;
            SearchTask left = new SearchTask(data, from, mid, query);
            SearchTask right = new SearchTask(data, mid, to, query);

            left.fork();
            List<String> rightResult = right.compute();
            List<String> leftResult = left.join();

            List<String> combined = new ArrayList<>(leftResult.size() + rightResult.size());
            combined.addAll(leftResult);
            combined.addAll(rightResult);
            return combined;
        }
    }

    public static class SearchResult {

        private final List<String> matches;
        private final int totalCount;
        private final double elapsedMs;

        SearchResult(List<String> matches, int totalCount, double elapsedMs) {
            this.matches = matches;
            this.totalCount = totalCount;
            this.elapsedMs = elapsedMs;
        }

        public List<String> getMatches() {
            return matches;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public double getElapsedMs() {
            return elapsedMs;
        }
    }
}
