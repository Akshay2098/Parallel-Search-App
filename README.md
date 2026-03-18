# Parallel String Search

A Java Swing desktop application that performs **in-memory parallel search** over
all 456,976 four-letter uppercase strings (AAAA … ZZZZ) using the Fork/Join
framework.


Requires **Java 11+** (only the standard library — no external dependencies).

## Features

| Requirement | Implementation |
|---|---|
| GUI with search field, scrollable list, timing display | Swing (`JTextField`, `JList`, status `JLabel`) |
| Case-insensitive substring search | `toLowerCase().contains()` |
| Parallel search using all CPU cores | `ForkJoinPool` + `RecursiveTask` |
| Configurable thread limit | `SearchConfig.MAX_THREADS` |
| Non-blocking GUI | `SwingWorker` for both data loading and each search |
| Data stored in file format | CSV (`data/strings.csv`, generated on first run) |
| Random order in memory | `Collections.shuffle()` after loading |

## Limiting CPU usage

Open `src/parallelsearch/SearchConfig.java` and change `MAX_THREADS`:

```java
// 0 = all cores (default),  positive number = fixed limit
public static final int MAX_THREADS = 4;   // use at most 4 cores
```

## Project structure

```
ParallelSearchApp/
├── src/parallelsearch/
│   ├── SearchConfig.java        # tunable settings (thread count, paths, …)
│   ├── DataStore.java           # CSV generation & loading + shuffle
│   ├── SearchEngine.java        # ForkJoinPool parallel search
│   └── ParallelSearchApp.java   # Swing GUI + entry point
├── data/strings.csv             # generated at first run (≈ 2.3 MB)
└── README.md
```

## How the parallel search works

1. The string array is wrapped in a `RecursiveTask<List<String>>`.
2. If the slice is larger than `FORK_THRESHOLD` (default 10 000), the task
   splits into two halves — left is **forked** to another thread while the
   right half is computed in the current thread.
3. Once both halves finish, results are merged.
4. The `ForkJoinPool` parallelism equals the configured thread count, so all
   available cores (or a fixed subset) participate in the search.
