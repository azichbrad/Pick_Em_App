package com.pickem.app.ui;

import com.pickem.app.dto.TeamDTO;
import com.pickem.app.model.Game;
import com.pickem.app.model.Player;
import com.pickem.app.service.ConferenceService;
import com.pickem.app.service.GameSyncService;
import com.pickem.app.service.OddsService;
import com.pickem.app.service.PickService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PickSelectionDialog extends Dialog {

    private final VerticalLayout gameListContainer = new VerticalLayout();
    private final List<Game> games;
    private final Map<String, TeamDTO> teamDataMap;
    private final Map<String, String> localConferenceCache = new HashMap<>();
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
        this.pickService = pickService;

        List<Game> fetchedGames = gameSyncService.getGamesForSportAndWeekFromDb(sport, weekNumber);
        this.games = fetchedGames != null ? fetchedGames : List.of();

        getElement().setAttribute("theme", "dark");
        getElement().getClassList().add("pick-dialog-overlay");

        setHeaderTitle("Select Pick for " + player.getName() + " (Slot " + slotNumber + ")");
        setWidth("100%");
        setMaxWidth("560px");
        setHeight("740px");

        this.teamDataMap = sport.equals("NCAAF") ? conferenceService.getTeamDataMap() : Map.of();

        // 1. Make conference selector full width
        ComboBox<String> conferenceFilter = new ComboBox<>();
        conferenceFilter.setPlaceholder("All Conferences");
        conferenceFilter.setWidthFull(); // Changed from 170px
        conferenceFilter.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
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

        // 2. Search field stays full width
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search team name...");
        searchField.setClearButtonVisible(true);
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidthFull();
        searchField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        searchField.getElement().setAttribute("theme", "dark");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);

        conferenceFilter.addValueChangeListener(event -> {
            renderGames(event.getValue(), searchField.getValue(), player, slotNumber, sport, weekNumber, onPickSaved);
        });

        searchField.addValueChangeListener(event -> {
            renderGames(conferenceFilter.getValue(), event.getValue(), player, slotNumber, sport, weekNumber, onPickSaved);
        });

        VerticalLayout filterBar = new VerticalLayout(conferenceFilter, searchField);
        filterBar.setWidthFull();
        filterBar.setPadding(false);
        filterBar.setSpacing(true);
        filterBar.getStyle().set("margin-bottom", "10px");

        gameListContainer.setSizeFull();
        gameListContainer.getStyle().set("overflow-y", "auto");
        gameListContainer.getStyle().set("padding-right", "2px");

        renderGames("All", "", player, slotNumber, sport, weekNumber, onPickSaved);

        VerticalLayout dialogLayout = new VerticalLayout(filterBar, gameListContainer);
        dialogLayout.setPadding(false);
        dialogLayout.setSpacing(false);
        dialogLayout.setSizeFull();

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

        Instant liveCutoff = Instant.now().minus(Duration.ofMinutes(15));
        Set<String> burnedSet = pickService.getBurnedSelectionKeys(player.getId(), sport, weekNumber);

        for (Game game : games) {
            String awayTeam = game.getAwayTeam() != null ? game.getAwayTeam() : "";
            String homeTeam = game.getHomeTeam() != null ? game.getHomeTeam() : "";

            boolean matchesSearch = query.isEmpty()
                    || awayTeam.toLowerCase().contains(query)
                    || homeTeam.toLowerCase().contains(query);
            if (!matchesSearch) continue;

            if (selectedConference != null && !selectedConference.equals("All") && sport.equals("NCAAF")) {
                String homeConf = getConference(homeTeam);
                String awayConf = getConference(awayTeam);

                if (!selectedConference.equals(homeConf) && !selectedConference.equals(awayConf)) {
                    continue;
                }
            }

            boolean isLiveOrFinished = game.getCommenceTime() != null &&
                    game.getCommenceTime().isBefore(liveCutoff);

            matchedCount++;

            String awayLogoUrl = game.getAwayLogo() != null && !game.getAwayLogo().isBlank() ? game.getAwayLogo() : TOTALS_ICON;
            String homeLogoUrl = game.getHomeLogo() != null && !game.getHomeLogo().isBlank() ? game.getHomeLogo() : TOTALS_ICON;
            String dualLogoUrls = awayLogoUrl + "|" + homeLogoUrl;

            Double awayPoint = game.getAwaySpread();
            Double homePoint = game.getHomeSpread();
            Double overPoint = game.getOverTotal();
            Double underPoint = game.getUnderTotal();

            VerticalLayout gameCard = new VerticalLayout();
            gameCard.getStyle().set("background-color", "#151d30");
            gameCard.getStyle().set("border", isLiveOrFinished ? "1px solid #7f1d1d" : "1px solid #22304d");
            gameCard.getStyle().set("border-radius", "12px");
            gameCard.getStyle().set("padding", "10px 12px");
            gameCard.getStyle().set("margin-bottom", "10px");
            gameCard.setSpacing(false);

            String matchNameStr = awayTeam + " @ " + homeTeam;

            // --- START TIME HEADER ---
            HorizontalLayout timeRow = new HorizontalLayout();
            timeRow.setWidthFull();
            timeRow.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
            timeRow.getStyle().set("margin-bottom", "6px");

            String timeString = game.getCommenceTime() != null ? timeFormatter.format(game.getCommenceTime()) + " PT" : "TBD";
            Span timeSpan = new Span(isLiveOrFinished ? timeString + " • IN PROGRESS" : timeString);
            timeSpan.getStyle().set("font-size", "0.80em");

            if (isLiveOrFinished) {
                timeSpan.getStyle().set("color", "#ef4444").set("font-weight", "600");
            } else {
                timeSpan.getStyle().set("color", "#94a3b8");
            }
            timeRow.add(timeSpan);

            // --- AWAY TEAM ROW ---
            HorizontalLayout awayRow = new HorizontalLayout();
            awayRow.setWidthFull();
            awayRow.setSpacing(false);
            awayRow.getStyle().set("gap", "6px");
            awayRow.setAlignItems(FlexComponent.Alignment.CENTER);

            Image awayLogo = new Image(awayLogoUrl, awayTeam + " logo");
            awayLogo.setWidth("26px");
            awayLogo.setHeight("26px");
            awayLogo.getStyle().set("flex-shrink", "0");

            Span awayName = new Span(awayTeam);
            styleTeamName(awayName);

            Button awayBtn = new Button();
            styleOddsButton(awayBtn);
            if (isLiveOrFinished) {
                awayBtn.setText("Locked");
                awayBtn.setEnabled(false);
            } else if (awayPoint != null) {
                boolean isBurned = burnedSet.contains(game.getId() + "_spread_" + awayTeam);
                if (isBurned) {
                    awayBtn.setText("Burned");
                    awayBtn.setEnabled(false);
                } else {
                    String pointStr = awayPoint > 0 ? "+" + awayPoint : String.valueOf(awayPoint);
                    awayBtn.setText(pointStr);
                    String selectionStr = awayTeam + " " + pointStr;
                    awayBtn.addClickListener(e -> submitPick(player, slotNumber, sport, weekNumber, selectionStr, awayLogoUrl, game.getId(), awayPoint, matchNameStr, "spread", awayTeam, onPickSaved));
                }
            } else {
                awayBtn.setText("N/A");
                awayBtn.setEnabled(false);
            }

            Button overBtn = new Button();
            styleOddsButton(overBtn);
            if (isLiveOrFinished) {
                overBtn.setText("Locked");
                overBtn.setEnabled(false);
            } else if (overPoint != null) {
                boolean isBurned = burnedSet.contains(game.getId() + "_total_Over");
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

            awayRow.add(awayLogo, awayName, awayBtn, overBtn);
            awayRow.expand(awayName);

            // --- HOME TEAM ROW ---
            HorizontalLayout homeRow = new HorizontalLayout();
            homeRow.setWidthFull();
            homeRow.setSpacing(false);
            homeRow.getStyle().set("gap", "6px");
            homeRow.setAlignItems(FlexComponent.Alignment.CENTER);
            homeRow.getStyle().set("margin-top", "6px");

            Image homeLogo = new Image(homeLogoUrl, homeTeam + " logo");
            homeLogo.setWidth("26px");
            homeLogo.setHeight("26px");
            homeLogo.getStyle().set("flex-shrink", "0");

            Span homeName = new Span(homeTeam);
            styleTeamName(homeName);

            Button homeBtn = new Button();
            styleOddsButton(homeBtn);
            if (isLiveOrFinished) {
                homeBtn.setText("Locked");
                homeBtn.setEnabled(false);
            } else if (homePoint != null) {
                boolean isBurned = burnedSet.contains(game.getId() + "_spread_" + homeTeam);
                if (isBurned) {
                    homeBtn.setText("Burned");
                    homeBtn.setEnabled(false);
                } else {
                    String pointStr = homePoint > 0 ? "+" + homePoint : String.valueOf(homePoint);
                    homeBtn.setText(pointStr);
                    String selectionStr = homeTeam + " " + pointStr;
                    homeBtn.addClickListener(e -> submitPick(player, slotNumber, sport, weekNumber, selectionStr, homeLogoUrl, game.getId(), homePoint, matchNameStr, "spread", homeTeam, onPickSaved));
                }
            } else {
                homeBtn.setText("N/A");
                homeBtn.setEnabled(false);
            }

            Button underBtn = new Button();
            styleOddsButton(underBtn);
            if (isLiveOrFinished) {
                underBtn.setText("Locked");
                underBtn.setEnabled(false);
            } else if (underPoint != null) {
                boolean isBurned = burnedSet.contains(game.getId() + "_total_Under");
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

            homeRow.add(homeLogo, homeName, homeBtn, underBtn);
            homeRow.expand(homeName);

            gameCard.add(timeRow, awayRow, homeRow);
            gameListContainer.add(gameCard);
        }

        if (matchedCount == 0) {
            gameListContainer.add(new Span("No matching games found."));
        }
    }

    private void styleTeamName(Span span) {
        span.getStyle()
                .set("font-size", "0.85rem")
                .set("font-weight", "600")
                .set("color", "#f8fafc")
                .set("line-height", "1.15")
                .set("display", "-webkit-box")
                .set("-webkit-line-clamp", "2")
                .set("-webkit-box-orient", "vertical")
                .set("overflow", "hidden")
                .set("min-width", "0")
                .set("margin-right", "4px");
    }

    private void styleOddsButton(Button btn) {
        btn.setWidth("68px");
        btn.setHeight("36px");
        btn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        btn.addClassName("odds-btn");
        btn.getStyle()
                .set("padding", "0 2px")
                .set("font-size", "0.82rem")
                .set("font-weight", "600")
                .set("letter-spacing", "-0.2px")
                .set("flex-shrink", "0");
    }

    private void submitPick(Player player, int slotNumber, String sport, int weekNumber, String selection,
                            String logoUrl, String gameId, Double lockedPoint, String matchName,
                            String marketType, String selectionSide, Runnable onPickSaved) {

        // Security check: ensure logged-in user owns this player slot
        if (!canUserEditPlayer(player)) {
            System.out.println("Unauthorized pick attempt for player: " + player.getName());
            return;
        }

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
        if (localConferenceCache.containsKey(teamName)) {
            return localConferenceCache.get(teamName);
        }
        TeamDTO teamData = getTeamData(teamName);
        String conf = teamData != null ? teamData.conference() : "Other";
        localConferenceCache.put(teamName, conf);
        return conf;
    }

    private boolean canUserEditPlayer(Player targetPlayer) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof OAuth2User oauth2User) {
            String loggedInEmail = oauth2User.getAttribute("email");
            if (loggedInEmail == null) return false;

            // 1. Allow if the logged-in user's email matches the target player's assigned email
            if (targetPlayer.getEmail() != null && targetPlayer.getEmail().equalsIgnoreCase(loggedInEmail)) {
                return true;
            }

            // 2. Allow if the logged-in user is Brad (Admin) based on the database flag
            // (Assumes you set admin = true for your player row in Supabase)
            if (targetPlayer.isAdmin() && targetPlayer.getEmail() != null && targetPlayer.getEmail().equalsIgnoreCase(loggedInEmail)) {
                return true;
            }
        }
        return false;
    }
}