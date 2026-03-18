package parallelsearch;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.List;

/**
 * Swing desktop application that performs live, parallel, case-insensitive
 * substring search over ~457 K four-letter strings loaded into memory.
 * @author Akshay Ghildiyal
 */
public class ParallelSearchApp extends JFrame {

    private JTextField searchField;
    private JButton searchButton;
    private ResultListModel resultModel;
    private JLabel statusLabel;
    private JLabel infoLabel;

    private SearchEngine engine;
    private Timer debounceTimer;
    private SwingWorker<SearchEngine.SearchResult, Void> activeSearch;

    public ParallelSearchApp() {
        super("Parallel String Search");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(620, 480));
        setPreferredSize(new Dimension(720, 600));
        buildUI();
        wireEvents();
        pack();
        setLocationRelativeTo(null);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));

        // --- header ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(0, 0, 12, 0));

        JLabel title = new JLabel("Parallel String Search");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        infoLabel = new JLabel("Initialising…");
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.PLAIN, 12f));
        infoLabel.setForeground(new Color(100, 100, 100));

        header.add(title, BorderLayout.NORTH);
        header.add(Box.createVerticalStrut(4), BorderLayout.CENTER);
        header.add(infoLabel, BorderLayout.SOUTH);

        // --- search bar ---
        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(searchLabel.getFont().deriveFont(Font.PLAIN, 14f));

        searchField = new JTextField();
        searchField.setFont(searchField.getFont().deriveFont(Font.PLAIN, 14f));
        searchField.setEnabled(false);

        searchButton = new JButton("Search");
        searchButton.setFont(searchButton.getFont().deriveFont(Font.PLAIN, 13f));
        searchButton.setEnabled(false);

        searchBar.add(searchLabel, BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(searchButton, BorderLayout.EAST);

        // --- result list ---
        resultModel = new ResultListModel();
        JList<String> resultList = new JList<>(resultModel);
        resultList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Results"),
                new EmptyBorder(4, 4, 4, 4)));

        // --- status bar ---
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        statusLabel.setBorder(new EmptyBorder(8, 2, 0, 0));

        // --- assemble ---
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(header, BorderLayout.NORTH);
        topPanel.add(searchBar, BorderLayout.SOUTH);

        root.add(topPanel, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void wireEvents() {
        debounceTimer = new Timer(SearchConfig.SEARCH_DEBOUNCE_MS, e -> performSearch());
        debounceTimer.setRepeats(false);

        DocumentListener liveSearch = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { debounceTimer.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { debounceTimer.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { debounceTimer.restart(); }
        };
        searchField.getDocument().addDocumentListener(liveSearch);
        searchField.addActionListener(e -> performSearch());
        searchButton.addActionListener(e -> performSearch());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (engine != null) {
                    engine.shutdown();
                }
                dispose();
                System.exit(0);
            }
        });
    }

    // Search execution (background task)

    private void performSearch() {
        if (engine == null) return;

        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            resultModel.setData(Collections.emptyList(), 0);
            statusLabel.setText("Enter a search term to begin");
            return;
        }

        if (activeSearch != null && !activeSearch.isDone()) {
            activeSearch.cancel(true);
        }

        statusLabel.setText("Searching…");

        activeSearch = new SwingWorker<>() {
            @Override
            protected SearchEngine.SearchResult doInBackground() {
                return engine.search(query);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    SearchEngine.SearchResult result = get();
                    int displayCount = Math.min(result.getTotalCount(), SearchConfig.MAX_DISPLAY_RESULTS);
                    resultModel.setData(result.getMatches(), displayCount);

                    String msg = getString(result);
                    statusLabel.setText(msg);
                } catch (java.util.concurrent.CancellationException ignored) {
                    // search was superseded
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                }
            }

            private String getString(SearchEngine.SearchResult result) {
                String msg = String.format(
                        "Found %,d match%s in %.2f ms  │  %d thread%s used",
                        result.getTotalCount(),
                        result.getTotalCount() == 1 ? "" : "es",
                        result.getElapsedMs(),
                        engine.getThreadCount(),
                        engine.getThreadCount() == 1 ? "" : "s");

                if (result.getTotalCount() > SearchConfig.MAX_DISPLAY_RESULTS) {
                    msg += String.format("  │  showing first %,d", SearchConfig.MAX_DISPLAY_RESULTS);
                }
                return msg;
            }
        };
        activeSearch.execute();
    }

    // Data loading (background task)

    private void loadData() {
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("Generating CSV data file (first run only)…");
                String[] data = DataStore.loadOrGenerate();
                publish("Loaded " + data.length + " strings, shuffled in random order");
                engine = new SearchEngine(data);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                infoLabel.setText(chunks.getLast());
            }

            @Override
            protected void done() {
                try {
                    get(); // propagate exceptions
                    infoLabel.setText(String.format(
                            "Data: %,d strings loaded  │  Threads: %d / %d available cores",
                            engine.getDataSize(),
                            engine.getThreadCount(),
                            Runtime.getRuntime().availableProcessors()));
                    searchField.setEnabled(true);
                    searchButton.setEnabled(true);
                    searchField.requestFocusInWindow();
                    statusLabel.setText("Enter a search term to begin");
                } catch (Exception ex) {
                    infoLabel.setText("Failed to load data");
                    statusLabel.setText("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ─── Custom list model backed by a List<String> ─────────────────────

    private static class ResultListModel extends AbstractListModel<String> {

        private List<String> data = Collections.emptyList();
        private int displayCount;

        void setData(List<String> data, int displayCount) {
            int oldCount = this.displayCount;
            this.data = data;
            this.displayCount = displayCount;
            if (oldCount > 0) {
                fireIntervalRemoved(this, 0, oldCount - 1);
            }
            if (displayCount > 0) {
                fireIntervalAdded(this, 0, displayCount - 1);
            }
        }

        @Override
        public int getSize() {
            return displayCount;
        }

        @Override
        public String getElementAt(int index) {
            return data.get(index);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ParallelSearchApp app = new ParallelSearchApp();
            app.setVisible(true);
            app.loadData();
        });
    }
}
