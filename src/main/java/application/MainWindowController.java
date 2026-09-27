package application;

import database.JournalDAO;
import database.SessionDAO;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.JournalEntry;
import model.Mood;
import model.Session;
import service.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Future;

public class MainWindowController {

    // ---- Header ----
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private Button exportBtn;

    // ---- Tabs ----
    @FXML private Button tabScreen, tabStudy, tabSleep, tabJournal;

    // ---- Panels ----
    @FXML private VBox mainPanel, sessionPanel, sleepPanel, journalPanel;

    // ---- Session panel ----
    @FXML private FlowPane activityChipBox;
    @FXML private TextField activityField;
    @FXML private HBox moodBox;
    @FXML private Button startBtn, pauseBtn;
    @FXML private Label timerLabel;

    // ---- Sleep panel ----
    @FXML private TextField sleepHoursField;
    @FXML private TextField sleepMinutesField;
    @FXML private Button sleepSaveBtn, sleepClearBtn;
    @FXML private Label sleepTodayLabel;

    // ---- Table ----
    @FXML private TableView<Session> sessionTable;
    @FXML private TableColumn<Session, String> colActivity;
    @FXML private TableColumn<Session, String> colCategory;
    @FXML private TableColumn<Session, String> colDuration;
    @FXML private TableColumn<Session, String> colRating;
    @FXML private TableColumn<Session, String> colMood;
    @FXML private TableColumn<Session, String> colFavorite;
    @FXML private CheckBox favoritesFilter;
    @FXML private TextField searchField;

    // ---- Right panel — PIE CHART ----
    @FXML private PieChart screenTimePieChart;
    @FXML private Label tipLabel;
    @FXML private Label streakLabel;

    // ---- Journal ----
    @FXML private DatePicker journalDatePicker;
    @FXML private TextArea journalMorning, journalNoon, journalAfternoon, journalEvening, journalNight;
    @FXML private Label journalStatus;

    // ---- Data ----
    private final ObservableList<Session> allSessions = FXCollections.observableArrayList();
    private final ObservableList<Session> visibleSessions = FXCollections.observableArrayList();
    private final Map<String, List<String>> activitiesByCategory = new LinkedHashMap<>();

    private final SessionDAO sessionDAO = new SessionDAO();
    private final JournalDAO journalDAO = new JournalDAO();

    private TimerThread sessionTimer;
    private Session currentSession;
    private Mood selectedMood = Mood.NEUTRAL;
    private String selectedActivity = null;
    private int elapsedSeconds = 0;
    private String activeCategory = "Screen Time";
    private boolean paused = false;

    private final ThreadManager threadManager = ThreadManager.getInstance();
    private PauseTransition journalSaveDebounce;

    // ============================================================
    //  INIT
    // ============================================================
    @FXML
    public void initialize() {
        seedActivities();
        setupHeader();
        setupTable();
        setupMoodPicker();
        setupJournalTab();
        setupSleepFields();
        setupSearch();
        loadFromDatabase();
        renderActivityChips();
        updateStreak();
        updateScreenTimeByAppChart();

        activityField.textProperty().addListener((obs, oldV, newV) -> {
            if (!newV.isEmpty() && selectedActivity != null && !newV.equals(selectedActivity)) {
                selectedActivity = null;
                highlightSelectedActivity();
            }
        });

        threadManager.startConsumer(this::onSessionProcessed);
        refreshTable();
    }

    private void setupHeader() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM");
        dateLabel.setText("[Date: " + LocalDate.now().format(fmt) + "]");
        statusLabel.setText("● READY");
    }

    private void setupSearch() {
        searchField.textProperty().addListener((o, a, b) -> refreshTable());
    }

    private void setupSleepFields() {
        javafx.scene.control.TextFormatter<String> hoursFmt =
                new javafx.scene.control.TextFormatter<>(change ->
                        change.getControlNewText().matches("\\d{0,2}") ? change : null);
        sleepHoursField.setTextFormatter(hoursFmt);

        javafx.scene.control.TextFormatter<String> minutesFmt =
                new javafx.scene.control.TextFormatter<>(change ->
                        change.getControlNewText().matches("\\d{0,2}") ? change : null);
        sleepMinutesField.setTextFormatter(minutesFmt);
    }

    private void setupTable() {
        colActivity.setCellValueFactory(new PropertyValueFactory<>("activity"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colDuration.setCellValueFactory(new PropertyValueFactory<>("durationFormatted"));
        colRating.setCellValueFactory(new PropertyValueFactory<>("ratingDisplay"));

        colMood.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getMood().getEmoji()));
        colMood.setCellFactory(column -> new TableCell<Session, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else {
                    setText(item);
                    setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji'; -fx-font-size: 18px;");
                }
            }
        });

        colFavorite.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().isFavorite() ? "⭐" : "☆"));
        colFavorite.setCellFactory(column -> new TableCell<Session, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); setGraphic(null); return; }
                Session s = getTableView().getItems().get(getIndex());
                Label star = new Label(s.isFavorite() ? "⭐" : "☆");
                star.setStyle("-fx-font-size: 18px; -fx-cursor: hand;");
                star.setOnMouseClicked(e -> {
                    s.setFavorite(!s.isFavorite());
                    try { sessionDAO.updateFavorite(s); }
                    catch (SQLException ex) { System.err.println(ex.getMessage()); }
                    refreshTable();
                });
                setGraphic(star);
                setText(null);
            }
        });

        sessionTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        sessionTable.setItems(visibleSessions);
        favoritesFilter.selectedProperty().addListener((o, a, b) -> refreshTable());
        sessionTable.setRowFactory(tv -> {
            TableRow<Session> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openEditDialog(row.getItem());
            });
            return row;
        });
    }

    private void setupMoodPicker() {
        moodBox.getChildren().clear();
        for (Mood m : Mood.values()) {
            Button b = new Button(m.getEmoji());
            b.getStyleClass().add("mood-button");
            b.setTooltip(new Tooltip(m.getLabel()));
            b.setOnAction(e -> { selectedMood = m; highlightSelectedMood(); });
            moodBox.getChildren().add(b);
        }
        highlightSelectedMood();
    }

    private void highlightSelectedMood() {
        int i = 0;
        for (Mood m : Mood.values()) {
            Button b = (Button) moodBox.getChildren().get(i++);
            b.getStyleClass().remove("mood-selected");
            if (m == selectedMood) b.getStyleClass().add("mood-selected");
        }
    }

    // ============================================================
    //  JOURNAL
    // ============================================================
    private void setupJournalTab() {
        journalDatePicker.setValue(LocalDate.now());
        journalSaveDebounce = new PauseTransition(Duration.millis(600));
        journalSaveDebounce.setOnFinished(e -> saveJournalEntry());

        javafx.beans.value.ChangeListener<String> listener = (obs, oldV, newV) -> {
            journalStatus.setText("● saving...");
            journalSaveDebounce.playFromStart();
        };
        journalMorning.textProperty().addListener(listener);
        journalNoon.textProperty().addListener(listener);
        journalAfternoon.textProperty().addListener(listener);
        journalEvening.textProperty().addListener(listener);
        journalNight.textProperty().addListener(listener);

        journalDatePicker.setOnAction(e -> {
            saveJournalEntry();
            loadJournalForDate(journalDatePicker.getValue());
        });

        loadJournalForDate(LocalDate.now());
    }

    private void loadJournalForDate(LocalDate date) {
        if (date == null) return;
        try {
            JournalEntry entry = journalDAO.getByDate(date);
            journalMorning.setText(entry.getMorning());
            journalNoon.setText(entry.getNoon());
            journalAfternoon.setText(entry.getAfternoon());
            journalEvening.setText(entry.getEvening());
            journalNight.setText(entry.getNight());
            journalStatus.setText("● " + entry.getTotalWords() + " words");
        } catch (SQLException ex) {
            journalStatus.setText("● load failed");
        }
    }

    private void saveJournalEntry() {
        LocalDate date = journalDatePicker.getValue();
        if (date == null) return;
        try {
            JournalEntry entry = new JournalEntry(date,
                    journalMorning.getText(), journalNoon.getText(),
                    journalAfternoon.getText(), journalEvening.getText(),
                    journalNight.getText());
            journalDAO.save(entry);
            journalStatus.setText("● saved  (" + entry.getTotalWords() + " words)");
        } catch (SQLException ex) {
            journalStatus.setText("● save failed");
        }
    }

    // ============================================================
    //  OTHER SETUP
    // ============================================================
    private void seedActivities() {
        activitiesByCategory.put("Screen Time", Arrays.asList(
                "YouTube", "Facebook", "Instagram", "WhatsApp",
                "Chrome", "TikTok", "Twitter", "Reddit"));
        activitiesByCategory.put("Study Time", Arrays.asList(
                "Java", "DSA", "Math", "Physics",
                "Chemistry", "English", "Revision", "Assignment"));
        activitiesByCategory.put("Sleep Time", Arrays.asList(
                "Night Sleep", "Nap", "Rest"));
    }

    private void loadFromDatabase() {
        try {
            allSessions.setAll(sessionDAO.getAll());
            System.out.println("[Main] loaded " + allSessions.size() + " sessions from DB");
        } catch (SQLException ex) {
            System.err.println("[Main] DB load failed: " + ex.getMessage());
        }
    }

    private void onSessionProcessed() {
        SessionCounter c = threadManager.getCounter();
        statusLabel.setText("● " + c.getTotalSessions() + " PROCESSED");
        updateStreak();
    }

    private void renderActivityChips() {
        activityChipBox.getChildren().clear();
        List<String> activities = activitiesByCategory.getOrDefault(activeCategory, List.of());
        for (String name : activities) {
            Button chip = new Button(name);
            chip.getStyleClass().add("activity-chip");
            chip.setOnAction(e -> {
                selectedActivity = name;
                activityField.setText(name);
                highlightSelectedActivity();
            });
            activityChipBox.getChildren().add(chip);
        }
        selectedActivity = null;
        activityField.clear();
    }

    private void highlightSelectedActivity() {
        for (Node node : activityChipBox.getChildren()) {
            if (node instanceof Button b) {
                b.getStyleClass().remove("activity-selected");
                if (b.getText().equals(selectedActivity)) {
                    if (!b.getStyleClass().contains("activity-selected")) {
                        b.getStyleClass().add("activity-selected");
                    }
                }
            }
        }
    }

    // ============================================================
    //  PIE CHART — Screen Time % by App
    // ============================================================
    private void updateScreenTimeByAppChart() {
        // Group screen-time seconds per app
        Map<String, Integer> appSeconds = new LinkedHashMap<>();
        int totalScreenSeconds = 0;

        for (Session s : allSessions) {
            if (!"Screen Time".equals(s.getCategory())) continue;
            appSeconds.merge(s.getActivity(), s.getDurationSeconds(), Integer::sum);
            totalScreenSeconds += s.getDurationSeconds();
        }

        screenTimePieChart.getData().clear();

        // Empty state
        if (totalScreenSeconds == 0) {
            PieChart.Data placeholder = new PieChart.Data("No screen time yet", 1);
            screenTimePieChart.getData().add(placeholder);
            Platform.runLater(() -> {
                if (placeholder.getNode() != null) {
                    placeholder.getNode().setStyle("-fx-pie-color: #94a3b8;");
                }
            });
            return;
        }

        // Sort apps by duration descending
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(appSeconds.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // Add slice per app with percentage in the label
        for (Map.Entry<String, Integer> e : entries) {
            double pct = e.getValue() * 100.0 / totalScreenSeconds;
            String label = String.format("%s (%.0f%%)", e.getKey(), pct);
            double minutes = e.getValue() / 60.0;

            PieChart.Data slice = new PieChart.Data(label, minutes);
            screenTimePieChart.getData().add(slice);

            // Custom color per app + tooltip
            final int seconds = e.getValue();
            final double percentFinal = pct;
            Platform.runLater(() -> {
                if (slice.getNode() != null) {
                    slice.getNode().setStyle("-fx-pie-color: " + colorForApp(e.getKey()) + ";");
                    Tooltip.install(slice.getNode(),
                            new Tooltip(String.format("%s%n%s (%.1f%%)",
                                    e.getKey(), fmtSec(seconds), percentFinal)));
                }
            });
        }
    }

    /** Deterministic color per app name so the chart is consistent between runs. */
    private String colorForApp(String app) {
        String[] palette = {
                "#d97706",  // amber
                "#10b981",  // emerald
                "#3b82f6",  // blue
                "#ef4444",  // red
                "#8052d2",  // purple
                "#f59e0b",  // orange
                "#06b6d4",  // cyan
                "#ec4899",  // pink
                "#84cc16",  // lime
                "#6366f1"   // indigo
        };
        int hash = Math.abs(app.hashCode());
        return palette[hash % palette.length];
    }

    private String fmtSec(int s) {
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + String.format("%02d", sec) + "s";
        return sec + "s";
    }

    // ============================================================
    //  STREAK
    // ============================================================
    private void updateStreak() {
        try {
            List<String> dates = sessionDAO.getAllActiveDates();
            if (dates.isEmpty()) { streakLabel.setText("00"); return; }
            Set<LocalDate> active = new HashSet<>();
            for (String d : dates) active.add(LocalDate.parse(d));
            LocalDate cursor = LocalDate.now();
            if (!active.contains(cursor)) cursor = cursor.minusDays(1);
            int streak = 0;
            while (active.contains(cursor)) { streak++; cursor = cursor.minusDays(1); }
            streakLabel.setText(String.format("%02d", streak));
        } catch (SQLException ex) { streakLabel.setText("00"); }
    }

    // ============================================================
    //  TAB HANDLERS
    // ============================================================
    @FXML private void onTabScreen() { switchTab("Screen Time", tabScreen); }
    @FXML private void onTabStudy()  { switchTab("Study Time", tabStudy); }

    @FXML private void onTabSleep()  {
        activeCategory = "Sleep Time";
        setActiveTab(tabSleep);
        mainPanel.setVisible(true); mainPanel.setManaged(true);
        sessionPanel.setVisible(false); sessionPanel.setManaged(false);
        sleepPanel.setVisible(true); sleepPanel.setManaged(true);
        journalPanel.setVisible(false); journalPanel.setManaged(false);
        refreshTable();
        loadSleepForToday();
    }

    @FXML private void onTabJournal() {
        activeCategory = "Journal";
        setActiveTab(tabJournal);
        mainPanel.setVisible(false); mainPanel.setManaged(false);
        journalPanel.setVisible(true); journalPanel.setManaged(true);
        loadJournalForDate(journalDatePicker.getValue());
    }

    private void switchTab(String category, Button tab) {
        activeCategory = category;
        setActiveTab(tab);
        mainPanel.setVisible(true); mainPanel.setManaged(true);
        sessionPanel.setVisible(true); sessionPanel.setManaged(true);
        sleepPanel.setVisible(false); sleepPanel.setManaged(false);
        journalPanel.setVisible(false); journalPanel.setManaged(false);
        sessionTable.setPlaceholder(new Label("No sessions in " + category + " yet."));
        renderActivityChips();
        refreshTable();
    }

    private void setActiveTab(Button active) {
        Button[] tabs = {tabScreen, tabStudy, tabSleep, tabJournal};
        for (Button b : tabs) b.getStyleClass().remove("tab-active");
        if (!active.getStyleClass().contains("tab-active")) {
            active.getStyleClass().add("tab-active");
        }
    }

    private void refreshTable() {
        visibleSessions.clear();
        if ("Journal".equals(activeCategory)) return;
        String query = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        boolean favoritesOnly = favoritesFilter.isSelected();
        for (Session s : allSessions) {
            if (!s.getCategory().equals(activeCategory)) continue;
            if (favoritesOnly && !s.isFavorite()) continue;
            if (!query.isEmpty() && !s.getActivity().toLowerCase().contains(query)) continue;
            visibleSessions.add(s);
        }
    }

    // ============================================================
    //  SLEEP
    // ============================================================
    private void loadSleepForToday() {
        Session todaySleep = findTodaySleep();
        if (todaySleep != null) {
            int totalMinutes = todaySleep.getDurationSeconds() / 60;
            sleepHoursField.setText(String.valueOf(totalMinutes / 60));
            sleepMinutesField.setText(String.format("%02d", totalMinutes % 60));
            sleepTodayLabel.setText("Logged: " + todaySleep.getDurationFormatted()
                    + "  (edit and save to update)");
        } else {
            sleepHoursField.setText("");
            sleepMinutesField.setText("");
            sleepTodayLabel.setText("No sleep logged for today");
        }
    }

    private Session findTodaySleep() {
        LocalDate today = LocalDate.now();
        for (Session s : allSessions) {
            if ("Sleep Time".equals(s.getCategory()) && s.getDate().equals(today)) return s;
        }
        return null;
    }

    @FXML private void onSaveSleep() {
        int hours = parseIntOr(sleepHoursField.getText(), 0);
        int minutes = parseIntOr(sleepMinutesField.getText(), 0);
        if (hours == 0 && minutes == 0) { showWarning("Please enter a sleep duration (at least 1 minute)."); return; }
        if (minutes > 59) { showWarning("Minutes must be 0–59."); return; }
        if (hours > 24 || (hours == 24 && minutes > 0)) { showWarning("Sleep cannot exceed 24 hours."); return; }

        int totalSeconds = hours * 3600 + minutes * 60;
        Session existing = findTodaySleep();
        if (existing != null) {
            try { sessionDAO.delete(existing); allSessions.remove(existing); }
            catch (SQLException ex) { showWarning("Could not remove old entry: " + ex.getMessage()); return; }
        }

        Session sleep = new Session("Night Sleep", "Sleep Time", Mood.CONTENT);
        sleep.setDurationSeconds(totalSeconds);
        sleep.setRating(4);
        sleep.setNotes("Manual entry");
        sleep.setStartHour(23);

        try {
            sessionDAO.insert(sleep);
            allSessions.add(sleep);
            refreshTable();
            loadSleepForToday();
            updateStreak();
            updateScreenTimeByAppChart();
            showInfo("Saved", "Sleep logged: " + sleep.getDurationFormatted());
        } catch (SQLException ex) { showWarning("Save failed: " + ex.getMessage()); }
    }

    @FXML private void onClearSleep() {
        Session existing = findTodaySleep();
        if (existing == null) { showInfo("Nothing to clear", "No sleep entry for today."); return; }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete today's sleep entry?", ButtonType.OK, ButtonType.CANCEL);
        if (c.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                sessionDAO.delete(existing);
                allSessions.remove(existing);
                refreshTable();
                loadSleepForToday();
                updateStreak();
                updateScreenTimeByAppChart();
                showInfo("Cleared", "Today's sleep entry removed.");
            } catch (SQLException ex) { showWarning("Clear failed: " + ex.getMessage()); }
        }
    }

    private int parseIntOr(String text, int fallback) {
        if (text == null || text.isBlank()) return fallback;
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    // ============================================================
    //  SESSION LIFECYCLE
    // ============================================================
    @FXML private void onStartSession() {
        if (currentSession != null) { requestStop(); return; }
        String activity = activityField.getText().trim();
        if (activity.isEmpty()) { showWarning("Please select an activity or type a custom name."); return; }

        currentSession = new Session(activity, activeCategory, selectedMood);
        elapsedSeconds = 0;
        paused = false;

        startBtn.setText("■ STOP");
        startBtn.getStyleClass().add("stop-button");
        pauseBtn.setVisible(true); pauseBtn.setManaged(true);
        pauseBtn.setText("⏸ PAUSE");
        statusLabel.setText("● RECORDING " + activity.toUpperCase());
        activityField.setDisable(true);
        activityChipBox.setDisable(true);

        startTimer();
    }

    private void startTimer() {
        sessionTimer = new TimerThread("session-timer", elapsedSeconds, new TimerThread.TickListener() {
            @Override public void onTick(int s) { elapsedSeconds = s; updateTimerDisplay(); }
            @Override public void onFinish(int s) { }
        });
        sessionTimer.start();
    }

    @FXML private void onPauseResume() {
        if (currentSession == null) return;
        if (!paused) {
            if (sessionTimer != null) { sessionTimer.stopTimer(); sessionTimer = null; }
            paused = true;
            pauseBtn.setText("▶ RESUME");
            timerLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 0 12 0 12;");
            statusLabel.setText("● PAUSED");
        } else {
            paused = false;
            pauseBtn.setText("⏸ PAUSE");
            timerLabel.setStyle("-fx-text-fill: #1c1917; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 0 12 0 12;");
            statusLabel.setText("● RECORDING " + currentSession.getActivity().toUpperCase());
            startTimer();
        }
    }

    private void requestStop() {
        if (elapsedSeconds < 3) { showWarning("Session too short (under 3 seconds)."); return; }
        if (sessionTimer != null) { sessionTimer.stopTimer(); sessionTimer = null; }

        currentSession.setDurationSeconds(elapsedSeconds);
        boolean confirmed = showReflectionDialog(currentSession);

        if (confirmed) {
            try { sessionDAO.insert(currentSession); } catch (SQLException ex) { showWarning(ex.getMessage()); }
            allSessions.add(currentSession);
            try { threadManager.getQueue().put(currentSession); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            currentSession = null;
            elapsedSeconds = 0;
            paused = false;
            resetSessionUI();
            refreshTable();
            updateScreenTimeByAppChart();
            updateStreak();
        } else {
            paused = false;
            pauseBtn.setText("⏸ PAUSE");
            startTimer();
            statusLabel.setText("● RECORDING " + currentSession.getActivity().toUpperCase());
        }
    }

    private void resetSessionUI() {
        timerLabel.setText("00:00:00");
        timerLabel.setStyle("-fx-text-fill: #1c1917; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 0 12 0 12;");
        activityField.clear();
        activityField.setDisable(false);
        activityChipBox.setDisable(false);
        startBtn.setText("▶ START SESSION");
        startBtn.getStyleClass().remove("stop-button");
        pauseBtn.setVisible(false); pauseBtn.setManaged(false);
        statusLabel.setText("● READY");
        selectedActivity = null;
        highlightSelectedActivity();
    }

    private void updateTimerDisplay() {
        int h = elapsedSeconds / 3600;
        int m = (elapsedSeconds % 3600) / 60;
        int s = elapsedSeconds % 60;
        timerLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    // ============================================================
    //  REFLECTION DIALOG
    // ============================================================
    private boolean showReflectionDialog(Session s) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Session Complete");
        dialog.setHeaderText(s.getActivity() + " — " + s.getDurationFormatted());
        ButtonType saveType = new ButtonType("Save Session", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20)); root.setPrefWidth(460);

        Label ratingLbl = new Label("How focused were you?");
        ratingLbl.setStyle("-fx-font-weight: bold;");
        HBox stars = new HBox(6);
        final int[] rating = { s.getRating() > 0 ? s.getRating() : 3 };
        Label[] starLabels = new Label[5];
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            starLabels[i] = new Label(i < rating[0] ? "⭐" : "☆");
            starLabels[i].setStyle("-fx-font-size: 26px; -fx-cursor: hand;");
            starLabels[i].setOnMouseClicked(e -> {
                rating[0] = idx + 1;
                for (int j = 0; j < 5; j++) starLabels[j].setText(j < rating[0] ? "⭐" : "☆");
            });
            stars.getChildren().add(starLabels[i]);
        }

        Label helpedLbl = new Label("💡 What helped you focus?");
        helpedLbl.setStyle("-fx-font-weight: bold;");
        TextArea helpedArea = new TextArea();
        helpedArea.setPromptText("Good environment, clear goal, no phone...");
        helpedArea.setPrefRowCount(2); helpedArea.setWrapText(true);

        Label distractedLbl = new Label("⚠ What distracted or slowed you down?");
        distractedLbl.setStyle("-fx-font-weight: bold;");
        TextArea distractedArea = new TextArea();
        distractedArea.setPromptText("Notifications, unclear task, wrong time of day...");
        distractedArea.setPrefRowCount(2); distractedArea.setWrapText(true);

        root.getChildren().addAll(ratingLbl, stars, new Separator(),
                helpedLbl, helpedArea, distractedLbl, distractedArea);
        dialog.getDialogPane().setContent(root);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveType) {
            s.setRating(rating[0]);
            String combined = "";
            if (!helpedArea.getText().isBlank()) combined += "💡 Helped: " + helpedArea.getText().trim() + "\n";
            if (!distractedArea.getText().isBlank()) combined += "⚠ Distracted: " + distractedArea.getText().trim();
            s.setNotes(combined.trim());
            return true;
        }
        return false;
    }

    private void openEditDialog(Session s) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Session");
        dialog.setHeaderText(s.getActivity() + " — " + s.getDurationFormatted());
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20)); root.setPrefWidth(460);
        HBox stars = new HBox(6);
        final int[] rating = { s.getRating() };
        Label[] starLabels = new Label[5];
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            starLabels[i] = new Label(i < rating[0] ? "⭐" : "☆");
            starLabels[i].setStyle("-fx-font-size: 26px; -fx-cursor: hand;");
            starLabels[i].setOnMouseClicked(e -> {
                rating[0] = idx + 1;
                for (int j = 0; j < 5; j++) starLabels[j].setText(j < rating[0] ? "⭐" : "☆");
            });
            stars.getChildren().add(starLabels[i]);
        }
        TextArea notes = new TextArea(s.getNotes());
        notes.setPrefRowCount(5); notes.setWrapText(true);
        root.getChildren().addAll(new Label("Rating:"), stars,
                new Label("Reflection (Helped / Distracted):"), notes);
        dialog.getDialogPane().setContent(root);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveType) {
            s.setRating(rating[0]);
            s.setNotes(notes.getText());
            try { sessionDAO.updateNotesAndRating(s); } catch (SQLException ex) { System.err.println(ex.getMessage()); }
            sessionTable.refresh();
        }
    }

    // ============================================================
    //  EXPORT
    // ============================================================
    @FXML private void onExport() {
        try {
            List<Session> last7 = sessionDAO.getLast7Days();
            StringBuilder sb = new StringBuilder();
            sb.append("PASTM Weekly Report\n");
            sb.append("Generated: ").append(LocalDate.now()).append("\n");
            sb.append("─────────────────────────────────────────\n\n");
            int totalSec = 0;
            for (Session s : last7) {
                sb.append(s.getDate()).append("  ")
                        .append(padRight(s.getActivity(), 20)).append("  ")
                        .append(padRight(s.getCategory(), 12)).append("  ")
                        .append(padRight(s.getDurationFormatted(), 10)).append("  ")
                        .append("rating=").append(s.getRating()).append("/5");
                if (s.isFavorite()) sb.append("  ⭐");
                if (!s.getNotes().isEmpty()) sb.append("\n      ").append(s.getNotes().replace("\n", "\n      "));
                sb.append("\n");
                totalSec += s.getDurationSeconds();
            }
            sb.append("\nTotal: ").append(totalSec / 60).append(" minutes across ")
                    .append(last7.size()).append(" sessions\n");
            String filename = "PASTM_Report_" + LocalDate.now() + ".txt";
            Files.writeString(Paths.get(filename), sb.toString());
            showInfo("Export Complete",
                    "Saved to: " + System.getProperty("user.dir") + "\\" + filename
                            + "\n\n" + last7.size() + " sessions · " + (totalSec / 60) + " minutes");
        } catch (Exception ex) { showWarning("Export failed: " + ex.getMessage()); }
    }

    private String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width - 1) + " ";
        return s + " ".repeat(width - s.length());
    }

    // ============================================================
    //  HEADER ACTIONS
    // ============================================================
    @FXML private void onLightModeToggle() { showInfo("Theme", "Coming soon."); }
    @FXML private void onUserClicked() { showInfo("Profile", "Coming soon."); }

    @FXML private void onRefreshTip() {
        List<Session> snapshot = new ArrayList<>(allSessions);
        Future<String> future = threadManager.submitCalculation(new StatsCalculator(snapshot));
        new Thread(() -> {
            try {
                String stats = future.get();
                Platform.runLater(() -> showInfo("Today's Stats", stats));
            } catch (Exception ex) {
                Platform.runLater(() -> showWarning("Stats failed: " + ex.getMessage()));
            }
        }, "stats-waiter").start();
    }

    public void stopAll() {
        if (sessionTimer != null) { sessionTimer.stopTimer(); sessionTimer = null; }
    }

    private void showInfo(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content); a.showAndWait();
    }

    private void showWarning(String content) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Notice"); a.setHeaderText(null); a.setContentText(content); a.showAndWait();
    }
}