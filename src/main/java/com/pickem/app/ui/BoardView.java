package com.pickem.app.ui;

import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.repository.PlayerRecordRepository;
import com.pickem.app.repository.PlayerRepository;
import com.pickem.app.service.ConferenceService;
import com.pickem.app.service.GameSyncService;
import com.pickem.app.service.GradingService;
import com.pickem.app.service.OddsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.TabSheetVariant;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Route("")
@CssImport("./styles.css")
public class BoardView extends VerticalLayout {

    private final PlayerRepository playerRepo;
    private final PickRepository pickRepo;
    private final OddsService oddsService;
    private final ConferenceService conferenceService;
    private final GradingService gradingService;
    private final GameSyncService gameSyncService;
    private final PlayerRecordRepository playerRecordRepo;

    // 2. Update the constructor to accept the new service
    public BoardView(PlayerRepository playerRepo, PickRepository pickRepo, OddsService oddsService, ConferenceService conferenceService, GradingService gradingService, GameSyncService gameSyncService, PlayerRecordRepository playerRecordRepo, PlayerRecordRepository playerRecordRepo1) {
        this.playerRepo = playerRepo;
        this.pickRepo = pickRepo;
        this.oddsService = oddsService;
        this.conferenceService = conferenceService;
        this.gradingService = gradingService; // 3. Assign it
        this.gameSyncService = gameSyncService;
        this.playerRecordRepo = playerRecordRepo1;

        getElement().setAttribute("theme", Lumo.DARK);
        setSizeFull();
        setPadding(true);
        getStyle().set("background-color", "#0b0f19");

        renderTabs();
    }

    private void renderTabs() {
        removeAll();

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.addThemeVariants(TabSheetVariant.LUMO_TABS_CENTERED, TabSheetVariant.LUMO_BORDERED);

        // 1. College Football tab label with absolute static path
        Image collegeLogo = new Image("/images/cfp.png", "College Football Logo");
        collegeLogo.setWidth("20px");
        collegeLogo.setHeight("20px");
        collegeLogo.getStyle().set("margin-right", "8px");

        HorizontalLayout collegeTabLabel = new HorizontalLayout(collegeLogo, new Span("College Football"));
        collegeTabLabel.setAlignItems(Alignment.CENTER);
        collegeTabLabel.setSpacing(false);

        // 2. NFL tab label with absolute static path
        Image nflLogo = new Image("/images/nfl.png", "NFL Logo");
        nflLogo.setWidth("20px");
        nflLogo.setHeight("20px");
        nflLogo.getStyle().set("margin-right", "8px");

        HorizontalLayout nflTabLabel = new HorizontalLayout(nflLogo, new Span("NFL"));
        nflTabLabel.setAlignItems(Alignment.CENTER);
        nflTabLabel.setSpacing(false);

        // 3. Add to TabSheet
        tabSheet.add(collegeTabLabel, createTabContent("NCAAF"));
        tabSheet.add(nflTabLabel, createTabContent("NFL"));

        add(tabSheet);
    }

    private VerticalLayout createTabContent(String sport) {
        VerticalLayout container = new VerticalLayout();
        container.setSizeFull();
        container.setPadding(false);

        HorizontalLayout controls = new HorizontalLayout();
        controls.setWidthFull();
        controls.setJustifyContentMode(JustifyContentMode.BETWEEN);
        controls.setAlignItems(Alignment.CENTER);

        ComboBox<Integer> weekSelector = new ComboBox<>("Week");
        if (sport.equals("NCAAF")) {
            weekSelector.setItems(IntStream.rangeClosed(0, 19).boxed().toList());
        } else {
            weekSelector.setItems(IntStream.rangeClosed(1, 18).boxed().toList());
        }
        weekSelector.setItemLabelGenerator(week -> formatWeekLabel(sport, week));
        weekSelector.setValue(sport.equals("NCAAF") ? 0 : 1);
        weekSelector.setWidth("220px");

        // 1. MOVE THIS UP: Create the grid FIRST so the button can see it
        VerticalLayout boardGrid = new VerticalLayout();
        boardGrid.setSizeFull();
        boardGrid.setPadding(false);

        HorizontalLayout leftControls = new HorizontalLayout(weekSelector);
        leftControls.setAlignItems(Alignment.BASELINE);

        controls.add(leftControls);

        // 3. Initial render and listeners
        renderPlayerColumns(boardGrid, sport, weekSelector.getValue());

        weekSelector.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                renderPlayerColumns(boardGrid, sport, event.getValue());
            }
        });

        container.add(controls, boardGrid);
        return container;
    }

    // Inside renderPlayerColumns in BoardView.java:

    private void renderPlayerColumns(VerticalLayout boardGrid, String sport, Integer selectedWeek) {
        long startTime = System.currentTimeMillis(); // Optional: track speed
        boardGrid.removeAll();

        // --- 1. BATCH FETCH ALL RECORDS FOR THIS SPORT/WEEK (Zero loop-queries) ---
        List<PlayerRecord> overallRecords = playerRecordRepo.findBySportAndWeekNumber(sport, 0);
        overallRecords.sort((a, b) -> Integer.compare(b.getWins(), a.getWins()));

        List<PlayerRecord> weeklyRecords = playerRecordRepo.findBySportAndWeekNumber(sport, selectedWeek);
        // Map player ID to their weekly record for O(1) lightning-fast lookups
        java.util.Map<Long, PlayerRecord> weeklyRecordMap = weeklyRecords.stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getPlayer().getId(), r -> r));

        List<Pick> weeklyPicList = pickRepo.findByWeekNumberAndSport(selectedWeek, sport);
        // Map player ID -> Map<SlotNumber, Pick> for instant slot matching
        java.util.Map<Long, java.util.Map<Integer, Pick>> playerPicksMap = weeklyPicList.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> p.getPlayer().getId(),
                        java.util.stream.Collectors.toMap(Pick::getSlotNumber, p -> p, (p1, p2) -> p1)
                ));

        // --- 2. CREATE LEADERBOARD BAR ---
        HorizontalLayout leaderboardBar = new HorizontalLayout();
        leaderboardBar.setWidthFull();
        leaderboardBar.getStyle().set("background", "#1e293b").set("padding", "10px 16px").set("border-radius", "8px");
        leaderboardBar.setAlignItems(Alignment.CENTER);

        Span leaderboardTitle = new Span("🏆 " + sport + " Overall Leaderboard: ");
        leaderboardTitle.getStyle().set("font-weight", "bold").set("color", "#38bdf8");
        leaderboardBar.add(leaderboardTitle);

        for (PlayerRecord rec : overallRecords) {
            Span statSpan = new Span(rec.getPlayer().getName() + ": " + rec.getWins() + "W-" + rec.getLosses() + "L-" + rec.getPushes() + "P");
            statSpan.getStyle().set("color", "#f8fafc").set("margin-right", "15px");
            leaderboardBar.add(statSpan);
        }
        boardGrid.add(leaderboardBar);

        // --- 3. SORT PLAYERS IN-MEMORY USING THE MAP ---
        List<Player> players = playerRepo.findAll();
        players.sort((p1, p2) -> {
            PlayerRecord r1 = weeklyRecordMap.getOrDefault(p1.getId(), new PlayerRecord(p1, sport, selectedWeek));
            PlayerRecord r2 = weeklyRecordMap.getOrDefault(p2.getId(), new PlayerRecord(p2, sport, selectedWeek));

            if (r2.getWins() != r1.getWins()) {
                return Integer.compare(r2.getWins(), r1.getWins());
            }
            return Integer.compare(r1.getLosses(), r2.getLosses());
        });

        // --- 4. RENDER PLAYER CARDS ---
        for (Player player : players) {
            VerticalLayout playerCard = new VerticalLayout();
            playerCard.setPadding(true);
            playerCard.setSpacing(true);

            playerCard.getStyle()
                    .set("background-color", "#151d30")
                    .set("border", "1px solid #22304d")
                    .set("border-radius", "16px")
                    .set("box-shadow", "0 10px 25px -5px rgba(0, 0, 0, 0.3)")
                    .set("margin-bottom", "15px");

            HorizontalLayout playerHeader = new HorizontalLayout();
            playerHeader.setWidthFull();
            playerHeader.setJustifyContentMode(JustifyContentMode.BETWEEN);
            playerHeader.setAlignItems(Alignment.CENTER);

            H2 name = new H2(player.getName());
            name.getStyle().set("margin", "0").set("font-size", "1.25rem").set("font-weight", "700");

            // Pull weekly stats instantly from map instead of individual queries
            PlayerRecord currentWeekRec = weeklyRecordMap.getOrDefault(player.getId(), new PlayerRecord(player, sport, selectedWeek));
            Span recordBadge = new Span(currentWeekRec.getWins() + "W - " + currentWeekRec.getLosses() + "L - " + currentWeekRec.getPushes() + "P");
            recordBadge.getStyle()
                    .set("background", "rgba(59, 130, 246, 0.15)")
                    .set("color", "#60a5fa")
                    .set("font-size", "0.75rem")
                    .set("font-weight", "600")
                    .set("padding", "4px 8px")
                    .set("border-radius", "9999px")
                    .set("border", "1px solid rgba(59, 130, 246, 0.3)");

            playerHeader.add(name, recordBadge);
            playerCard.add(playerHeader);

            // Get player's picks map for this week
            java.util.Map<Integer, Pick> slotPickMap = playerPicksMap.getOrDefault(player.getId(), java.util.Map.of());

            // Render 5 Pick Slots
            for (int slot = 1; slot <= 5; slot++) {
                int currentSlot = slot;
                Pick existingPick = slotPickMap.get(currentSlot);

                Button slotButton = new Button();
                slotButton.setWidthFull();
                slotButton.setHeight("50px");

                if (existingPick != null) {
                    com.vaadin.flow.component.html.Div slotContent = new com.vaadin.flow.component.html.Div();
                    slotContent.addClassName("pick-card-wrapper");

                    com.vaadin.flow.component.html.Div topRow = new com.vaadin.flow.component.html.Div();
                    topRow.getStyle().set("display", "flex");
                    topRow.getStyle().set("align-items", "center");
                    topRow.getStyle().set("gap", "8px");

                    if (existingPick.getLogoUrl() != null && !existingPick.getLogoUrl().isEmpty()) {
                        if (existingPick.getLogoUrl().contains("|")) {
                            String[] urls = existingPick.getLogoUrl().split("\\|");
                            Image awayLogo = new Image(urls[0], "away logo");
                            awayLogo.setWidth("20px");
                            awayLogo.setHeight("20px");
                            awayLogo.getStyle().set("flex-shrink", "0").set("margin-right", "2px");

                            Image homeLogo = new Image(urls[1], "home logo");
                            homeLogo.setWidth("20px");
                            homeLogo.setHeight("20px");
                            homeLogo.getStyle().set("flex-shrink", "0");

                            topRow.add(awayLogo, homeLogo);
                        } else {
                            Image logo = new Image(existingPick.getLogoUrl(), "icon");
                            logo.setWidth("24px");
                            logo.setHeight("24px");
                            logo.getStyle().set("flex-shrink", "0");
                            topRow.add(logo);
                        }
                    }

                    String rawSelection = existingPick.getSelection();
                    String matchName;
                    String lineBadgeText;

                    if (rawSelection.contains(" O ")) {
                        String[] parts = rawSelection.split(" O ", 2);
                        matchName = parts[0];
                        lineBadgeText = "O " + (parts.length > 1 ? parts[1] : "");
                    } else if (rawSelection.contains(" U ")) {
                        String[] parts = rawSelection.split(" U ", 2);
                        matchName = parts[0];
                        lineBadgeText = "U " + (parts.length > 1 ? parts[1] : "");
                    } else {
                        int lastSpace = rawSelection.lastIndexOf(" ");
                        if (lastSpace != -1) {
                            matchName = rawSelection.substring(0, lastSpace);
                            lineBadgeText = rawSelection.substring(lastSpace + 1);
                        } else {
                            matchName = rawSelection;
                            lineBadgeText = "";
                        }
                    }

                    Span nameSpan = new Span(matchName);
                    nameSpan.addClassName("pick-card-text");
                    topRow.add(nameSpan);

                    com.vaadin.flow.component.html.Div bottomRow = new com.vaadin.flow.component.html.Div();
                    bottomRow.addClassName("pick-card-bottom");

                    Span lineBadge = new Span(lineBadgeText);
                    lineBadge.addClassName("pick-line-badge");
                    bottomRow.add(lineBadge);

                    slotContent.add(topRow, bottomRow);
                    slotButton.setIcon(slotContent);

                    String status = existingPick.getStatus();
                    if ("WIN".equals(status)) {
                        slotButton.addClassName("pick-win");
                    } else if ("LOSS".equals(status)) {
                        slotButton.addClassName("pick-loss");
                    } else if ("PUSH".equals(status)) {
                        slotButton.addClassName("pick-push");
                    } else {
                        slotButton.addClassName("filled-slot-btn");
                    }
                }

                slotButton.addClickListener(event -> {
                    PickSelectionDialog dialog = new PickSelectionDialog(
                            player,
                            currentSlot,
                            sport,
                            selectedWeek,
                            oddsService,
                            pickRepo,
                            conferenceService,
                            gameSyncService,
                            () -> renderPlayerColumns(boardGrid, sport, selectedWeek)
                    );
                    dialog.open();
                });

                playerCard.add(slotButton);
            }
            boardGrid.add(playerCard);
        }
        System.out.println("Rendered week " + selectedWeek + " for " + sport + " in " + (System.currentTimeMillis() - startTime) + "ms");
    }

    private String formatWeekLabel(String sport, Integer week) {
        if (sport.equals("NFL")) return "Week " + week;
        if (sport.equals("NCAAF")) {
            if (week <= 14) return "Week " + week;
            if (week == 15) return "Conf. Championships";
            if (week == 16) return "CFP Round 1";
            if (week == 17) return "CFP Quarterfinals";
            if (week == 18) return "CFP Semifinals";
            if (week == 19) return "National Championship";
        }
        return "Week " + week;
    }
}