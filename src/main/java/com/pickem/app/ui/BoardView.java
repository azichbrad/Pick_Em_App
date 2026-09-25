package com.pickem.app.ui;

import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.repository.PlayerRecordRepository;
import com.pickem.app.repository.PlayerRepository;
import com.pickem.app.service.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
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
    private final PickRepository pickRepository;
    private final PickService pickService;

    public BoardView(PlayerRepository playerRepo, PickRepository pickRepo, OddsService oddsService,
                     ConferenceService conferenceService, GradingService gradingService,
                     GameSyncService gameSyncService, PlayerRecordRepository playerRecordRepo, PlayerRecordRepository playerRecordRepo1,
                     PickRepository pickRepository, PickService pickService) {
        this.playerRepo = playerRepo;
        this.pickRepo = pickRepo;
        this.oddsService = oddsService;
        this.conferenceService = conferenceService;
        this.gradingService = gradingService;
        this.gameSyncService = gameSyncService;
        this.playerRecordRepo = playerRecordRepo1;
        this.pickRepository = pickRepository;
        this.pickService = pickService;

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

        Image collegeLogo = new Image("/images/cfp.png", "College Football Logo");
        collegeLogo.setWidth("20px");
        collegeLogo.setHeight("20px");
        collegeLogo.getStyle().set("margin-right", "8px");

        HorizontalLayout collegeTabLabel = new HorizontalLayout(collegeLogo, new Span("College Football"));
        collegeTabLabel.setAlignItems(Alignment.CENTER);
        collegeTabLabel.setSpacing(false);

        Image nflLogo = new Image("/images/nfl.png", "NFL Logo");
        nflLogo.setWidth("20px");
        nflLogo.setHeight("20px");
        nflLogo.getStyle().set("margin-right", "8px");

        HorizontalLayout nflTabLabel = new HorizontalLayout(nflLogo, new Span("NFL"));
        nflTabLabel.setAlignItems(Alignment.CENTER);
        nflTabLabel.setSpacing(false);

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

        // FIXED: Dynamically load the current week instead of hardcoding 0 or 1!
        weekSelector.setValue(calculateCurrentWeek(sport));

        weekSelector.setWidth("220px");

        VerticalLayout boardGrid = new VerticalLayout();
        boardGrid.setSizeFull();
        boardGrid.setPadding(false);

        HorizontalLayout leftControls = new HorizontalLayout(weekSelector);
        leftControls.setAlignItems(Alignment.BASELINE);

        controls.add(leftControls);

        renderPlayerColumns(boardGrid, sport, weekSelector.getValue());

        weekSelector.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                renderPlayerColumns(boardGrid, sport, event.getValue());
            }
        });

        container.add(controls, boardGrid);
        return container;
    }

    private void renderPlayerColumns(VerticalLayout boardGrid, String sport, Integer selectedWeek) {
        long startTime = System.currentTimeMillis();
        boardGrid.removeAll();

        List<PlayerRecord> overallRecords = playerRecordRepo.findBySportAndWeekNumber(sport, 0);
        overallRecords.sort((a, b) -> Integer.compare(b.getWins(), a.getWins()));

        List<PlayerRecord> weeklyRecords = playerRecordRepo.findBySportAndWeekNumber(sport, selectedWeek);
        java.util.Map<Long, PlayerRecord> weeklyRecordMap = weeklyRecords.stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getPlayer().getId(), r -> r));

        List<Pick> weeklyPicList = pickRepo.findByWeekNumberAndSport(selectedWeek, sport);
        java.util.Map<Long, java.util.Map<Integer, Pick>> playerPicksMap = weeklyPicList.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> p.getPlayer().getId(),
                        java.util.stream.Collectors.toMap(Pick::getSlotNumber, p -> p, (p1, p2) -> p1)
                ));

        // NEW: Fetch all games for the week once to check individual lock times instantly
        List<com.pickem.app.model.Game> weeklyGames = gameSyncService.getGamesForSportAndWeekFromDb(sport, selectedWeek);
        java.util.Map<String, com.pickem.app.model.Game> gamesMap = weeklyGames == null ? java.util.Map.of() :
                weeklyGames.stream().collect(java.util.stream.Collectors.toMap(com.pickem.app.model.Game::getId, g -> g));

        java.time.Instant liveCutoff = java.time.Instant.now().minus(java.time.Duration.ofMinutes(15));

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

        List<Player> players = playerRepo.findAll();
        players.sort((p1, p2) -> {
            PlayerRecord r1 = weeklyRecordMap.getOrDefault(p1.getId(), new PlayerRecord(p1, sport, selectedWeek));
            PlayerRecord r2 = weeklyRecordMap.getOrDefault(p2.getId(), new PlayerRecord(p2, sport, selectedWeek));

            if (r2.getWins() != r1.getWins()) {
                return Integer.compare(r2.getWins(), r1.getWins());
            }
            return Integer.compare(r1.getLosses(), r2.getLosses());
        });

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

            java.util.Map<Integer, Pick> slotPickMap = playerPicksMap.getOrDefault(player.getId(), java.util.Map.of());
            boolean weekLocked = isWeekLocked(sport, selectedWeek);

            for (int slot = 1; slot <= 5; slot++) {
                int currentSlot = slot;
                Pick existingPick = slotPickMap.get(currentSlot);

                // NEW: Check if this specific game is locked
                boolean gameLocked = false;
                if (existingPick != null && existingPick.getGameId() != null) {
                    com.pickem.app.model.Game game = gamesMap.get(existingPick.getGameId());
                    if (game != null && game.getCommenceTime() != null) {
                        gameLocked = game.getCommenceTime().isBefore(liveCutoff);
                    }
                }

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

                    // NEW: Append a padlock if this specific game has started
                    Span lineBadge = new Span(lineBadgeText + (gameLocked ? " 🔒" : ""));
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

                // NEW: Combine global week lock OR individual game lock
                if (weekLocked || gameLocked) {
                    slotButton.getStyle().set("cursor", "default"); // No pointer finger

                    if (weekLocked && existingPick == null) {
                        slotButton.setText("🔒 Locked");
                        slotButton.getStyle()
                                .set("color", "#475569")
                                .set("background-color", "#0f172a")
                                .set("border", "1px dashed #334155");
                    }
                } else {
                    slotButton.addClickListener(event -> {
                        PickSelectionDialog dialog = new PickSelectionDialog(
                                player,
                                currentSlot,
                                sport,
                                selectedWeek,
                                oddsService,
                                pickService,
                                conferenceService,
                                gameSyncService,
                                () -> renderPlayerColumns(boardGrid, sport, selectedWeek)
                        );
                        dialog.open();
                    });
                }

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

    private boolean isWeekLocked(String sport, int weekNumber) {
        java.time.ZonedDateTime week1Start = "NFL".equalsIgnoreCase(sport)
                ? java.time.ZonedDateTime.of(2026, 9, 8, 0, 0, 0, 0, java.time.ZoneId.of("America/Los_Angeles"))
                : java.time.ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, java.time.ZoneId.of("America/Los_Angeles"));

        java.time.Instant windowEnd = week1Start.plusDays(weekNumber * 7L).toInstant();
        return java.time.Instant.now().isAfter(windowEnd);
    }

    private int calculateCurrentWeek(String sport) {
        java.time.ZoneId zone = java.time.ZoneId.of("America/Los_Angeles");
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(zone);

        java.time.ZonedDateTime week1Start = "NFL".equalsIgnoreCase(sport)
                ? java.time.ZonedDateTime.of(2026, 9, 8, 0, 0, 0, 0, zone)
                : java.time.ZonedDateTime.of(2026, 8, 30, 0, 0, 0, 0, zone);

        if (now.isBefore(week1Start)) {
            return "NCAAF".equalsIgnoreCase(sport) ? 0 : 1;
        }

        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(week1Start, now);
        int currentWeek = (int) (daysBetween / 7) + 1;

        int maxWeek = "NFL".equalsIgnoreCase(sport) ? 18 : 16;
        return Math.min(currentWeek, maxWeek);
    }
}