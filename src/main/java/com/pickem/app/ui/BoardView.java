package com.pickem.app.ui;

import com.pickem.app.model.League;
import com.pickem.app.model.LeagueMember;
import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.model.PlayerRecord;
import com.pickem.app.repository.LeagueMemberRepository;
import com.pickem.app.repository.LeagueRepository;
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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.lumo.Lumo;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.IntStream;

@Route(value = "board", layout = MainLayout.class)
@CssImport("./styles.css")
@PermitAll
public class BoardView extends VerticalLayout implements BeforeEnterObserver {

    private final PlayerRepository playerRepo;
    private final PickRepository pickRepo;
    private final OddsService oddsService;
    private final ConferenceService conferenceService;
    private final GameSyncService gameSyncService;
    private final PlayerRecordRepository playerRecordRepo;
    private final PickService pickService;
    private final ParlayService parlayService;
    private final LeagueMemberRepository leagueMemberRepo;
    private final LeagueRepository leagueRepo; // 1. Added LeagueRepository

    public BoardView(PlayerRepository playerRepo, PickRepository pickRepo, OddsService oddsService,
                     ConferenceService conferenceService,
                     GameSyncService gameSyncService, PlayerRecordRepository playerRecordRepo,
                     PickService pickService, ParlayService parlayService,
                     LeagueMemberRepository leagueMemberRepo,
                     LeagueRepository leagueRepo) { // Inject it here
        this.playerRepo = playerRepo;
        this.pickRepo = pickRepo;
        this.oddsService = oddsService;
        this.conferenceService = conferenceService;
        this.gameSyncService = gameSyncService;
        this.playerRecordRepo = playerRecordRepo;
        this.parlayService = parlayService;
        this.pickService = pickService;
        this.leagueMemberRepo = leagueMemberRepo;
        this.leagueRepo = leagueRepo;

        getElement().setAttribute("theme", Lumo.DARK);
        setSizeFull();
        setPadding(true);
        getStyle().set("background-color", "#0b0f19");

        renderTabs();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Long currentLeagueId = (Long) VaadinSession.getCurrent().getAttribute("league_id");
        if (currentLeagueId == null) {
            event.forwardTo(DashboardView.class);
        }
    }

    private void renderTabs() {
        removeAll();

        Long currentLeagueId = (Long) VaadinSession.getCurrent().getAttribute("league_id");
        League activeLeague = null;

        if (currentLeagueId != null) {
            activeLeague = leagueRepo.findById(currentLeagueId).orElse(null);
        }

        boolean parlayEnabled = activeLeague != null && Boolean.TRUE.equals(activeLeague.getEnableGroupParlay());
        int picksPerWeek = activeLeague != null && activeLeague.getPicksPerWeek() != null ? activeLeague.getPicksPerWeek() : 5;

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.addThemeVariants(TabSheetVariant.LUMO_BORDERED);

        Image collegeLogo = new Image("/images/cfp.png", "College Football Logo");
        collegeLogo.setWidth("20px");
        collegeLogo.setHeight("20px");
        collegeLogo.getStyle().set("margin-right", "8px");

        HorizontalLayout collegeTabLabel = new HorizontalLayout(collegeLogo, new Span("CFB"));
        collegeTabLabel.setAlignItems(Alignment.CENTER);
        collegeTabLabel.setSpacing(false);

        Image nflLogo = new Image("/images/nfl.png", "NFL Logo");
        nflLogo.setWidth("20px");
        nflLogo.setHeight("20px");
        nflLogo.getStyle().set("margin-right", "8px");

        HorizontalLayout nflTabLabel = new HorizontalLayout(nflLogo, new Span("NFL"));
        nflTabLabel.setAlignItems(Alignment.CENTER);
        nflTabLabel.setSpacing(false);

        Button groupParlayBtn = new Button();
        groupParlayBtn.getStyle().set("margin-right", "8px");
        groupParlayBtn.setVisible(parlayEnabled);

        int[] currentWeeks = { calculateCurrentWeek("NCAAF"), calculateCurrentWeek("NFL") };
        String[] currentTab = { "NCAAF" };

        Runnable updateParlayBtnState = () -> {
            int masterWeek = currentTab[0].equals("NCAAF") ? currentWeeks[0] : (currentWeeks[1] + 1);
            // PASSED LEAGUE ID HERE
            int lockedCount = parlayService.getLockedPickCount(currentLeagueId, masterWeek);
            String slateLabel = getSlateSundayForWeek(masterWeek);

            if (lockedCount >= picksPerWeek) {
                groupParlayBtn.setText(slateLabel + " (Ready)");
                groupParlayBtn.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_SUCCESS, com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);
            } else {
                groupParlayBtn.setText(slateLabel + " (" + lockedCount + "/" + picksPerWeek + ")");
                groupParlayBtn.removeThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_SUCCESS);
                groupParlayBtn.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);
            }
        };

        groupParlayBtn.addClickListener(e -> {
            int masterWeek = currentTab[0].equals("NCAAF") ? currentWeeks[0] : (currentWeeks[1] + 1);
            // PASSED LEAGUE ID HERE
            GroupParlayDialog dialog = new GroupParlayDialog(
                    currentLeagueId, masterWeek, parlayService, playerRepo, oddsService,
                    conferenceService, gameSyncService, updateParlayBtnState
            );
            dialog.open();
        });

        tabSheet.setSuffixComponent(groupParlayBtn);

        com.vaadin.flow.component.tabs.Tab cfbTab = tabSheet.add(collegeTabLabel, createTabContent("NCAAF", week -> {
            currentWeeks[0] = week;
            if (currentTab[0].equals("NCAAF")) updateParlayBtnState.run();
        }));

        com.vaadin.flow.component.tabs.Tab nflTab = tabSheet.add(nflTabLabel, createTabContent("NFL", week -> {
            currentWeeks[1] = week;
            if (currentTab[0].equals("NFL")) updateParlayBtnState.run();
        }));

        tabSheet.addSelectedChangeListener(event -> {
            currentTab[0] = event.getSelectedTab().equals(cfbTab) ? "NCAAF" : "NFL";
            updateParlayBtnState.run();
        });

        updateParlayBtnState.run();
        add(tabSheet);
    }

    private VerticalLayout createTabContent(String sport, java.util.function.Consumer<Integer> onWeekChanged) {
        VerticalLayout container = new VerticalLayout();
        container.setSizeFull();
        container.setPadding(false);

        HorizontalLayout controls = new HorizontalLayout();
        controls.setWidthFull();
        controls.setJustifyContentMode(JustifyContentMode.BETWEEN);
        controls.setAlignItems(Alignment.CENTER);

        int minWeek = sport.equals("NCAAF") ? 0 : 1;
        int maxWeek = sport.equals("NCAAF") ? 19 : 18;

        ComboBox<Integer> weekSelector = new ComboBox<>("Week");
        weekSelector.setItems(IntStream.rangeClosed(minWeek, maxWeek).boxed().toList());
        weekSelector.setItemLabelGenerator(week -> formatWeekLabel(sport, week));
        weekSelector.setValue(calculateCurrentWeek(sport));
        weekSelector.setWidth("220px");

        Button prevButton = new Button(com.vaadin.flow.component.icon.VaadinIcon.ANGLE_LEFT.create());
        prevButton.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_ICON, com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        prevButton.addClickListener(e -> {
            Integer current = weekSelector.getValue();
            if (current != null && current > minWeek) {
                weekSelector.setValue(current - 1);
            }
        });

        Button nextButton = new Button(com.vaadin.flow.component.icon.VaadinIcon.ANGLE_RIGHT.create());
        nextButton.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_ICON, com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        nextButton.addClickListener(e -> {
            Integer current = weekSelector.getValue();
            if (current != null && current < maxWeek) {
                weekSelector.setValue(current + 1);
            }
        });

        VerticalLayout boardGrid = new VerticalLayout();
        boardGrid.setSizeFull();
        boardGrid.setPadding(false);

        HorizontalLayout leftControls = new HorizontalLayout(prevButton, weekSelector, nextButton);
        leftControls.setAlignItems(Alignment.END);

        controls.add(leftControls);

        prevButton.setEnabled(weekSelector.getValue() > minWeek);
        nextButton.setEnabled(weekSelector.getValue() < maxWeek);

        renderPlayerColumns(boardGrid, sport, weekSelector.getValue());

        weekSelector.addValueChangeListener(event -> {
            Integer val = event.getValue();
            if (val != null) {
                prevButton.setEnabled(val > minWeek);
                nextButton.setEnabled(val < maxWeek);
                renderPlayerColumns(boardGrid, sport, val);
                onWeekChanged.accept(val);
            }
        });

        container.add(controls, boardGrid);
        return container;
    }

    private void renderPlayerColumns(VerticalLayout boardGrid, String sport, Integer selectedWeek) {
        long startTime = System.currentTimeMillis();
        boardGrid.removeAll();

        Long leagueId = (Long) VaadinSession.getCurrent().getAttribute("league_id");

        // Prevent Spring Data crash if session is missing; beforeEnter will handle the redirect
        if (leagueId == null) {
            return;
        }

        List<LeagueMember> memberships = leagueMemberRepo.findByLeagueId(leagueId);
        List<Player> activeLeaguePlayers = new java.util.ArrayList<>(
                memberships.stream()
                        .map(LeagueMember::getPlayer)
                        .toList()
        );

        // 5. Fetch active league again to determine how many pick slots to render
        League activeLeague = leagueRepo.findById(leagueId).orElse(null);
        int picksPerWeek = activeLeague != null && activeLeague.getPicksPerWeek() != null ? activeLeague.getPicksPerWeek() : 5;

        List<PlayerRecord> allWeeklyRecords = playerRecordRepo.findByLeagueIdAndSport(leagueId, sport).stream()
                .filter(r -> r.getWeekNumber() != null && r.getWeekNumber() < 90)
                .toList();

        java.util.Map<Long, int[]> aggregatedMap = new java.util.HashMap<>();
        for (Player p : activeLeaguePlayers) {
            aggregatedMap.put(p.getId(), new int[3]);
        }

        for (PlayerRecord rec : allWeeklyRecords) {
            if (rec.getPlayer() != null && aggregatedMap.containsKey(rec.getPlayer().getId())) {
                int[] stats = aggregatedMap.get(rec.getPlayer().getId());
                stats[0] += rec.getWins();
                stats[1] += rec.getLosses();
                stats[2] += rec.getPushes();
            }
        }

        java.util.Map<Long, Player> playerMap = activeLeaguePlayers.stream()
                .collect(java.util.stream.Collectors.toMap(Player::getId, p -> p));

        List<PlayerRecord> overallRecords = aggregatedMap.entrySet().stream()
                .map(entry -> {
                    PlayerRecord total = new PlayerRecord(playerMap.get(entry.getKey()), sport, 99, leagueId);
                    total.setWins(entry.getValue()[0]);
                    total.setLosses(entry.getValue()[1]);
                    total.setPushes(entry.getValue()[2]);
                    return total;
                })
                .sorted((a, b) -> {
                    if (b.getWins() != a.getWins()) {
                        return Integer.compare(b.getWins(), a.getWins());
                    }
                    return Integer.compare(a.getLosses(), b.getLosses());
                })
                .toList();

        List<PlayerRecord> weeklyRecords = playerRecordRepo.findByLeagueIdAndSportAndWeekNumber(leagueId, sport, selectedWeek);
        java.util.Map<Long, PlayerRecord> weeklyRecordMap = weeklyRecords.stream()
                .filter(r -> aggregatedMap.containsKey(r.getPlayer().getId()))
                .collect(java.util.stream.Collectors.toMap(r -> r.getPlayer().getId(), r -> r));

        List<Pick> weeklyPicList = pickRepo.findByLeagueIdAndWeekNumberAndSport(leagueId, selectedWeek, sport);
        java.util.Map<Long, java.util.Map<Integer, Pick>> playerPicksMap = weeklyPicList.stream()
                .filter(p -> aggregatedMap.containsKey(p.getPlayer().getId()))
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> p.getPlayer().getId(),
                        java.util.stream.Collectors.toMap(Pick::getSlotNumber, p -> p, (p1, p2) -> p1)
                ));

        List<com.pickem.app.model.Game> weeklyGames = gameSyncService.getGamesForSportAndWeekFromDb(sport, selectedWeek);
        java.util.Map<String, com.pickem.app.model.Game> gamesMap = weeklyGames == null ? java.util.Map.of() :
                weeklyGames.stream().collect(java.util.stream.Collectors.toMap(com.pickem.app.model.Game::getId, g -> g));

        java.time.Instant liveCutoff = java.time.Instant.now();

        HorizontalLayout leaderboardBar = new HorizontalLayout();
        leaderboardBar.setWidthFull();
        leaderboardBar.getStyle()
                .set("flex-wrap", "nowrap")
                .set("overflow-x", "auto")
                .set("gap", "16px")
                .set("padding-bottom", "8px")
                .set("-webkit-overflow-scrolling", "touch")
                .set("align-items", "baseline")
                .set("flex-shrink", "0")
                .set("min-height", "45px");
        leaderboardBar.setAlignItems(Alignment.CENTER);

        Span leaderboardTitle = new Span("🏆 " + sport + " Overall Leaderboard: ");
        leaderboardTitle.getStyle()
                .set("font-weight", "bold")
                .set("color", "#38bdf8")
                .set("white-space", "nowrap");
        leaderboardBar.add(leaderboardTitle);

        for (PlayerRecord rec : overallRecords) {
            Span statSpan = new Span(rec.getPlayer().getName() + ": " + rec.getWins() + "W-" + rec.getLosses() + "L-" + rec.getPushes() + "P");
            statSpan.getStyle().set("white-space", "nowrap");
            leaderboardBar.add(statSpan);
        }
        boardGrid.add(leaderboardBar);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = "";

        if (auth != null) {
            if (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User oauthUser) {
                currentUser = oauthUser.getAttribute("name");
                if (currentUser == null) currentUser = oauthUser.getAttribute("given_name");
            }
            if (currentUser == null || currentUser.isEmpty()) {
                currentUser = auth.getName();
            }
        }

        final String loggedInUserLower = currentUser != null ? currentUser.toLowerCase() : "";

        activeLeaguePlayers.sort((p1, p2) -> {
            String p1Name = p1.getName().toLowerCase();
            String p2Name = p2.getName().toLowerCase();

            boolean isP1Current = !loggedInUserLower.isEmpty() && (loggedInUserLower.contains(p1Name) || p1Name.contains(loggedInUserLower));
            boolean isP2Current = !loggedInUserLower.isEmpty() && (loggedInUserLower.contains(p2Name) || p2Name.contains(loggedInUserLower));

            if (isP1Current && !isP2Current) return -1;
            if (!isP1Current && isP2Current) return 1;

            PlayerRecord r1 = weeklyRecordMap.getOrDefault(p1.getId(), new PlayerRecord(p1, sport, selectedWeek, leagueId));
            PlayerRecord r2 = weeklyRecordMap.getOrDefault(p2.getId(), new PlayerRecord(p2, sport, selectedWeek, leagueId));

            if (r2.getWins() != r1.getWins()) {
                return Integer.compare(r2.getWins(), r1.getWins());
            }
            return Integer.compare(r1.getLosses(), r2.getLosses());
        });

        for (Player player : activeLeaguePlayers) {
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

            PlayerRecord currentWeekRec = weeklyRecordMap.getOrDefault(player.getId(), new PlayerRecord(player, sport, selectedWeek, leagueId));
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

            // 6. Changed hardcoded 5 to the league's custom picksPerWeek amount
            for (int slot = 1; slot <= picksPerWeek; slot++) {
                int currentSlot = slot;
                Pick existingPick = slotPickMap.get(currentSlot);

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

                if (weekLocked || gameLocked) {
                    slotButton.getStyle().set("cursor", "default");

                    if (weekLocked && existingPick == null) {
                        slotButton.setText("🔒 Locked");
                        slotButton.getStyle()
                                .set("color", "#475569")
                                .set("background-color", "#0f172a")
                                .set("border", "1px dashed #334155");
                    }
                } else {
                    slotButton.addClickListener(event -> {
                        // PASSED LEAGUE ID HERE
                        PickSelectionDialog dialog = new PickSelectionDialog(
                                player,
                                leagueId,
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

    private String getSlateSundayForWeek(int ncaafWeek) {
        java.time.ZonedDateTime week0Sunday = java.time.ZonedDateTime.of(2026, 8, 23, 0, 0, 0, 0, java.time.ZoneId.of("America/Los_Angeles"));
        return week0Sunday.plusWeeks(ncaafWeek).format(java.time.format.DateTimeFormatter.ofPattern("MMM d"));
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

    private String getCurrentSlateSunday() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Los_Angeles"));

        if (now.getDayOfWeek() == java.time.DayOfWeek.MONDAY) {
            now = now.minusDays(1);
        } else if (now.getDayOfWeek() == java.time.DayOfWeek.TUESDAY) {
            now = now.plusDays(5);
        }

        while (now.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
            now = now.plusDays(1);
        }

        return now.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"));
    }
}