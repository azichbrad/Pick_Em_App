package com.pickem.app.ui;

import com.pickem.app.dto.GameOddsDTO;
import com.pickem.app.dto.TeamDTO;
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
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;
import java.time.Instant;

public class PickSelectionDialog extends Dialog {

    private final VerticalLayout gameListContainer = new VerticalLayout();
    private final List<GameOddsDTO> games;
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

        List<GameOddsDTO> fetchedGames = oddsService.getOddsForSportAndWeek(sport, weekNumber);
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
        searchField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);

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

        // Get the current time to check the 15-minute grace period
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Los_Angeles"));

        for (GameOddsDTO game : games) {
            String awayTeam = game.awayTeam() != null ? game.awayTeam() : "";
            String homeTeam = game.homeTeam() != null ? game.homeTeam() : "";

            // 1. Text Search Filter
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

            // 3. EVALUATE GRACE PERIOD: Kicked off + 15 minutes
            Instant nowTwo = Instant.now();
            boolean isLiveOrFinished = game.commenceTime() != null &&
                    game.commenceTime().plus(Duration.ofMinutes(15)).isBefore(nowTwo);

            // Skip the game entirely if it has started (hides it from the modal)
            // NOTE: Comment out the 'continue;' line if you need to visually test the UI during live games!
            if (isLiveOrFinished) {
                continue;
            }

            // 4. Count the game ONLY if it survives the search, conference, and time filters
            matchedCount++;
            String dualLogoUrls = getLogoUrl(game.awayTeam(), sport) + "|" + getLogoUrl(game.homeTeam(), sport);

            // 5. Check for SharpAPI singular market keys ("spread" and "total")
            Optional<GameOddsDTO.MarketDTO> spreadMarket = game.bookmakers().stream()
                    .flatMap(b -> b.markets().stream())
                    .filter(m -> "spread".equalsIgnoreCase(m.key())) // FIXED: Singular
                    .findFirst();

            Optional<GameOddsDTO.MarketDTO> totalsMarket = game.bookmakers().stream()
                    .flatMap(b -> b.markets().stream())
                    .filter(m -> "total".equalsIgnoreCase(m.key())) // FIXED: Singular
                    .findFirst();

            if (spreadMarket.isPresent() || totalsMarket.isPresent()) {

                final GameOddsDTO.OutcomeDTO awayOutcome = spreadMarket
                        .flatMap(market -> market.outcomes().stream().filter(o -> o.name().equals(game.awayTeam())).findFirst())
                        .orElse(null);

                final GameOddsDTO.OutcomeDTO homeOutcome = spreadMarket
                        .flatMap(market -> market.outcomes().stream().filter(o -> o.name().equals(game.homeTeam())).findFirst())
                        .orElse(null);

                final GameOddsDTO.OutcomeDTO overOutcome = totalsMarket
                        .flatMap(market -> market.outcomes().stream().filter(o -> "Over".equalsIgnoreCase(o.name())).findFirst())
                        .orElse(null);

                final GameOddsDTO.OutcomeDTO underOutcome = totalsMarket
                        .flatMap(market -> market.outcomes().stream().filter(o -> "Under".equalsIgnoreCase(o.name())).findFirst())
                        .orElse(null);

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

                Image awayLogo = new Image(getLogoUrl(awayTeam, sport), awayTeam + " logo");
                awayLogo.setWidth("30px");
                awayLogo.setHeight("30px");
                Span awayName = new Span(awayTeam);
                awayName.getStyle().set("font-weight", "500").set("color", "#f8fafc");

                Button awayBtn = new Button();
                if (awayOutcome != null && awayOutcome.point() != null) {
                    boolean isBurned = pickService.isSelectionBurned(player.getId(), sport, weekNumber, game.id(), "spread", awayTeam);

                    if (isBurned) {
                        awayBtn.setText("Burned");
                        awayBtn.setEnabled(false);
                    } else {
                        String pointStr = awayOutcome.point() > 0 ? "+" + awayOutcome.point() : String.valueOf(awayOutcome.point());
                        awayBtn.setText(pointStr);
                        String selectionStr = awayTeam + " " + pointStr;
                        awayBtn.addClickListener(e -> submitPick(player, slotNumber, sport, weekNumber, selectionStr, getLogoUrl(awayTeam, sport), game.id(), awayOutcome.point(), matchNameStr, "spread", awayTeam, onPickSaved));
                    }
                } else {
                    awayBtn.setText("N/A");
                    awayBtn.setEnabled(false);
                }
                awayBtn.setWidth("80px");
                awayBtn.addClassName("odds-btn");

                Button overBtn = new Button();
                if (overOutcome != null && overOutcome.point() != null) {
                    boolean isBurned = pickService.isSelectionBurned(player.getId(), sport, weekNumber, game.id(), "total", "Over");

                    if (isBurned) {
                        overBtn.setText("Burned");
                        overBtn.setEnabled(false);
                    } else {
                        String pointStr = "O " + overOutcome.point();
                        overBtn.setText(pointStr);
                        String selectionStr = matchNameStr + " " + pointStr;
                        overBtn.addClickListener(e -> submitPick(player, slotNumber, sport, weekNumber, selectionStr, dualLogoUrls, game.id(), overOutcome.point(), matchNameStr, "total", "Over", onPickSaved));
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
                homeRow.setAlignItems(Alignment.CENTER);
                homeRow.getStyle().set("margin-top", "8px");

                Image homeLogo = new Image(getLogoUrl(homeTeam, sport), homeTeam + " logo");
                homeLogo.setWidth("30px");
                homeLogo.setHeight("30px");
                Span homeName = new Span(homeTeam);
                homeName.getStyle().set("font-weight", "500").set("color", "#f8fafc");

                Button homeBtn = new Button();
                if (homeOutcome != null && homeOutcome.point() != null) {
                    boolean isBurned = pickService.isSelectionBurned(player.getId(), sport, weekNumber, game.id(), "spread", homeTeam);

                    if (isBurned) {
                        homeBtn.setText("Burned");
                        homeBtn.setEnabled(false);
                    } else {
                        String pointStr = homeOutcome.point() > 0 ? "+" + homeOutcome.point() : String.valueOf(homeOutcome.point());
                        homeBtn.setText(pointStr);
                        String selectionStr = homeTeam + " " + pointStr;
                        homeBtn.addClickListener(e -> submitPick(player, slotNumber, sport, weekNumber, selectionStr, getLogoUrl(homeTeam, sport), game.id(), homeOutcome.point(), matchNameStr, "spread", homeTeam, onPickSaved));
                    }
                } else {
                    homeBtn.setText("N/A");
                    homeBtn.setEnabled(false);
                }
                homeBtn.setWidth("80px");
                homeBtn.addClassName("odds-btn");

                Button underBtn = new Button();
                if (underOutcome != null && underOutcome.point() != null) {
                    boolean isBurned = pickService.isSelectionBurned(player.getId(), sport, weekNumber, game.id(), "total", "Under");

                    if (isBurned) {
                        underBtn.setText("Burned");
                        underBtn.setEnabled(false);
                    } else {
                        String pointStr = "U " + underOutcome.point();
                        underBtn.setText(pointStr);
                        String selectionStr = matchNameStr + " " + pointStr;
                        underBtn.addClickListener(e -> submitPick(player, slotNumber, sport, weekNumber, selectionStr, dualLogoUrls, game.id(), underOutcome.point(), matchNameStr, "total", "Under", onPickSaved));
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