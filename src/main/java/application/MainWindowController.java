package application;

import database.DailyGoalDAO;
import database.JournalDAO;
import database.SessionDAO;
import database.UserProfileDAO;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.util.Duration;
import model.JournalEntry;
import model.Mood;
import model.Session;
import model.Tip;
import model.UserProfile;
import service.ReminderService;
import service.SessionCounter;
import service.StatsCalculator;
import service.ThreadManager;
import service.TimerThread;
import service.TipService;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

public class MainWindowController {

    @FXML private Label clockLabel;
    @FXML private Label statusLabel;
    @FXML private Label dndStatusBarLabel;
    @FXML private Button exportBtn;
    @FXML private Button themeToggleBtn;
    @FXML private Button userBtn;

    @FXML private Button tabScreen, tabStudy, tabSleep, tabJournal;

    @FXML private VBox mainPanel, sessionPanel, sleepPanel, journalPanel;

    @FXML private FlowPane activityChipBox;
    @FXML private TextField activityField;
    @FXML private HBox moodBox;
    @FXML private Button startBtn, pauseBtn;
    @FXML private Label timerLabel;

    // Sleep
    @FXML private TextField sleepHoursField;
    @FXML private TextField sleepMinutesField;
    @FXML private Button sleepSaveBtn, sleepClearBtn;
    @FXML private Label sleepTodayLabel;

    // Nap
    @FXML private TextField napMinutesField;
    @FXML private Label napCountdownLabel;
    @FXML private Button napStartBtn, napCancelBtn;

    // Sleep history
    @FXML private ListView<String> sleepHistoryList;

    @FXML private TableView<Session> sessionTable;
    @FXML private TableColumn<Session, String> colActivity;
    @FXML private TableColumn<Session, String> colCategory;
    @FXML private TableColumn<Session, String> colDuration;
    @FXML private TableColumn<Session, String> colRating;
    @FXML private TableColumn<Session, String> colMood;
    @FXML private TableColumn<Session, String> colFavorite;
    @FXML private CheckBox favoritesFilter;
    @FXML private TextField searchField;

    @FXML private VBox dndCard;
    @FXML private Label dndStatusLabel;
    @FXML private Button dndOnBtn, dndOffBtn;

    @FXML private BarChart<String, Number> weekChart;
    @FXML private ProgressBar goalStudyBar, goalScreenBar, goalSleepBar;
    @FXML private Label goalStudyText, goalScreenText, goalSleepText;
    @FXML private PieChart screenTimePieChart;
    @FXML private Label tipLabel;
    @FXML private Label tipAttributionLabel;
    @FXML private Label streakLabel;

    @FXML private DatePicker journalDatePicker;
    @FXML private TextArea journalMorning, journalNoon, journalAfternoon, journalEvening, journalNight;
    @FXML private Label journalStatus;
    @FXML private Button prepareJournalBtn;

    private final ObservableList<Session> allSessions = FXCollections.observableArrayList();
    private final ObservableList<Session> visibleSessions = FXCollections.observableArrayList();
    private final Map<String, List<String>> activitiesByCategory = new LinkedHashMap<>();

    private final SessionDAO sessionDAO = new SessionDAO();
    private final JournalDAO journalDAO = new JournalDAO();
    private final DailyGoalDAO goalDAO = new DailyGoalDAO();
    private final UserProfileDAO userProfileDAO = new UserProfileDAO();
    private final ReminderService reminderService = new ReminderService();
    private final TipService tipService = new TipService();

    private UserProfile userProfile = new UserProfile();

    private Timeline clockTimeline;

    private TimerThread sessionTimer;
    private Session currentSession;
    private Mood selectedMood = Mood.NEUTRAL;
    private String selectedActivity = null;
    private int elapsedSeconds = 0;
    private String activeCategory = "Screen Time";
    private boolean paused = false;
    private boolean dndActive = false;

    // Nap
    private TimerThread napTimer;
    private int napTargetSeconds = 0;
    private int napElapsedSeconds = 0;
    private boolean napCompleted = false;
    private boolean napRunning = false;

    private final ThreadManager threadManager = ThreadManager.getInstance();
    private PauseTransition journalSaveDebounce;

    private static final int DEFAULT_STUDY_MIN  = 180;
    private static final int DEFAULT_SCREEN_MIN = 120;
    private static final int DEFAULT_SLEEP_MIN  = 420;
    private static final int NAP_MIN_MINUTES = 5;
    private static final int NAP_MAX_MINUTES = 180;

    @FXML
    public void initialize() {
        seedActivities();
        setupHeader();
        setupTable();
        setupMoodPicker();
        setupJournalTab();
        setupSleepFields();
        setupNapFields();
        setupSearch();
        loadFromDatabase();
        loadUserProfile();
        renderActivityChips();
        refreshDashboard();

        activityField.textProperty().addListener((obs, oldV, newV) -> {
            if (!newV.isEmpty() && selectedActivity != null && !newV.equals(selectedActivity)) {
                selectedActivity = null;
                highlightSelectedActivity();
            }
        });

        threadManager.startConsumer(this::onSessionProcessed);
        refreshTable();
        reminderService.start(this::showReminder);
        updateDndUi();

        loadInitialTip();
    }

    private void refreshDashboard() {
        updateWeeklyChart();
        updateGoalsProgress();
        updateScreenTimeByAppChart();
        updateStreak();
    }

    private void setupHeader() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");

        clockLabel.setText("[" + LocalTime.now().format(fmt) + "]");

        clockTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1),
                        e -> clockLabel.setText("[" + LocalTime.now().format(fmt) + "]"))
        );
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();

        statusLabel.setText("● READY");
    }

    private void setupSearch() {
        searchField.textProperty().addListener((o, a, b) -> refreshTable());
    }

    private void setupSleepFields() {
        sleepHoursField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,2}") ? change : null));
        sleepMinutesField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,2}") ? change : null));
    }

    private void setupNapFields() {
        napMinutesField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,3}") ? change : null));
    }

    private void setupTable() {
        colActivity.setCellValueFactory(new PropertyValueFactory<>("activity"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colDuration.setCellValueFactory(new PropertyValueFactory<>("durationFormatted"));
        colRating.setCellValueFactory(new PropertyValueFactory<>("ratingDisplay"));

        colMood.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getMood().getEmoji()));
        colMood.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else {
                    setText(item);
                    setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji'; -fx-font-size: 18px;");
                }
            }
        });

        colFavorite.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().isFavorite() ? "⭐" : "☆"));
        colFavorite.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
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

        sessionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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
    //  USER PROFILE
    // ============================================================
    private void loadUserProfile() {
        try {
            userProfile = userProfileDAO.load();
        } catch (SQLException ex) {
            System.err.println("[Profile] load failed: " + ex.getMessage());
            userProfile = new UserProfile();
        }
        applyTheme(userProfile.getTheme());
        refreshUserButton();
    }

    private void refreshUserButton() {
        if (userBtn != null) userBtn.setText(userProfile.getDisplayLabel());
    }

    @FXML
    private void onUserClicked() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Profile");
        dialog.initModality(Modality.APPLICATION_MODAL);
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setPrefWidth(400);

        TextField avatarF = new TextField(userProfile.getAvatar());
        avatarF.setPrefWidth(80);
        avatarF.setPromptText("👤");

        TextField nameF  = new TextField(userProfile.getName());
        TextField emailF = new TextField(userProfile.getEmail());
        TextArea mottoA  = new TextArea(userProfile.getMotto());
        mottoA.setPrefRowCount(3);
        mottoA.setWrapText(true);
        mottoA.setPromptText("A short line that keeps you going...");

        HBox quickPicks = new HBox(6);
        for (String e : new String[]{"👤","🦊","🐼","🐨","🐯","🦁","🐸","🐵","🦉","🐺","🤖","🧑‍💻"}) {
            Button eb = new Button(e);
            eb.setStyle("-fx-font-size: 18px; -fx-cursor: hand; -fx-background-color: transparent;");
            eb.setOnAction(ev -> avatarF.setText(e));
            quickPicks.getChildren().add(eb);
        }

        root.getChildren().addAll(
                new Label("Avatar:"), avatarF, quickPicks,
                new Label("Name:"), nameF,
                new Label("Email:"), emailF,
                new Label("Motto:"), mottoA);

        dialog.getDialogPane().setContent(root);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveType) {
            userProfile.setAvatar(avatarF.getText());
            userProfile.setName(nameF.getText());
            userProfile.setEmail(emailF.getText());
            userProfile.setMotto(mottoA.getText());
            try {
                userProfileDAO.save(userProfile);
                refreshUserButton();
                showInfo("Profile Saved", "Your profile has been updated.");
            } catch (SQLException ex) {
                showWarning("Save failed: " + ex.getMessage());
            }
        }
    }

    // ============================================================
    //  THEME TOGGLE
    // ============================================================
    @FXML
    private void onLightModeToggle() {
        String next = userProfile.isDark() ? "light" : "dark";
        userProfile.setTheme(next);
        try { userProfileDAO.saveTheme(next); }
        catch (SQLException ex) { System.err.println("[Theme] save failed: " + ex.getMessage()); }
        applyTheme(next);
    }

    private void applyTheme(String theme) {
        if (themeToggleBtn == null || themeToggleBtn.getScene() == null) return;

        var sheets = themeToggleBtn.getScene().getStylesheets();
        String base  = getClass().getResource("/application/style.css").toExternalForm();
        String light = getClass().getResource("/application/light.css").toExternalForm();

        sheets.clear();
        sheets.add(base);
        if ("light".equalsIgnoreCase(theme)) {
            sheets.add(light);
        }

        themeToggleBtn.setText("light".equalsIgnoreCase(theme) ? "[Dark]" : "[Light]");
    }

    // ============================================================
    //  NAP TIMER
    // ============================================================
    @FXML
    private void onStartNap() {
        if (napRunning) return;
        String txt = napMinutesField.getText();
        if (txt == null || txt.isBlank()) { showWarning("Please enter nap duration in minutes."); return; }
        int minutes;
        try { minutes = Integer.parseInt(txt.trim()); }
        catch (NumberFormatException ex) { showWarning("Invalid number."); return; }

        if (minutes < NAP_MIN_MINUTES || minutes > NAP_MAX_MINUTES) {
            showWarning("Nap duration must be between " + NAP_MIN_MINUTES + " and " + NAP_MAX_MINUTES + " minutes.");
            return;
        }

        napTargetSeconds = minutes * 60;
        napElapsedSeconds = 0;
        napCompleted = false;
        napRunning = true;

        napMinutesField.setDisable(true);
        napStartBtn.setDisable(true);
        napCancelBtn.setVisible(true);
        napCancelBtn.setManaged(true);
        updateNapCountdownLabel();
        statusLabel.setText("● NAP RUNNING — " + minutes + " min");

        napTimer = new TimerThread("nap-timer", 0, new TimerThread.TickListener() {
            @Override
            public void onTick(int s) {
                napElapsedSeconds = s;
                updateNapCountdownLabel();
                if (!napCompleted && s >= napTargetSeconds) {
                    napCompleted = true;
                    completeNap();
                }
            }
            @Override public void onFinish(int s) { }
        });
        napTimer.start();
    }

    private void updateNapCountdownLabel() {
        int remaining = napTargetSeconds - napElapsedSeconds;
        if (remaining < 0) remaining = 0;
        napCountdownLabel.setText(String.format("%02d:%02d", remaining / 60, remaining % 60));
    }

    @FXML
    private void onCancelNap() {
        if (!napRunning) return;
        Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Cancel the running nap? It won't be saved.",
                ButtonType.OK, ButtonType.CANCEL);
        if (c.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        stopNapTimer();
        resetNapUI();
        statusLabel.setText("● READY");
        napCountdownLabel.setText("--:--");
        showInfo("Nap Cancelled", "The nap was cancelled and not saved.");
    }

    private void completeNap() {
        stopNapTimer();
        int napMinutes = napTargetSeconds / 60;

        Session nap = new Session("Power Nap", "Sleep Time", Mood.CONTENT);
        nap.setDurationSeconds(napTargetSeconds);
        nap.setRating(4);
        nap.setNotes("Auto-logged nap (" + napMinutes + " min)");
        nap.setStartHour(LocalTime.now().getHour());

        try {
            sessionDAO.insert(nap);
            allSessions.add(nap);
            refreshTable();
            refreshDashboard();
            refreshSleepHistory();

            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Nap Complete");
            a.setHeaderText("😴  Nap finished!");
            a.setContentText("You slept for " + napMinutes + " minutes.\nIt has been added to your Sleep Time activities.");
            a.showAndWait();
        } catch (SQLException ex) {
            showWarning("Failed to save nap: " + ex.getMessage());
        }

        resetNapUI();
        statusLabel.setText("● READY");
        napCountdownLabel.setText("00:00");
    }

    private void stopNapTimer() {
        napRunning = false;
        if (napTimer != null) { napTimer.stopTimer(); napTimer = null; }
    }

    private void resetNapUI() {
        napMinutesField.setDisable(false);
        napStartBtn.setDisable(false);
        napCancelBtn.setVisible(false);
        napCancelBtn.setManaged(false);
    }

    // ============================================================
    //  SLEEP HISTORY
    // ============================================================
    private void refreshSleepHistory() {
        if (sleepHistoryList == null) return;
        List<Session> sleepSessions = new ArrayList<>();
        LocalDate cutoff = LocalDate.now().minusDays(13);
        for (Session s : allSessions) {
            if (!"Sleep Time".equals(s.getCategory())) continue;
            if (s.getDate().isBefore(cutoff)) continue;
            sleepSessions.add(s);
        }
        sleepSessions.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        ObservableList<String> items = FXCollections.observableArrayList();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, dd MMM");
        for (Session s : sleepSessions) {
            items.add(String.format("%-16s   %-10s   %s",
                    s.getDate().format(fmt),
                    s.getDurationFormatted(),
                    s.getActivity()));
        }
        if (items.isEmpty()) items.add("No sleep entries in the last 14 days.");
        sleepHistoryList.setItems(items);
    }

    // ============================================================
    //  DND
    // ============================================================
    @FXML
    private void onDndOn() {
        if (dndActive) return;
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                "Turn ON Do Not Disturb?\n\nOnly the Study Time tab will remain accessible.",
                ButtonType.OK, ButtonType.CANCEL);
        if (c.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        dndActive = true;
        updateDndUi();
        switchTab("Study Time", tabStudy);
        showInfo("Do Not Disturb", "DND mode is now ON.\nOnly Study Time is accessible.");
    }

    @FXML
    private void onDndOff() {
        if (!dndActive) return;
        dndActive = false;
        updateDndUi();
        showInfo("Do Not Disturb", "DND mode is now OFF.\nAll tabs are accessible again.");
    }

    private void updateDndUi() {
        if (dndActive) {
            dndStatusLabel.setText("ACTIVE");
            dndStatusLabel.getStyleClass().removeAll("dnd-status-off", "dnd-status-on");
            dndStatusLabel.getStyleClass().add("dnd-status-on");
            dndCard.getStyleClass().removeAll("dnd-off", "dnd-on");
            dndCard.getStyleClass().add("dnd-on");
            dndOnBtn.setDisable(true);
            dndOffBtn.setDisable(false);
            tabScreen.setDisable(true);
            tabSleep.setDisable(true);
            tabJournal.setDisable(true);
            dndStatusBarLabel.setText("🔴 DND ACTIVE");
            dndStatusBarLabel.setVisible(true);
            dndStatusBarLabel.setManaged(true);
        } else {
            dndStatusLabel.setText("OFF");
            dndStatusLabel.getStyleClass().removeAll("dnd-status-off", "dnd-status-on");
            dndStatusLabel.getStyleClass().add("dnd-status-off");
            dndCard.getStyleClass().removeAll("dnd-off", "dnd-on");
            dndCard.getStyleClass().add("dnd-off");
            dndOnBtn.setDisable(false);
            dndOffBtn.setDisable(true);
            tabScreen.setDisable(false);
            tabSleep.setDisable(false);
            tabJournal.setDisable(false);
            dndStatusBarLabel.setVisible(false);
            dndStatusBarLabel.setManaged(false);
        }
    }

    private boolean blockIfDnd(String targetCategory) {
        if (!dndActive) return false;
        if ("Study Time".equals(targetCategory)) return false;
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("DND Active");
        a.setHeaderText("🚫  Do Not Disturb is ON");
        a.setContentText("Only Study Time is accessible.\nTurn DND OFF from the right panel.");
        a.showAndWait();
        return true;
    }

    // ============================================================
    //  PREPARE JOURNAL
    // ============================================================
    @FXML
    private void onPrepareJournal() {
        LocalDate date = journalDatePicker.getValue();
        if (date == null) date = LocalDate.now();

        List<Session> daySessions = new ArrayList<>();
        for (Session s : allSessions) if (s.getDate().equals(date)) daySessions.add(s);
        daySessions.sort(Comparator.comparingInt(Session::getStartHour));

        JournalEntry entry;
        try { entry = journalDAO.getByDate(date); }
        catch (SQLException ex) { entry = new JournalEntry(date); }

        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");

        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("              PASTM  —  DAILY JOURNAL REPORT\n");
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("  User: ").append(userProfile.getName().isBlank() ? "(unnamed)" : userProfile.getName()).append("\n");
        sb.append("  Date: ").append(date.format(dateFmt)).append("\n");
        sb.append("  Generated: ").append(LocalDate.now()).append("\n\n");

        int studySec = 0, screenSec = 0, sleepSec = 0;
        for (Session s : daySessions) {
            switch (s.getCategory()) {
                case "Study Time":  studySec  += s.getDurationSeconds(); break;
                case "Screen Time": screenSec += s.getDurationSeconds(); break;
                case "Sleep Time":  sleepSec  += s.getDurationSeconds(); break;
            }
        }

        sb.append("─── TIME BUDGET ───────────────────────────────────────\n");
        sb.append(String.format("  Study Time:  %s%n",  fmtSec(studySec)));
        sb.append(String.format("  Screen Time: %s%n",  fmtSec(screenSec)));
        sb.append(String.format("  Sleep Time:  %s%n",  fmtSec(sleepSec)));
        sb.append("\n");

        List<String> order = Arrays.asList("Study Time", "Screen Time", "Sleep Time");
        for (String cat : order) {
            List<Session> bucket = new ArrayList<>();
            for (Session s : daySessions) if (s.getCategory().equals(cat)) bucket.add(s);
            if (bucket.isEmpty()) continue;

            sb.append("─── ").append(cat.toUpperCase()).append(" ─────────────────────────────────\n");
            for (Session s : bucket) {
                sb.append("  • ").append(padRight(s.getActivity(), 22))
                        .append(padRight(s.getDurationFormatted(), 12))
                        .append("rating=").append(s.getRating()).append("/5");
                if (s.isFavorite()) sb.append("  ⭐");
                sb.append("\n");
                if (!s.getNotes().isEmpty()) {
                    for (String line : s.getNotes().split("\n")) {
                        sb.append("        ").append(line).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        sb.append("─── JOURNAL REFLECTIONS ────────────────────────────────\n");
        if (!entry.getMorning().isBlank())   sb.append("  MORNING:   ").append(entry.getMorning()).append("\n");
        if (!entry.getNoon().isBlank())      sb.append("  NOON:      ").append(entry.getNoon()).append("\n");
        if (!entry.getAfternoon().isBlank()) sb.append("  AFTERNOON: ").append(entry.getAfternoon()).append("\n");
        if (!entry.getEvening().isBlank())   sb.append("  EVENING:   ").append(entry.getEvening()).append("\n");
        if (!entry.getNight().isBlank())     sb.append("  NIGHT:     ").append(entry.getNight()).append("\n");
        if (entry.getTotalWords() == 0)      sb.append("  (No reflections recorded.)\n");

        sb.append("\n═══════════════════════════════════════════════════════\n");
        sb.append("  End of report — ").append(daySessions.size()).append(" sessions logged.\n");
        sb.append("═══════════════════════════════════════════════════════\n");

        String filename = "PASTM_Journal_" + date + ".txt";
        try {
            Files.writeString(Paths.get(filename), sb.toString());
            showInfo("Journal Prepared",
                    "Saved to: " + System.getProperty("user.dir") + "\\" + filename
                            + "\n\n" + daySessions.size() + " sessions · " + entry.getTotalWords() + " words");
        } catch (Exception ex) {
            showWarning("Failed to write journal: " + ex.getMessage());
        }
    }

    // ============================================================
    //  WEEKLY CHART
    // ============================================================
    private void updateWeeklyChart() {
        weekChart.getData().clear();
        try {
            Map<String, Map<String, Integer>> data = sessionDAO.getDailyTotalsLast7Days();
            XYChart.Series<String, Number> studySeries  = new XYChart.Series<>(); studySeries.setName("Study");
            XYChart.Series<String, Number> screenSeries = new XYChart.Series<>(); screenSeries.setName("Screen");
            XYChart.Series<String, Number> sleepSeries  = new XYChart.Series<>(); sleepSeries.setName("Sleep");

            DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE");
            for (int i = 6; i >= 0; i--) {
                LocalDate d = LocalDate.now().minusDays(i);
                String key = d.toString();
                String label = (i == 0) ? "Today" : d.format(dayFmt);
                Map<String, Integer> row = data.getOrDefault(key, Map.of());
                double study  = row.getOrDefault("Study Time",  0) / 3600.0;
                double screen = row.getOrDefault("Screen Time", 0) / 3600.0;
                double sleep  = row.getOrDefault("Sleep Time",  0) / 3600.0;
                studySeries.getData().add(new XYChart.Data<>(label, study));
                screenSeries.getData().add(new XYChart.Data<>(label, screen));
                sleepSeries.getData().add(new XYChart.Data<>(label, sleep));
            }
            weekChart.getData().addAll(studySeries, screenSeries, sleepSeries);
            Platform.runLater(() -> {
                styleSeries(studySeries,  "#d97706");
                styleSeries(screenSeries, "#ef4444");
                styleSeries(sleepSeries,  "#8052d2");
            });
        } catch (SQLException ex) { System.err.println("[Chart] weekly failed: " + ex.getMessage()); }
    }

    private void styleSeries(XYChart.Series<String, Number> series, String color) {
        for (XYChart.Data<String, Number> data : series.getData())
            if (data.getNode() != null) data.getNode().setStyle("-fx-bar-fill: " + color + ";");
    }

    // ============================================================
    //  GOALS
    // ============================================================
    private void updateGoalsProgress() {
        try {
            Map<String, Integer> goals = goalDAO.getByDate(LocalDate.now());
            int studyTarget  = goals.getOrDefault("Study Time",  DEFAULT_STUDY_MIN);
            int screenTarget = goals.getOrDefault("Screen Time", DEFAULT_SCREEN_MIN);
            int sleepTarget  = goals.getOrDefault("Sleep Time",  DEFAULT_SLEEP_MIN);

            int studyActual = 0, screenActual = 0, sleepActual = 0;
            LocalDate today = LocalDate.now();
            for (Session s : allSessions) {
                if (!s.getDate().equals(today)) continue;
                switch (s.getCategory()) {
                    case "Study Time":  studyActual  += s.getDurationSeconds(); break;
                    case "Screen Time": screenActual += s.getDurationSeconds(); break;
                    case "Sleep Time":  sleepActual  += s.getDurationSeconds(); break;
                }
            }
            applyGoal(goalStudyBar,  goalStudyText,  studyActual,  studyTarget, false);
            applyGoal(goalScreenBar, goalScreenText, screenActual, screenTarget, true);
            applyGoal(goalSleepBar,  goalSleepText,  sleepActual,  sleepTarget, false);
        } catch (SQLException ex) { System.err.println("[Goals] load failed: " + ex.getMessage()); }
    }

    private void applyGoal(ProgressBar bar, Label label, int currentSec, int targetMin, boolean isMaxGoal) {
        int currentMin = currentSec / 60;
        double ratio = targetMin > 0 ? (double) currentMin / targetMin : 0;
        bar.setProgress(Math.min(1.0, ratio));
        label.setText(formatMinutes(currentMin) + " / " + formatMinutes(targetMin));
        bar.getStyleClass().removeAll("goal-ok", "goal-warn", "goal-bad");
        if (isMaxGoal) {
            if (ratio >= 1.0) bar.getStyleClass().add("goal-bad");
            else if (ratio >= 0.8) bar.getStyleClass().add("goal-warn");
            else bar.getStyleClass().add("goal-ok");
        } else {
            if (ratio >= 1.0) bar.getStyleClass().add("goal-ok");
            else if (ratio >= 0.5) bar.getStyleClass().add("goal-warn");
            else bar.getStyleClass().add("goal-bad");
        }
    }

    private String formatMinutes(int min) {
        if (min < 60) return min + "m";
        return (min / 60) + "h " + String.format("%02d", min % 60) + "m";
    }

    @FXML
    private void onEditGoals() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Daily Goals");
        dialog.initModality(Modality.APPLICATION_MODAL);
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setPrefWidth(380);

        Map<String, Integer> current = Map.of();
        try { current = goalDAO.getByDate(LocalDate.now()); } catch (SQLException ignored) {}

        TextField studyF  = new TextField(String.valueOf(current.getOrDefault("Study Time",  DEFAULT_STUDY_MIN)));
        TextField screenF = new TextField(String.valueOf(current.getOrDefault("Screen Time", DEFAULT_SCREEN_MIN)));
        TextField sleepF  = new TextField(String.valueOf(current.getOrDefault("Sleep Time",  DEFAULT_SLEEP_MIN)));

        for (TextField f : List.of(studyF, screenF, sleepF)) {
            f.setTextFormatter(new TextFormatter<String>(change ->
                    change.getControlNewText().matches("\\d{0,4}") ? change : null));
        }

        root.getChildren().addAll(
                new Label("Study Goal (minutes):"), studyF,
                new Label("Screen Limit (minutes):"), screenF,
                new Label("Sleep Target (minutes):"), sleepF);
        dialog.getDialogPane().setContent(root);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveType) {
            try {
                goalDAO.saveAll(LocalDate.now(),
                        parseInt(studyF.getText(), DEFAULT_STUDY_MIN),
                        parseInt(screenF.getText(), DEFAULT_SCREEN_MIN),
                        parseInt(sleepF.getText(), DEFAULT_SLEEP_MIN));
                updateGoalsProgress();
                showInfo("Saved", "Daily goals updated.");
            } catch (SQLException ex) { showWarning("Save failed: " + ex.getMessage()); }
        }
    }

    private int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    // ============================================================
    //  PIE CHART
    // ============================================================
    private void updateScreenTimeByAppChart() {
        Map<String, Integer> appSeconds = new LinkedHashMap<>();
        int totalScreenSeconds = 0;
        LocalDate today = LocalDate.now();
        for (Session s : allSessions) {
            if (!"Screen Time".equals(s.getCategory())) continue;
            if (!s.getDate().equals(today)) continue;
            appSeconds.merge(s.getActivity(), s.getDurationSeconds(), Integer::sum);
            totalScreenSeconds += s.getDurationSeconds();
        }
        screenTimePieChart.getData().clear();
        if (totalScreenSeconds == 0) {
            PieChart.Data placeholder = new PieChart.Data("No screen time yet", 1);
            screenTimePieChart.getData().add(placeholder);
            Platform.runLater(() -> {
                if (placeholder.getNode() != null)
                    placeholder.getNode().setStyle("-fx-pie-color: #94a3b8;");
            });
            return;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(appSeconds.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Integer> e : entries) {
            double pct = e.getValue() * 100.0 / totalScreenSeconds;
            String label = String.format("%s (%.0f%%)", e.getKey(), pct);
            double minutes = e.getValue() / 60.0;
            PieChart.Data slice = new PieChart.Data(label, minutes);
            screenTimePieChart.getData().add(slice);
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

    private String colorForApp(String app) {
        String[] palette = {"#d97706","#10b981","#3b82f6","#ef4444","#8052d2",
                "#f59e0b","#06b6d4","#ec4899","#84cc16","#6366f1"};
        return palette[Math.abs(app.hashCode()) % palette.length];
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
        } catch (SQLException ex) { journalStatus.setText("● load failed"); }
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
        } catch (SQLException ex) { journalStatus.setText("● save failed"); }
    }

    // ============================================================
    //  SEED / LOAD
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
        } catch (SQLException ex) { System.err.println("[Main] DB load failed: " + ex.getMessage()); }
    }

    private void onSessionProcessed() {
        SessionCounter c = threadManager.getCounter();
        statusLabel.setText("● " + c.getTotalSessions() + " PROCESSED");
        refreshDashboard();
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
                    if (!b.getStyleClass().contains("activity-selected"))
                        b.getStyleClass().add("activity-selected");
                }
            }
        }
    }

    // ============================================================
    //  TAB HANDLERS
    // ============================================================
    @FXML private void onTabScreen() {
        if (blockIfDnd("Screen Time")) return;
        switchTab("Screen Time", tabScreen);
    }

    @FXML private void onTabStudy() {
        switchTab("Study Time", tabStudy);
    }

    @FXML private void onTabSleep() {
        if (blockIfDnd("Sleep Time")) return;
        activeCategory = "Sleep Time";
        setActiveTab(tabSleep);
        mainPanel.setVisible(true); mainPanel.setManaged(true);
        sessionPanel.setVisible(false); sessionPanel.setManaged(false);
        sleepPanel.setVisible(true); sleepPanel.setManaged(true);
        journalPanel.setVisible(false); journalPanel.setManaged(false);
        refreshTable();
        loadSleepForToday();
        refreshSleepHistory();
    }

    @FXML private void onTabJournal() {
        if (blockIfDnd("Journal")) return;
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
        if (!active.getStyleClass().contains("tab-active"))
            active.getStyleClass().add("tab-active");
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
        for (Session s : allSessions)
            if ("Sleep Time".equals(s.getCategory()) && s.getDate().equals(today)) return s;
        return null;
    }

    @FXML private void onSaveSleep() {
        int hours = parseInt(sleepHoursField.getText(), 0);
        int minutes = parseInt(sleepMinutesField.getText(), 0);
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
            refreshDashboard();
            refreshSleepHistory();
            showInfo("Saved", "Sleep logged: " + sleep.getDurationFormatted());
        } catch (SQLException ex) { showWarning("Save failed: " + ex.getMessage()); }
    }

    @FXML private void onClearSleep() {
        Session existing = findTodaySleep();
        if (existing == null) { showInfo("Nothing to clear", "No sleep entry for today."); return; }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Delete today's sleep entry?", ButtonType.OK, ButtonType.CANCEL);
        if (c.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                sessionDAO.delete(existing);
                allSessions.remove(existing);
                refreshTable();
                loadSleepForToday();
                refreshDashboard();
                refreshSleepHistory();
                showInfo("Cleared", "Today's sleep entry removed.");
            } catch (SQLException ex) { showWarning("Clear failed: " + ex.getMessage()); }
        }
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
            timerLabel.getStyleClass().add("timer-label-paused");
            statusLabel.setText("● PAUSED");
        } else {
            paused = false;
            pauseBtn.setText("⏸ PAUSE");
            timerLabel.getStyleClass().remove("timer-label-paused");
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
            refreshDashboard();
        } else {
            paused = false;
            pauseBtn.setText("⏸ PAUSE");
            startTimer();
            statusLabel.setText("● RECORDING " + currentSession.getActivity().toUpperCase());
        }
    }

    private void resetSessionUI() {
        timerLabel.setText("00:00:00");
        timerLabel.getStyleClass().remove("timer-label-paused");
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

    private boolean showReflectionDialog(Session s) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Session Complete");
        dialog.setHeaderText(s.getActivity() + " — " + s.getDurationFormatted());
        ButtonType saveType = new ButtonType("Save Session", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setPrefWidth(460);

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
        root.setPadding(new Insets(20));
        root.setPrefWidth(460);
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
            if (!userProfile.getName().isBlank())
                sb.append("User: ").append(userProfile.getName()).append("\n");
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
                if (!s.getNotes().isEmpty())
                    sb.append("\n      ").append(s.getNotes().replace("\n", "\n      "));
                sb.append("\n");
                totalSec += s.getDurationSeconds();
            }
            sb.append("\nTotal: ").append(totalSec / 60).append(" minutes across ")
                    .append(last7.size()).append(" sessions\n");
            String filename = "PASTM_Report_" + LocalDate.now() + ".txt";
            Files.writeString(Paths.get(filename), sb.toString());
            showInfo("Export Complete",
                    "Saved to: " + System.getProperty("user.dir") + "\\" + filename);
        } catch (Exception ex) { showWarning("Export failed: " + ex.getMessage()); }
    }

    private String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width - 1) + " ";
        return s + " ".repeat(width - s.length());
    }

    // ============================================================
    //  DAILY TIP — JSON FEATURE
    // ============================================================
    private void loadInitialTip() {
        new Thread(() -> {
            Tip tip = tipService.fetchRandomTip();
            Platform.runLater(() -> showTip(tip));
        }, "tip-loader").start();
    }

    @FXML
    private void onRefreshTip() {
        new Thread(() -> {
            Tip tip = tipService.fetchRandomTip();
            Platform.runLater(() -> showTip(tip));
        }, "tip-fetcher").start();
    }

    private void showTip(Tip tip) {
        if (tip == null) return;
        tipLabel.setText("\"" + tip.getContent() + "\"");
        String author = tip.getAuthor() == null ? "UNKNOWN" : tip.getAuthor().toUpperCase();
        tipAttributionLabel.setText("— " + author);
    }

    // ============================================================
    //  REMINDER CALLBACK
    // ============================================================
    private void showReminder(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("PASTM Reminder");
        alert.setHeaderText("⏰  " + title);
        alert.setContentText(message);
        alert.initModality(Modality.NONE);
        alert.show();
    }

    // ============================================================
    //  LIFECYCLE
    // ============================================================
    public void stopAll() {
        if (clockTimeline != null) { clockTimeline.stop(); clockTimeline = null; }
        if (sessionTimer != null) { sessionTimer.stopTimer(); sessionTimer = null; }
        if (napTimer != null) { napTimer.stopTimer(); napTimer = null; }
        reminderService.shutdown();
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