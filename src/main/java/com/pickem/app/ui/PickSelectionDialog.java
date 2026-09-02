package com.pickem.app.ui;

import com.pickem.app.dto.GameOddsDTO;
import com.pickem.app.dto.TeamDTO;
import com.pickem.app.model.Pick;
import com.pickem.app.model.Player;
import com.pickem.app.repository.PickRepository;
import com.pickem.app.service.ConferenceService;
import com.pickem.app.service.GameSyncService;
import com.pickem.app.service.OddsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PickSelectionDialog extends Dialog {

    private final VerticalLayout gameListContainer = new VerticalLayout();
    private final List<GameOddsDTO> games;
    private final Map<String, TeamDTO> teamDataMap;
    private final GameSyncService gameSyncService;
    private static final String TOTALS_ICON = "https://cdn-icons-png.flaticon.com/512/1199/1199155.png";

    // Formatter for the game times (Pacific Time)
    private final DateTimeFormatter timeFormatter = DateTimeFormatter
            .ofPattern("EEE, MMM d • h:mm a")
            .withZone(ZoneId.of("America/Los_Angeles"));

    public PickSelectionDialog(
            Player player, int slotNumber, String sport, int weekNumber,
            OddsService oddsService, PickRepository pickRepo, ConferenceService conferenceService,
            GameSyncService gameSyncService, Runnable onPickSaved
    ) {
        this.gameSyncService = gameSyncService;

        // Fast-path assignment with fallback to avoid null pointer delays
        List<GameOddsDTO> fetchedGames = oddsService.getOddsForSportAndWeek(sport, weekNumber);
        this.games = fetchedGames != null ? fetchedGames : List.of();

        // 1. Enable built-in dark theme on the dialog & attach custom CSS overlay class
        getElement().setAttribute("theme", "dark");
        getElement().getClassList().add("pick-dialog-overlay");

        setHeaderTitle("Select Pick for " + player.getName() + " (Slot " + slotNumber + ")");
        setWidth("560px");
        setHeight("720px");

        this.teamDataMap = sport.equals("NCAAF") ? conferenceService.getTeamDataMap() : Map.of();

        // --- CONFERENCE FILTER ---
        ComboBox<String> conferenceFilter = new ComboBox<>("Filter by Conference");
        conferenceFilter.setWidthFull();
        conferenceFilter.getElement().setAttribute("theme", "dark");

        if (sport.equals("NCAAF")) {
            List<String> dynamicConferences = new ArrayList<>();
            dynamicConferences.add("All");
            dynamicConferences.addAll(teamDataMap.values().stream().map(TeamDTO::conference).distinct().sorted().toList());
            conferenceFilter.setItems(dynamicConferences);
        } else {
            conferenceFilter.setItems("All", "AFC", "NFC");
        }
        conferenceFilter.setValue("All");

        // --- SEARCH FIELD ---
        TextField searchField = new TextField("Search Team");
        searchField.setPlaceholder("Type team name...");
        searchField.setClearButtonVisible(true);
        searchField.setWidthFull();
        searchField.getElement().setAttribute("theme", "dark");
        searchField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);

        // --- LISTENERS ---
        conferenceFilter.addValueChangeListener(event -> {
            renderGames(event.getValue(), searchField.getValue(), player, slotNumber, sport, weekNumber, pickRepo, onPickSaved);
        });

        searchField.addValueChangeListener(event -> {
            renderGames(conferenceFilter.getValue(), event.getValue(), player, slotNumber, sport, weekNumber, pickRepo, onPickSaved);
        });

        gameListContainer.setSizeFull();
        gameListContainer.getStyle().set("overflow-y", "auto");
        gameListContainer.getStyle().set("padding-right", "4px");

        // Initial Render call
        renderGames("All", "", player, slotNumber, sport, weekNumber, pickRepo, onPickSaved);

        VerticalLayout dialogLayout = new VerticalLayout(conferenceFilter, searchField, gameListContainer);
        dialogLayout.setPadding(false);
        dialogLayout.setSpacing(true);
        dialogLayout.setHeight("600px");
        dialogLayout.setWidthFull();

        add(dialogLayout);

        // --- CANCEL BUTTON IN FOOTER ---
        Button cancelBtn = new Button("Cancel", e -> close());
        cancelBtn.getStyle()
                .set("color", "#94a3b8")
                .set("background", "transparent")
                .set("cursor", "pointer");

        getFooter().add(cancelBtn);
    }

    private void renderGames(
            String selectedConference, String searchQuery, Player player, int slotNumber, String sport,
            int weekNumber, PickRepository pickRepo, Runnable onPickSaved
    ) {
        gameListContainer.removeAll();

        if (games == null || games.isEmpty()) {
            gameListContainer.add(new Span("No upcoming games found."));
            return;
        }

        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        int matchedCount = 0;

        for (GameOddsDTO game : games) {
            String awayTeam = game.awayTeam() != null ? game.awayTeam() : "";
            String homeTeam = game.homeTeam() != null ? game.homeTeam() : "";

            // 1. Text Search Filter (Matches either away or home team name)
            boolean matchesSearch = query.isEmpty()
                    || awayTeam.toLowerCase().contains(query)
                    || homeTeam.toLowerCase().contains(query);
            if (!matchesSearch) continue;

            // 2. Conference Filter (NCAAF only)
            if (selectedConference != null && !selectedConference.equals("All") && sport.equals("NCAAF")) {
                String homeConf = getConference(game.homeTeam());
                String awayConf = getConference(game.awayTeam());

                if (!selectedConference.equals(homeConf) && !selectedConference.equals(awayConf)) {
                    continue;
                }
            }

            matchedCount++;
            String dualLogoUrls = getLogoUrl(game.awayTeam(), sport) + "|" + getLogoUrl(game.homeTeam(), sport);

            Optional<GameOddsDTO.MarketDTO> spreadMarket = game.bookmakers().stream()
                    .flatMap(b -> b.markets().stream())
                    .filter(m -> "spreads".equalsIgnoreCase(m.key()))
                    .findFirst();

            Optional<GameOddsDTO.MarketDTO> totalsMarket = game.bookmakers().stream()
                    .flatMap(b -> b.markets().stream())
                    .filter(m -> "totals".equalsIgnoreCase(m.key()))
                    .findFirst();

            if (spreadMarket.isPresent() || totalsMarket.isPresent()) {

                GameOddsDTO.OutcomeDTO awayOutcome;
                GameOddsDTO.OutcomeDTO homeOutcome;

                if (spreadMarket.isPresent()) {
                    List<GameOddsDTO.OutcomeDTO> outcomes = spreadMarket.get().outcomes();
                    awayOutcome = outcomes.stream().filter(o -> o.name().equals(game.awayTeam())).findFirst().orElse(null);
                    homeOutcome = outcomes.stream().filter(o -> o.name().equals(game.homeTeam())).findFirst().orElse(null);
                } else {
                    homeOutcome = null;
                    awayOutcome = null;
                }

                GameOddsDTO.OutcomeDTO overOutcome;
                GameOddsDTO.OutcomeDTO underOutcome;

                if (totalsMarket.isPresent()) {
                    List<GameOddsDTO.OutcomeDTO> totalOutcomes = totalsMarket.get().outcomes();
                    overOutcome = totalOutcomes.stream().filter(o -> "Over".equalsIgnoreCase(o.name())).findFirst().orElse(null);
                    underOutcome = totalOutcomes.stream().filter(o -> "Under".equalsIgnoreCase(o.name())).findFirst().orElse(null);
                } else {
                    underOutcome = null;
                    overOutcome = null;
                }

                VerticalLayout gameCard = new VerticalLayout();
                gameCard.getStyle().set("background-color", "#151d30");
                gameCard.getStyle().set("border", "1px solid #22304d");
                gameCard.getStyle().set("border-radius", "12px");
                gameCard.getStyle().set("padding", "14px");
                gameCard.getStyle().set("margin-bottom", "12px");
                gameCard.setSpacing(false);

                String matchNameStr = awayTeam + " @ " + homeTeam;

                // --- START TIME HEADER ---
                HorizontalLayout timeRow = new HorizontalLayout();
                timeRow.setWidthFull();
                timeRow.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
                timeRow.getStyle().set("margin-bottom", "8px");

                String timeString = game.commenceTime() != null ? timeFormatter.format(game.commenceTime()) + " PT" : "TBD";
                Span timeSpan = new Span(timeString);
                timeSpan.getStyle().set("font-size", "0.85em").set("color", "#94a3b8");
                timeRow.add(timeSpan);

                // --- AWAY TEAM ROW ---
                HorizontalLayout awayRow = new HorizontalLayout();
                awayRow.setWidthFull();
                awayRow.setAlignItems(Alignment.CENTER);

                String awayLogoUrl = getLogoUrl(awayTeam, sport);
                Image awayLogo = new Image(awayLogoUrl, awayTeam + " logo");
                awayLogo.setWidth("30px");
                awayLogo.setHeight("30px");

                Span awayName = new Span(awayTeam);
                awayName.getStyle().set("font-weight", "500").set("color", "#f8fafc");

                Button awayBtn = new Button();
                if (awayOutcome != null && awayOutcome.point() != null) {
                    String pointStr = awayOutcome.point() > 0 ? "+" + awayOutcome.point() : String.valueOf(awayOutcome.point());
                    awayBtn.setText(pointStr);
                    String selectionStr = awayTeam + " " + pointStr;

                    awayBtn.addClickListener(e -> savePick(player, slotNumber, sport, weekNumber, selectionStr, getLogoUrl(awayTeam, sport), game.id(), awayOutcome.point(), matchNameStr, pickRepo, onPickSaved));
                } else {
                    awayBtn.setText("N/A");
                    awayBtn.setEnabled(false);
                }
                awayBtn.setWidth("80px");
                awayBtn.addClassName("odds-btn");

                Button overBtn = new Button();
                if (overOutcome != null && overOutcome.point() != null) {
                    String pointStr = "O " + overOutcome.point();
                    overBtn.setText(pointStr);
                    String selectionStr = matchNameStr + " " + pointStr;

                    overBtn.addClickListener(e -> savePick(
                            player, slotNumber, sport, weekNumber, selectionStr,
                            dualLogoUrls,
                            game.id(), overOutcome.point(), matchNameStr, pickRepo, onPickSaved
                    ));
                } else {
                    overBtn.setText("N/A");
                    overBtn.setEnabled(false);
                }
                overBtn.setWidth("80px");
                overBtn.addClassName("odds-btn");

                awayRow.add(awayLogo, awayName, awayBtn, overBtn);
                awayRow.expand(awayName);

                // --- HOME TEAM ROW ---
                HorizontalLayout homeRow = new HorizontalLayout();
                homeRow.setWidthFull();
                homeRow.setAlignItems(Alignment.CENTER);
                homeRow.getStyle().set("margin-top", "8px");

                String homeLogoUrl = getLogoUrl(homeTeam, sport);
                Image homeLogo = new Image(homeLogoUrl, homeTeam + " logo");
                homeLogo.setWidth("30px");
                homeLogo.setHeight("30px");

                Span homeName = new Span(homeTeam);
                homeName.getStyle().set("font-weight", "500").set("color", "#f8fafc");

                Button homeBtn = new Button();
                if (homeOutcome != null && homeOutcome.point() != null) {
                    String pointStr = homeOutcome.point() > 0 ? "+" + homeOutcome.point() : String.valueOf(homeOutcome.point());
                    homeBtn.setText(pointStr);
                    String selectionStr = homeTeam + " " + pointStr;

                    homeBtn.addClickListener(e -> savePick(player, slotNumber, sport, weekNumber, selectionStr, getLogoUrl(homeTeam, sport), game.id(), homeOutcome.point(), matchNameStr, pickRepo, onPickSaved));
                } else {
                    homeBtn.setText("N/A");
                    homeBtn.setEnabled(false);
                }
                homeBtn.setWidth("80px");
                homeBtn.addClassName("odds-btn");

                Button underBtn = new Button();
                if (underOutcome != null && underOutcome.point() != null) {
                    String pointStr = "U " + underOutcome.point();
                    underBtn.setText(pointStr);
                    String selectionStr = matchNameStr + " " + pointStr;

                    underBtn.addClickListener(e -> savePick(
                            player, slotNumber, sport, weekNumber, selectionStr,
                            dualLogoUrls,
                            game.id(), underOutcome.point(), matchNameStr, pickRepo, onPickSaved
                    ));
                } else {
                    underBtn.setText("N/A");
                    underBtn.setEnabled(false);
                }
                underBtn.setWidth("80px");
                underBtn.addClassName("odds-btn");

                homeRow.add(homeLogo, homeName, homeBtn, underBtn);
                homeRow.expand(homeName);

                gameCard.add(timeRow, awayRow, homeRow);
                gameListContainer.add(gameCard);
            }
        }

        if (matchedCount == 0) {
            gameListContainer.add(new Span("No matching games found."));
        }
    }

    private void savePick(Player player, int slotNumber, String sport, int weekNumber, String selection, String logoUrl, String gameId, Double lockedPoint, String matchName, PickRepository pickRepo, Runnable onPickSaved) {
        Pick pick = pickRepo.findByPlayerIdAndWeekNumberAndSlotNumber(player.getId(), weekNumber, slotNumber).orElse(new Pick());
        pick.setPlayer(player);
        pick.setSlotNumber(slotNumber);
        pick.setWeekNumber(weekNumber);
        pick.setSport(sport);
        pick.setSelection(selection);
        pick.setLogoUrl(logoUrl);

        pick.setGameId(gameId);
        pick.setLockedPoint(lockedPoint);
        pick.setMatchName(matchName);

        pick.setStatus("PENDING");
        pickRepo.save(pick);
        close();
        onPickSaved.run();
    }

    private TeamDTO getTeamData(String teamName) {
        if (teamDataMap.isEmpty()) return null;

        TeamDTO bestMatch = null;
        int maxMatchLength = 0;

        for (Map.Entry<String, TeamDTO> entry : teamDataMap.entrySet()) {
            String cfbdTeamName = entry.getKey();

            // FIX: Use startsWith instead of contains!
            // This prevents "West Georgia Wolves" from matching "Georgia"
            if (teamName.startsWith(cfbdTeamName)) {
                if (cfbdTeamName.length() > maxMatchLength) {
                    maxMatchLength = cfbdTeamName.length();
                    bestMatch = entry.getValue();
                }
            }
        }

        return bestMatch;
    }

    private String getConference(String teamName) {
        TeamDTO teamData = getTeamData(teamName);
        return teamData != null ? teamData.conference() : "Other";
    }

    private String getLogoUrl(String teamName, String sport) {
        // Tap into the GameSyncService dynamic logo cache (which now handles both!)
        String cachedLogo = gameSyncService.getLogoUrl(teamName);

        if (cachedLogo != null && !cachedLogo.isBlank()) {
            return cachedLogo;
        }

        return TOTALS_ICON;
    }
}