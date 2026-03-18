package parallelsearch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates (if needed) and loads the CSV data file containing every
 * four-letter uppercase combination from AAAA to ZZZZ (26^4 = 456 976 entries).
 * The loaded array is shuffled into random order before being returned.
 */
public final class DataStore {

    private DataStore() {}

    public static String[] loadOrGenerate() throws IOException {
        Path path = Paths.get(SearchConfig.DATA_FILE);
        if (!Files.exists(path)) {
            generate(path);
        }
        return load(path);
    }

    private static void generate(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write("value");
            w.newLine();
            char[] buf = new char[4];
            for (int a = 0; a < 26; a++) {
                buf[0] = (char) ('A' + a);
                for (int b = 0; b < 26; b++) {
                    buf[1] = (char) ('A' + b);
                    for (int c = 0; c < 26; c++) {
                        buf[2] = (char) ('A' + c);
                        for (int d = 0; d < 26; d++) {
                            buf[3] = (char) ('A' + d);
                            w.write(buf);
                            w.newLine();
                        }
                    }
                }
            }
        }
    }

    private static String[] load(Path path) throws IOException {
        List<String> lines = new ArrayList<>(500_000);
        try (BufferedReader r = Files.newBufferedReader(path)) {
            r.readLine(); // skip CSV header
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        Collections.shuffle(lines);
        return lines.toArray(new String[0]);
    }
}
