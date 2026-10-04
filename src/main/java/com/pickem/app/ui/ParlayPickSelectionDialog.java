package com.pickem.app.ui;

import com.pickem.app.dto.TeamDTO;
import com.pickem.app.model.Game;
import com.pickem.app.model.Player;
import com.pickem.app.service.ConferenceService;
import com.pickem.app.service.GameSyncService;
import com.pickem.app.service.OddsService;
import com.pickem.app.service.ParlayService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParlayPickSelectionDialog extends Dialog {

    private final VerticalLayout gameListContainer = new VerticalLayout();
    private final ParlayService parlayService;
    private final GameSyncService gameSyncService;
    private final ConferenceService conferenceService;
    private final Player player;
    private final int weekNumber;
    private final Runnable onPickSaved;

    private String currentSport = "NCAAF";
    private Map<String, Integer> top25Map;
    private Map<String, TeamDTO> teamDataMap;
    private List<Game> currentGames = new ArrayList<>();

    private static final String TOTALS_ICON = "https://cdn-icons-png.flaticon.com/512/1199/1199155.png";

    public ParlayPickSelectionDialog(
            Player player, int weekNumber, ParlayService parlayService,
            OddsService oddsService, ConferenceService conferenceService,
            GameSyncService gameSyncService, Runnable onPickSaved
    ) {
        this.player = player;
        this.weekNumber = weekNumber;
        this.parlayService = parlayService;
        this.gameSyncService = gameSyncService;
        this.conferenceService = conferenceService;
        this.onPickSaved = onPickSaved;

        getElement().setAttribute("theme", "dark");
        getElement().getClassList().add("pick-dialog-overlay");

        setHeaderTitle("Select Parlay Leg for " + player.getName());
        setWidth("100%");
        setMaxWidth("560px");
        setHeight("740px");

        Tabs sportTabs = new Tabs();
        sportTabs.setWidthFull();
        Tab cfbTab = new Tab("NCAAF");
        Tab nflTab = new Tab("NFL");
        sportTabs.add(cfbTab, nflTab);

        TextField searchField = new TextField();
        searchField.setPlaceholder("Search team name...");
        searchField.setClearButtonVisible(true);
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidthFull();
        searchField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        searchField.getElement().setAttribute("theme", "dark");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);

        VerticalLayout filterBar = new VerticalLayout(sportTabs, searchField);
        filterBar.setWidthFull();
        filterBar.setPadding(false);
        filterBar.setSpacing(true);
        filterBar.getStyle().set("margin-bottom", "10px");

        gameListContainer.setSizeFull();
        gameListContainer.getStyle().set("overflow-y", "auto");
        gameListContainer.getStyle().set("padding-right", "2px");

        sportTabs.addSelectedChangeListener(event -> {
            this.currentSport = event.getSelectedTab().equals(cfbTab) ? "NCAAF" : "NFL";
            loadGamesForSport();
            renderGames(searchField.getValue());
        });

        searchField.addValueChangeListener(event -> renderGames(event.getValue()));

        loadGamesForSport();
        renderGames("");

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

    private void loadGamesForSport() {
        this.top25Map = currentSport.equals("NCAAF") ? conferenceService.getApTop25(weekNumber) : Map.of();
        this.teamDataMap = currentSport.equals("NCAAF") ? conferenceService.getTeamDataMap() : Map.of();
        List<Game> fetchedGames = gameSyncService.getGamesForSportAndWeekFromDb(currentSport, weekNumber);
        this.currentGames = fetchedGames != null ? fetchedGames : new ArrayList<>();
    }

    private void renderGames(String searchQuery) {
        gameListContainer.removeAll();

        if (currentGames.isEmpty()) {
            gameListContainer.add(new Span("No upcoming games found."));
            return;
        }

        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        Instant liveCutoff = Instant.now();
        int matchedCount = 0;

        for (Game game : currentGames) {
            String awayTeam = game.getAwayTeam() != null ? game.getAwayTeam() : "";
            String homeTeam = game.getHomeTeam() != null ? game.getHomeTeam() : "";

            if (!query.isEmpty() && !awayTeam.toLowerCase().contains(query) && !homeTeam.toLowerCase().contains(query)) {
                continue;
            }

            boolean isLiveOrFinished = game.getCommenceTime() != null && game.getCommenceTime().isBefore(liveCutoff);
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
            gameCard.getStyle().set("padding", "6px 8px");
            gameCard.getStyle().set("margin-bottom", "6px");
            gameCard.setSpacing(false);

            String matchNameStr = awayTeam + " @ " + homeTeam;

            HorizontalLayout timeRow = new HorizontalLayout();
            timeRow.setWidthFull();
            timeRow.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
            timeRow.getStyle().set("margin-bottom", "2px");

            Span timeSpan = new Span();
            timeSpan.getStyle().set("font-size", "0.70em");
            timeSpan.getStyle().set("color", isLiveOrFinished ? "#ef4444" : "#94a3b8");
            if (isLiveOrFinished) timeSpan.getStyle().set("font-weight", "600");

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
                timeSpan.setText("TBD");
            }
            timeRow.add(timeSpan);

            // --- AWAY TEAM ROW ---
            HorizontalLayout awayRow = new HorizontalLayout();
            awayRow.setWidthFull();
            awayRow.setSpacing(false);
            awayRow.getStyle().set("gap", "6px");
            awayRow.setAlignItems(FlexComponent.Alignment.CENTER);

            Image awayLogo = new Image(awayLogoUrl, awayTeam + " logo");
            awayLogo.setWidth("20px");
            awayLogo.setHeight("20px");
            awayLogo.getStyle().set("flex-shrink", "0");

            String awayRank = top25Map.containsKey(awayTeam) ? "#" + top25Map.get(awayTeam) + " " : "";
            Span awayName = new Span(awayRank + awayTeam);
            styleTeamName(awayName);

            awayRow.add(awayLogo, awayName); // 1. Add logo and name first

            // 2. Only inject a score span if the game is finished
            if (isCompleted && game.getAwayScore() != null) {
                Span awayScoreSpan = new Span(String.valueOf(Math.round(game.getAwayScore().doubleValue())));
                awayScoreSpan.getStyle()
                        .set("font-weight", "700")
                        .set("font-size", "0.95rem")
                        .set("color", "#f1f5f9")
                        .set("margin-left", "auto")
                        .set("margin-right", "4px")
                        .set("flex-shrink", "0");
                awayRow.add(awayScoreSpan);
            }

            Button awayBtn = new Button();
            styleOddsButton(awayBtn);
            if (isLiveOrFinished) {
                awayBtn.setText("Locked");
                awayBtn.setEnabled(false);
            } else if (awayPoint != null) {
                String pointStr = awayPoint > 0 ? "+" + awayPoint : String.valueOf(awayPoint);
                awayBtn.setText(pointStr);
                String selectionStr = awayTeam + " " + pointStr;
                awayBtn.addClickListener(e -> submitPick(selectionStr, awayLogoUrl, game.getId(), awayPoint, matchNameStr));
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
                String selectionStr = matchNameStr + " " + pointStr;
                overBtn.addClickListener(e -> submitPick(selectionStr, dualLogoUrls, game.getId(), overPoint, matchNameStr));
            } else {
                overBtn.setText("N/A");
                overBtn.setEnabled(false);
            }

            awayRow.add(awayBtn, overBtn); // 3. Append buttons last
            awayRow.expand(awayName);

// --- HOME TEAM ROW ---
            HorizontalLayout homeRow = new HorizontalLayout();
            homeRow.setWidthFull();
            homeRow.setSpacing(false);
            homeRow.getStyle().set("gap", "6px");
            homeRow.setAlignItems(FlexComponent.Alignment.CENTER);
            homeRow.getStyle().set("margin-top", "6px");

            Image homeLogo = new Image(homeLogoUrl, homeTeam + " logo");
            homeLogo.setWidth("20px");
            homeLogo.setHeight("20px");
            homeLogo.getStyle().set("flex-shrink", "0");

            String homeRank = top25Map.containsKey(homeTeam) ? "#" + top25Map.get(homeTeam) + " " : "";
            Span homeName = new Span(homeRank + homeTeam);
            styleTeamName(homeName);

            homeRow.add(homeLogo, homeName); // 1. Add logo and name first

            // 2. Only inject a score span if the game is finished
            if (isCompleted && game.getHomeScore() != null) {
                Span homeScoreSpan = new Span(String.valueOf(Math.round(game.getHomeScore().doubleValue())));
                homeScoreSpan.getStyle()
                        .set("font-weight", "700")
                        .set("font-size", "0.95rem")
                        .set("color", "#f1f5f9")
                        .set("margin-left", "auto")
                        .set("margin-right", "4px")
                        .set("flex-shrink", "0");
                homeRow.add(homeScoreSpan);
            }

            Button homeBtn = new Button();
            styleOddsButton(homeBtn);
            if (isLiveOrFinished) {
                homeBtn.setText("Locked");
                homeBtn.setEnabled(false);
            } else if (homePoint != null) {
                String pointStr = homePoint > 0 ? "+" + homePoint : String.valueOf(homePoint);
                homeBtn.setText(pointStr);
                String selectionStr = homeTeam + " " + pointStr;
                homeBtn.addClickListener(e -> submitPick(selectionStr, homeLogoUrl, game.getId(), homePoint, matchNameStr));
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
                String selectionStr = matchNameStr + " " + pointStr;
                underBtn.addClickListener(e -> submitPick(selectionStr, dualLogoUrls, game.getId(), underPoint, matchNameStr));
            } else {
                underBtn.setText("N/A");
                underBtn.setEnabled(false);
            }

            homeRow.add(homeBtn, underBtn); // 3. Append buttons last
            homeRow.expand(homeName);       // 4. Expand name to push buttons right

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
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("min-width", "0")
                .set("margin-right", "4px");
    }

    private void styleOddsButton(Button btn) {
        btn.setWidth("62px");
        btn.setHeight("28px");
        btn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        btn.addClassName("odds-btn");
        btn.getStyle()
                .set("padding", "0")
                .set("font-size", "0.75rem")
                .set("font-weight", "600")
                .set("letter-spacing", "-0.2px")
                .set("flex-shrink", "0")
                .set("position", "relative") // Pops button out of flat flow
                .set("z-index", "10");       // Protects against transparent overlays
    }

    private void submitPick(String selection, String logoUrl, String gameId, Double lockedPoint, String matchName) {
        parlayService.saveOrUpdatePick(player, weekNumber, currentSport, gameId, selection, lockedPoint, matchName, logoUrl);
        if (onPickSaved != null) {
            onPickSaved.run();
        }
        close();
    }
}