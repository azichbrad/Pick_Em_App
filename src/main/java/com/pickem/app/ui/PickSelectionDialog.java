package com.pickem.app.ui;

import com.pickem.app.dto.TeamDTO;
import com.pickem.app.model.Game;
import com.pickem.app.model.Player;
import com.pickem.app.service.ConferenceService;
import com.pickem.app.service.GameSyncService;
import com.pickem.app.service.OddsService;
import com.pickem.app.service.PickService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PickSelectionDialog extends Dialog {

    private final VerticalLayout gameListContainer = new VerticalLayout();
    private final List<Game> games; // Switched from GameOddsDTO to your local DB Game
    private final Map<String, TeamDTO> teamDataMap;
    private final GameSyncService gameSyncService;
    private final PickService pickService;
    private static final String TOTALS_ICON = "https://cdn-icons-png.flaticon.com/512/1199/1199155.png";

    private final DateTimeFormatter timeFormatter = DateTimeFormatter
            .ofPattern("EEE, MMM d • h:mm a")
            .withZone(ZoneId.of("America/Los_Angeles"));

    public PickSelectionDialog(
            Player player, int slotNumber, String sport, int weekNumber,
            OddsService oddsService, PickService pickService, ConferenceService conferenceService,
            GameSyncService gameSyncService, Runnable onPickSaved
    ) {
        this.gameSyncService = gameSyncService;
        this.pickService = pickService;

        // FETCH DIRECTLY FROM DB: Instant load times, zero rate limits!
        List<Game> fetchedGames = gameSyncService.getGamesForSportAndWeekFromDb(sport, weekNumber);
        this.games = fetchedGames != null ? fetchedGames : List.of();

        getElement().setAttribute("theme", "dark");
        getElement().getClassList().add("pick-dialog-overlay");

        setHeaderTitle("Select Pick for " + player.getName() + " (Slot " + slotNumber + ")");
        setWidth("560px");
        setHeight("720px");

        this.teamDataMap = sport.equals("NCAAF") ? conferenceService.getTeamDataMap() : Map.of();

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

        TextField searchField = new TextField("Search Team");
        searchField.setPlaceholder("Type team name...");
        searchField.setClearButtonVisible(true);
        searchField.setWidthFull();
        searchField.getElement().setAttribute("theme", "dark");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);

        conferenceFilter.addValueChangeListener(event -> {
            renderGames(event.getValue(), searchField.getValue(), player, slotNumber, sport, weekNumber, onPickSaved);
        });

        searchField.addValueChangeListener(event -> {
            renderGames(conferenceFilter.getValue(), event.getValue(), player, slotNumber, sport, weekNumber, onPickSaved);
        });

        gameListContainer.setSizeFull();
        gameListContainer.getStyle().set("overflow-y", "auto");
        gameListContainer.getStyle().set("padding-right", "4px");

        renderGames("All", "", player, slotNumber, sport, weekNumber, onPickSaved);

        VerticalLayout dialogLayout = new VerticalLayout(conferenceFilter, searchField, gameListContainer);
        dialogLayout.setPadding(false);
        dialogLayout.setSpacing(true);
        dialogLayout.setHeight("600px");
        dialogLayout.setWidthFull();

        add(dialogLayout);

        Button cancelBtn = new Button("Cancel", e -> close());
        cancelBtn.getStyle()
                .set("color", "#94a3b8")
                .set("background", "transparent")
                .set("cursor", "pointer");

        getFooter().add(cancelBtn);
    }

    private void renderGames(
            String selectedConference, String searchQuery, Player player, int slotNumber, String sport,
            int weekNumber, Runnable onPickSaved
    ) {
        gameListContainer.removeAll();

        if (games == null || games.isEmpty()) {
            gameListContainer.add(new Span("No upcoming games found."));
            return;
        }

        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        int matchedCount = 0;

        for (Game game : games) {
            String awayTeam = game.getAwayTeam() != null ? game.getAwayTeam() : "";
            String homeTeam = game.getHomeTeam() != null ? game.getHomeTeam() : "";

            // 1. Text Search Filter
            boolean matchesSearch = query.isEmpty()
                    || awayTeam.toLowerCase().contains(query)
                    || homeTeam.toLowerCase().contains(query);
            if (!matchesSearch) continue;

            // 2. Conference Filter (NCAAF only)
            if (selectedConference != null && !selectedConference.equals("All") && sport.equals("NCAAF")) {
                String homeConf = getConference(homeTeam);
                String awayConf = getConference(awayTeam);

                if (!selectedConference.equals(homeConf) && !selectedConference.equals(awayConf)) {
                    continue;
                }
            }

            // 3. EVALUATE GRACE PERIOD: Kicked off + 15 minutes
            Instant nowTwo = Instant.now();
            boolean isLiveOrFinished = game.getCommenceTime() != null &&
                    game.getCommenceTime().plus(Duration.ofMinutes(15)).isBefore(nowTwo);

            matchedCount++;
            String dualLogoUrls = getLogoUrl(awayTeam, sport) + "|" + getLogoUrl(homeTeam, sport);

            // Fetch odds from your local Game object getters
            Double awayPoint = game.getAwaySpread();
            Double homePoint = game.getHomeSpread();
            Double overPoint = game.getOverTotal();
            Double underPoint = game.getUnderTotal();

            // FORCE RENDER: Draw the card if odds exist OR if the game is already in progress
            if (awayPoint != null || homePoint != null || overPoint != null || underPoint != null || isLiveOrFinished) {

                VerticalLayout gameCard = new VerticalLayout();
                gameCard.getStyle().set("background-color", "#151d30");
                gameCard.getStyle().set("border", isLiveOrFinished ? "1px solid #7f1d1d" : "1px solid #22304d");
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

                String timeString = game.getCommenceTime() != null ? timeFormatter.format(game.getCommenceTime()) + " PT" : "TBD";
                Span timeSpan = new Span(isLiveOrFinished ? timeString + " • IN PROGRESS" : timeString);
                timeSpan.getStyle().set("font-size", "0.85em");

                if (isLiveOrFinished) {
                    timeSpan.getStyle().set("color", "#ef4444").set("font-weight", "600");
                } else {
                    timeSpan.getStyle().set("color", "#94a3b8");
                }
                timeRow.add(timeSpan);

                // --- AWAY TEAM ROW ---
                HorizontalLayout awayRow = new HorizontalLayout();
                awayRow.setWidthFull();
                awayRow.setAlignItems(FlexComponent.Alignment.CENTER);

                Image awayLogo = new Image(getLogoUrl(awayTeam, sport), awayTeam + " logo");
                awayLogo.setWidth("30px");
                awayLogo.setHeight("30px");
                Span awayName = new Span(awayTeam);
                awayName.getStyle().set("font-weight", "500").set("color", "#f8fafc");

                Button awayBtn = new Button();
                if (isLiveOrFinished) {
                    awayBtn.setText("Locked");
                    awayBtn.setEnabled(false);
                } else if (awayPoint != null) {
                    boolean isBurned = pickService.isSelectionBurned(player.getId(), sport, weekNumber, game.getId(), "spread", awayTeam);

                    if (isBurned) {
                        awayBtn.setText("Burned");
                        awayBtn.setEnabled(false);
                    } else {
                        String pointStr = awayPoint > 0 ? "+" + awayPoint : String.valueOf(awayPoint);
                        awayBtn.setText(pointStr);
                        String selectionStr = awayTeam + " " + pointStr;
                        awayBtn.addClickListener(e -> submitPick(player, slotNumber, sport, weekNumber, selectionStr, getLogoUrl(awayTeam, sport), game.getId(), awayPoint, matchNameStr, "spread", awayTeam, onPickSaved));
                    }
                } else {
                    awayBtn.setText("N/A");
                    awayBtn.setEnabled(false);
                }
                awayBtn.setWidth("80px");
                awayBtn.addClassName("odds-btn");

                Button overBtn = new Button();
                if (isLiveOrFinished) {
                    overBtn.setText("Locked");
                    overBtn.setEnabled(false);
                } else if (overPoint != null) {
                    boolean isBurned = pickService.isSelectionBurned(player.getId(), sport, weekNumber, game.getId(), "total", "Over");

                    if (isBurned) {
                        overBtn.setText("Burned");
                        overBtn.setEnabled(false);
                    } else {
                        String pointStr = "O " + overPoint;
                        overBtn.setText(pointStr);
                        String selectionStr = matchNameStr + " " + pointStr;
                        overBtn.addClickListener(e -> submitPick(player, slotNumber, sport, weekNumber, selectionStr, dualLogoUrls, game.getId(), overPoint, matchNameStr, "total", "Over", onPickSaved));
                    }
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
                homeRow.setAlignItems(FlexComponent.Alignment.CENTER);
                homeRow.getStyle().set("margin-top", "8px");

                Image homeLogo = new Image(getLogoUrl(homeTeam, sport), homeTeam + " logo");
                homeLogo.setWidth("30px");
                homeLogo.setHeight("30px");
                Span homeName = new Span(homeTeam);
                homeName.getStyle().set("font-weight", "500").set("color", "#f8fafc");

                Button homeBtn = new Button();
                if (isLiveOrFinished) {
                    homeBtn.setText("Locked");
                    homeBtn.setEnabled(false);
                } else if (homePoint != null) {
                    boolean isBurned = pickService.isSelectionBurned(player.getId(), sport, weekNumber, game.getId(), "spread", homeTeam);

                    if (isBurned) {
                        homeBtn.setText("Burned");
                        homeBtn.setEnabled(false);
                    } else {
                        String pointStr = homePoint > 0 ? "+" + homePoint : String.valueOf(homePoint);
                        homeBtn.setText(pointStr);
                        String selectionStr = homeTeam + " " + pointStr;
                        homeBtn.addClickListener(e -> submitPick(player, slotNumber, sport, weekNumber, selectionStr, getLogoUrl(homeTeam, sport), game.getId(), homePoint, matchNameStr, "spread", homeTeam, onPickSaved));
                    }
                } else {
                    homeBtn.setText("N/A");
                    homeBtn.setEnabled(false);
                }
                homeBtn.setWidth("80px");
                homeBtn.addClassName("odds-btn");

                Button underBtn = new Button();
                if (isLiveOrFinished) {
                    underBtn.setText("Locked");
                    underBtn.setEnabled(false);
                } else if (underPoint != null) {
                    boolean isBurned = pickService.isSelectionBurned(player.getId(), sport, weekNumber, game.getId(), "total", "Under");

                    if (isBurned) {
                        underBtn.setText("Burned");
                        underBtn.setEnabled(false);
                    } else {
                        String pointStr = "U " + underPoint;
                        underBtn.setText(pointStr);
                        String selectionStr = matchNameStr + " " + pointStr;
                        underBtn.addClickListener(e -> submitPick(player, slotNumber, sport, weekNumber, selectionStr, dualLogoUrls, game.getId(), underPoint, matchNameStr, "total", "Under", onPickSaved));
                    }
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

    private void submitPick(Player player, int slotNumber, String sport, int weekNumber, String selection,
                            String logoUrl, String gameId, Double lockedPoint, String matchName,
                            String marketType, String selectionSide, Runnable onPickSaved) {

        pickService.savePickWithBurnCheck(player, slotNumber, sport, weekNumber, selection, logoUrl, gameId, lockedPoint, matchName, marketType, selectionSide, onPickSaved);
        close();
    }

    private TeamDTO getTeamData(String teamName) {
        if (teamDataMap.isEmpty()) return null;
        TeamDTO bestMatch = null;
        int maxMatchLength = 0;
        for (Map.Entry<String, TeamDTO> entry : teamDataMap.entrySet()) {
            String cfbdTeamName = entry.getKey();
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
        String cachedLogo = gameSyncService.getLogoUrl(teamName);
        if (cachedLogo != null && !cachedLogo.isBlank()) {
            return cachedLogo;
        }
        return TOTALS_ICON;
    }
}