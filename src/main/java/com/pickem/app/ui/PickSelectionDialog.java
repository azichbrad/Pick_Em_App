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
import org.springframework.security.core.Authentication;
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
    private final Map<String, Integer> top25Map;

    public PickSelectionDialog(
            Player player, int slotNumber, String sport, int weekNumber,
            OddsService oddsService, PickService pickService, ConferenceService conferenceService,
            GameSyncService gameSyncService, Runnable onPickSaved
    ) {
        this.pickService = pickService;
        this.top25Map = sport.equals("NCAAF") ? conferenceService.getApTop25(weekNumber) : Map.of();

        List<Game> fetchedGames = gameSyncService.getGamesForSportAndWeekFromDb(sport, weekNumber);
        this.games = fetchedGames != null ? fetchedGames : List.of();

        getElement().setAttribute("theme", "dark");
        getElement().getClassList().add("pick-dialog-overlay");

        setHeaderTitle("Select Pick for " + player.getName() + " (Slot " + slotNumber + ")");
        setWidth("100%");
        setMaxWidth("560px");
        setHeight("740px");

        this.teamDataMap = sport.equals("NCAAF") ? conferenceService.getTeamDataMap() : Map.of();

// 1. Horizontal Scrollable Tabs
        com.vaadin.flow.component.tabs.Tabs conferenceTabs = new com.vaadin.flow.component.tabs.Tabs();
        conferenceTabs.setWidthFull();

        java.util.Map<com.vaadin.flow.component.tabs.Tab, String> tabToConferenceMap = new java.util.HashMap<>();

        if (sport.equals("NCAAF")) {
            List<String> dynamicConferences = new ArrayList<>();
            dynamicConferences.add("All");
            dynamicConferences.add("AP Top 25");
            dynamicConferences.addAll(teamDataMap.values().stream().map(TeamDTO::conference).distinct().sorted().toList());

            for (String conf : dynamicConferences) {
                com.vaadin.flow.component.tabs.Tab tab = new com.vaadin.flow.component.tabs.Tab(conf);
                tabToConferenceMap.put(tab, conf);
                conferenceTabs.add(tab);
            }
        } else {
            for (String conf : List.of("All", "AFC", "NFC")) {
                com.vaadin.flow.component.tabs.Tab tab = new com.vaadin.flow.component.tabs.Tab(conf);
                tabToConferenceMap.put(tab, conf);
                conferenceTabs.add(tab);
            }
        }

        // 2. Search field stays full width
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search team name...");
        searchField.setClearButtonVisible(true);
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidthFull();
        searchField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        searchField.getElement().setAttribute("theme", "dark");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);

        conferenceTabs.addSelectedChangeListener(event -> {
            String selectedConf = tabToConferenceMap.get(event.getSelectedTab());
            if (selectedConf != null) {
                renderGames(selectedConf, searchField.getValue(), player, slotNumber, sport, weekNumber, onPickSaved);
            }
        });

        searchField.addValueChangeListener(event -> {
            String selectedConf = tabToConferenceMap.get(conferenceTabs.getSelectedTab());
            renderGames(selectedConf, event.getValue(), player, slotNumber, sport, weekNumber, onPickSaved);
        });

        VerticalLayout filterBar = new VerticalLayout(conferenceTabs, searchField);
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
        Instant liveCutoff = Instant.now();
        Set<String> burnedSet = pickService.getBurnedSelectionKeys(player.getId(), sport, weekNumber);

        // 1. FILTER THE GAMES
        List<Game> filteredGames = new ArrayList<>();
        for (Game game : games) {
            String awayTeam = game.getAwayTeam() != null ? game.getAwayTeam() : "";
            String homeTeam = game.getHomeTeam() != null ? game.getHomeTeam() : "";

            boolean matchesSearch = query.isEmpty()
                    || awayTeam.toLowerCase().contains(query)
                    || homeTeam.toLowerCase().contains(query);
            if (!matchesSearch) continue;

            if (selectedConference != null && !selectedConference.equals("All") && sport.equals("NCAAF")) {
                if (selectedConference.equals("AP Top 25")) {
                    if (!top25Map.containsKey(awayTeam) && !top25Map.containsKey(homeTeam)) {
                        continue;
                    }
                } else {
                    String homeConf = getConference(homeTeam);
                    String awayConf = getConference(awayTeam);

                    if (!selectedConference.equals(homeConf) && !selectedConference.equals(awayConf)) {
                        continue;
                    }
                }
            }
            filteredGames.add(game);
        }

        // 2. SORT THE GAMES BY RANK (if AP Top 25 is selected)
        if ("AP Top 25".equals(selectedConference)) {
            filteredGames.sort((g1, g2) -> {
                int rank1 = Math.min(top25Map.getOrDefault(g1.getAwayTeam(), 99), top25Map.getOrDefault(g1.getHomeTeam(), 99));
                int rank2 = Math.min(top25Map.getOrDefault(g2.getAwayTeam(), 99), top25Map.getOrDefault(g2.getHomeTeam(), 99));
                return Integer.compare(rank1, rank2);
            });
        }

        int matchedCount = 0;

        // 3. RENDER THE UI
        for (Game game : filteredGames) {
            String awayTeam = game.getAwayTeam() != null ? game.getAwayTeam() : "";
            String homeTeam = game.getHomeTeam() != null ? game.getHomeTeam() : "";

            boolean isLiveOrFinished = game.getCommenceTime() != null &&
                    game.getCommenceTime().isBefore(liveCutoff);

            boolean isCompleted = Boolean.TRUE.equals(game.getCompleted());

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
            gameCard.getStyle().set("border-radius", "8px");
            gameCard.getStyle().set("padding", "6px 8px"); // Was 10px 12px
            gameCard.getStyle().set("margin-bottom", "6px"); // Was 10px
            gameCard.setSpacing(false);

            String matchNameStr = awayTeam + " @ " + homeTeam;

            // --- START TIME HEADER ---
            HorizontalLayout timeRow = new HorizontalLayout();
            timeRow.setWidthFull();
            timeRow.setJustifyContentMode(FlexComponent.JustifyContentMode.START); // Left-align the time
            timeRow.getStyle().set("margin-bottom", "2px");

            Span timeSpan = new Span();
            timeSpan.getStyle().set("font-size", "0.70em");

            if (isLiveOrFinished) {
                timeSpan.getStyle().set("color", "#ef4444").set("font-weight", "600");
            } else {
                timeSpan.getStyle().set("color", "#94a3b8");
            }

            if (game.getCommenceTime() != null) {
                long epochMillis = game.getCommenceTime().toEpochMilli();
                String js = "const d = new Date($0);" +
                        "let text = d.toLocaleDateString([], {weekday: 'short', month: 'short', day: 'numeric'}) + ' • ' + " +
                        "d.toLocaleTimeString([], {hour: 'numeric', minute: '2-digit', timeZoneName: 'short'});" +
                        "if ($1 && !$2) text += ' • IN PROGRESS';" +
                        "if ($2) text += ' • FINAL';" +
                        "this.textContent = text;";

                timeSpan.getElement().executeJs(js, (double) epochMillis, isLiveOrFinished, isCompleted);
            } else {
                String fallback = "TBD";
                if (isCompleted) fallback += " • FINAL";
                else if (isLiveOrFinished) fallback += " • IN PROGRESS";
                timeSpan.setText(fallback);
            }

            timeRow.add(timeSpan);

            // --- AWAY TEAM ROW ---
            HorizontalLayout awayRow = new HorizontalLayout();
            awayRow.setWidthFull();
            awayRow.setSpacing(false);
            awayRow.getStyle().set("gap", "6px");
            awayRow.setAlignItems(FlexComponent.Alignment.CENTER);

            Image awayLogo = new Image(awayLogoUrl, awayTeam + " logo");
            awayLogo.setWidth("20px"); // Was 26px
            awayLogo.setHeight("20px"); // Was 26px
            awayLogo.getStyle().set("flex-shrink", "0");

            String awayRank = top25Map.containsKey(awayTeam) ? "#" + top25Map.get(awayTeam) + " " : "";
            String awayScoreText = (isCompleted && game.getAwayScore() != null) ? " (" + Math.round(game.getAwayScore().doubleValue()) + ")" : "";
            Span awayName = new Span(awayRank + awayTeam + awayScoreText);
            styleTeamName(awayName);

            Button awayBtn = new Button();
            styleOddsButton(awayBtn);
            if (isLiveOrFinished) {
                awayBtn.setText("Locked");
                awayBtn.setEnabled(false);
            } else if (awayPoint != null) {
                String pointStr = awayPoint > 0 ? "+" + awayPoint : String.valueOf(awayPoint);
                awayBtn.setText(pointStr);

                if (burnedSet.contains(game.getId() + "_spread_" + awayTeam)) {
                    awayBtn.setEnabled(false);
                } else {
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
                String pointStr = "O " + overPoint;
                overBtn.setText(pointStr);

                if (burnedSet.contains(game.getId() + "_total_Over")) {
                    overBtn.setEnabled(false);
                } else {
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
            homeLogo.setWidth("20px"); // Was 26px
            homeLogo.setHeight("20px"); // Was 26px
            homeRow.getStyle().set("margin-top", "4px"); // Was 6px

            String homeRank = top25Map.containsKey(homeTeam) ? "#" + top25Map.get(homeTeam) + " " : "";
            String homeScoreText = (isCompleted && game.getHomeScore() != null) ? " (" + Math.round(game.getHomeScore().doubleValue()) + ")" : "";
            Span homeName = new Span(homeRank + homeTeam + homeScoreText);
            styleTeamName(homeName);

            Button homeBtn = new Button();
            styleOddsButton(homeBtn);
            if (isLiveOrFinished) {
                homeBtn.setText("Locked");
                homeBtn.setEnabled(false);
            } else if (homePoint != null) {
                String pointStr = homePoint > 0 ? "+" + homePoint : String.valueOf(homePoint);
                homeBtn.setText(pointStr);

                if (burnedSet.contains(game.getId() + "_spread_" + homeTeam)) {
                    homeBtn.setEnabled(false);
                } else {
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
                String pointStr = "U " + underPoint;
                underBtn.setText(pointStr);

                if (burnedSet.contains(game.getId() + "_total_Under")) {
                    underBtn.setEnabled(false);
                } else {
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
                .set("line-height", "1")
                // Force a single line with an ellipsis
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("min-width", "0")
                .set("margin-right", "4px");
    }

    private void styleOddsButton(Button btn) {
        btn.setWidth("62px"); // Was 68px
        btn.setHeight("28px"); // Was 36px
        btn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        btn.addClassName("odds-btn");
        btn.getStyle()
                .set("padding", "0")
                .set("font-size", "0.75rem") // Slightly smaller text
                .set("font-weight", "600")
                .set("letter-spacing", "-0.2px")
                .set("flex-shrink", "0");
    }

    private void submitPick(Player player, int slotNumber, String sport, int weekNumber, String selection,
                            String logoUrl, String gameId, Double lockedPoint, String matchName,
                            String marketType, String selectionSide, Runnable onPickSaved) {

        // Security check: ensure logged-in user owns this player slot or is Admin
        if (!canUserEditPlayer(player)) {
            System.out.println("Unauthorized pick attempt for player: " + player.getName());
            return;
        }

        // 1. Pass an empty lambda () -> {} so the service doesn't trigger the UI refresh prematurely
        pickService.savePickWithBurnCheck(player, slotNumber, sport, weekNumber, selection, logoUrl,
                gameId, lockedPoint, matchName, marketType, selectionSide, () -> {});

        // 2. The transaction is now fully committed! We can safely refresh the UI.
        if (onPickSaved != null) {
            onPickSaved.run();
        }

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // 1. Safely check if the Vaadin WebSocket lost the auth context
        if (auth == null || !auth.isAuthenticated()) {
            System.out.println("⚠️ Auth missing or user not authenticated.");
            return false;
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof OAuth2User oauth2User) {
            String loggedInEmail = oauth2User.getAttribute("email");
            String loggedInName = oauth2User.getAttribute("name");

            if (loggedInEmail == null) return false;

            // Admin Override
            boolean isAdmin = (loggedInName != null && loggedInName.toLowerCase().contains("azich"));
            if (isAdmin) {
                return true;
            }

            // Standard User Check
            if (targetPlayer.getEmail() != null && targetPlayer.getEmail().equalsIgnoreCase(loggedInEmail)) {
                return true;
            }
        }
        return false;
    }
}