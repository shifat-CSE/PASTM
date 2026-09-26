package application;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import model.Mood;
import model.Session;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Week 3: Main controller.
 * v2: Activity chips replace the free-text typing flow — click to select like v1.
 */
public class MainWindowController {

    // ---- Header ----
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;

    // ---- Tab bar ----
    @FXML private Button tabScreen, tabStudy, tabSleep, tabJournal;

    // ---- New Session panel ----
    @FXML private FlowPane activityChipBox;
    @FXML private TextField activityField;
    @FXML private HBox moodBox;
    @FXML private Button startBtn;
    @FXML private Label timerLabel;

    // ---- Table ----
    @FXML private TableView<Session> sessionTable;
    @FXML private TableColumn<Session, String> colActivity;
    @FXML private TableColumn<Session, String> colCategory;
    @FXML private TableColumn<Session, String> colDuration;
    @FXML private TableColumn<Session, String> colRating;
    @FXML private TableColumn<Session, String> colMood;

    // ---- Right panel ----
    @FXML private BarChart<String, Number> timeOfDayChart;
    @FXML private Label tipLabel;

    // ---- Data ----
    private final ObservableList<Session> allSessions = FXCollections.observableArrayList();
    private final ObservableList<Session> visibleSessions = FXCollections.observableArrayList();

    /**
     * Category → list of predefined activities.
     * This is the "app list" that replaces v1's icon grid.
     * Phase 3 (SQLite) will make this user-editable.
     */
    private final Map<String, List<String>> activitiesByCategory = new LinkedHashMap<>();

    private Session currentSession;
    private Mood selectedMood = Mood.NEUTRAL;
    private String selectedActivity = null;
    private Timeline runningTimer;
    private int elapsedSeconds = 0;
    private String activeCategory = "Screen Time";

    // ============================================================
    //  INIT
    // ============================================================
    @FXML
    public void initialize() {
        seedActivities();
        setupHeader();
        setupTable();
        setupMoodPicker();
        loadSampleData();
        configureBarChart();

        // Render chips for the initially active tab
        renderActivityChips();

        // When the user types a custom name, deselect any chip
        activityField.textProperty().addListener((obs, oldV, newV) -> {
            if (!newV.isEmpty() && selectedActivity != null && !newV.equals(selectedActivity)) {
                selectedActivity = null;
                highlightSelectedActivity();
            }
        });

        refreshTable();
    }

    /** The activity lists — one per category. */
    private void seedActivities() {
        activitiesByCategory.put("Screen Time", Arrays.asList(
                "YouTube", "Facebook", "Instagram", "WhatsApp",
                "Chrome", "TikTok", "Twitter", "Reddit"
        ));
        activitiesByCategory.put("Study Time", Arrays.asList(
                "Java", "DSA", "Math", "Physics",
                "Chemistry", "English", "Revision", "Assignment"
        ));
        activitiesByCategory.put("Sleep Time", Arrays.asList(
                "Night Sleep", "Nap", "Rest"
        ));
    }

    private void setupHeader() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM");
        dateLabel.setText("[Date: " + LocalDate.now().format(fmt) + "]");
        statusLabel.setText("● READY");
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
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji'; " +
                            "-fx-font-size: 20px; -fx-alignment: CENTER-LEFT;");
                }
            }
        });

        sessionTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        sessionTable.setItems(visibleSessions);
    }

    private void setupMoodPicker() {
        moodBox.getChildren().clear();
        for (Mood m : Mood.values()) {
            Button b = new Button(m.getEmoji());
            b.getStyleClass().add("mood-button");
            b.setTooltip(new Tooltip(m.getLabel()));
            b.setOnAction(e -> {
                selectedMood = m;
                highlightSelectedMood();
            });
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

    /** Rebuilds the activity chip row based on active category. */
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

        // Reset selection when the tab changes
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

    private void loadSampleData() {
        allSessions.add(new Session("youtube", "Screen Time",
                42 * 60 + 15, 4, Mood.HAPPY, "", false, LocalDate.now()));
        allSessions.add(new Session("whatsapp", "Screen Time",
                15 * 60 + 3, 3, Mood.CONTENT, "", false, LocalDate.now()));
        allSessions.add(new Session("java revision", "Study Time",
                80 * 60, 5, Mood.HAPPY, "", true, LocalDate.now()));
        allSessions.add(new Session("sleep log", "Sleep Time",
                7 * 3600 + 30 * 60, 4, Mood.CONTENT, "", false, LocalDate.now()));
    }

    private void configureBarChart() {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("MORN", 1.5));
        series.getData().add(new XYChart.Data<>("AFTN", 3.6));
        series.getData().add(new XYChart.Data<>("EVE", 2.0));
        series.getData().add(new XYChart.Data<>("NIGHT", 1.0));
        timeOfDayChart.getData().add(series);
        timeOfDayChart.setLegendVisible(false);
        timeOfDayChart.setAnimated(false);
    }

    // ============================================================
    //  TAB HANDLERS
    // ============================================================
    @FXML private void onTabScreen() { switchTab("Screen Time", tabScreen); }
    @FXML private void onTabStudy()  { switchTab("Study Time", tabStudy); }
    @FXML private void onTabSleep()  { switchTab("Sleep Time", tabSleep); }

    @FXML private void onTabJournal() {
        activeCategory = "Journal";
        setActiveTab(tabJournal);
        visibleSessions.clear();
        activityChipBox.getChildren().clear();
        sessionTable.setPlaceholder(new Label("Journal view — coming in Phase 2"));
    }

    private void switchTab(String category, Button tab) {
        activeCategory = category;
        setActiveTab(tab);
        sessionTable.setPlaceholder(new Label("No sessions in " + category + " yet."));
        renderActivityChips();   // rebuild chips for the new category
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
        for (Session s : allSessions) {
            if (s.getCategory().equals(activeCategory)) {
                visibleSessions.add(s);
            }
        }
    }

    // ============================================================
    //  SESSION LIFECYCLE
    // ============================================================
    @FXML
    private void onStartSession() {
        if (currentSession != null) {
            stopSession();
            return;
        }

        String activity = activityField.getText().trim();
        if (activity.isEmpty()) {
            showWarning("Please select an activity or type a custom name.");
            return;
        }

        // Use the category from the active tab (no more dropdown)
        currentSession = new Session(activity, activeCategory, selectedMood);
        elapsedSeconds = 0;

        startBtn.setText("■ STOP SESSION");
        startBtn.getStyleClass().add("stop-button");
        statusLabel.setText("● RECORDING " + activity.toUpperCase());
        activityField.setDisable(true);
        activityChipBox.setDisable(true);

        startRunningTimer();
    }

    private void startRunningTimer() {
        runningTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            elapsedSeconds++;
            updateTimerDisplay();
        }));
        runningTimer.setCycleCount(Timeline.INDEFINITE);
        runningTimer.play();
        updateTimerDisplay();
    }

    private void updateTimerDisplay() {
        int h = elapsedSeconds / 3600;
        int m = (elapsedSeconds % 3600) / 60;
        int s = elapsedSeconds % 60;
        timerLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    private void stopSession() {
        if (runningTimer != null) runningTimer.stop();

        currentSession.setDurationSeconds(elapsedSeconds);
        allSessions.add(currentSession);

        timerLabel.setText("00:00:00");
        activityField.clear();
        activityField.setDisable(false);
        activityChipBox.setDisable(false);
        startBtn.setText("▶ START SESSION");
        startBtn.getStyleClass().remove("stop-button");
        statusLabel.setText("● READY");

        currentSession = null;
        elapsedSeconds = 0;
        selectedActivity = null;
        highlightSelectedActivity();
        refreshTable();
    }

    // ============================================================
    //  HEADER ACTIONS
    // ============================================================
    @FXML
    private void onLightModeToggle() {
        showInfo("Theme", "Dark-mode toggle will be wired in Phase 2 (Week 4).");
    }

    @FXML
    private void onUserClicked() {
        showInfo("Profile", "User profile editor comes in Phase 2 (Week 4).");
    }

    @FXML
    private void onRefreshTip() {
        tipLabel.setText("\u201CDiscipline equals freedom.\u201D");
    }

    // ============================================================
    //  HELPERS
    // ============================================================
    private void showInfo(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content); a.showAndWait();
    }

    private void showWarning(String content) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Missing information"); a.setHeaderText(null); a.setContentText(content); a.showAndWait();
    }
}