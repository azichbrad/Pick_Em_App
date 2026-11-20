package com.pickem.app.ui;

import com.pickem.app.model.AtsSituation;
import com.pickem.app.model.Game;
import com.pickem.app.model.TeamAtsSplit;
import com.pickem.app.service.game.GameAnalyticsService;
import com.pickem.app.service.game.GameAnalyticsService.MatchupAnalyticsDTO;
import com.pickem.app.service.game.GameAnalyticsService.TeamStatRankDTO;
import com.pickem.app.service.game.TeamAtsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.TabSheetVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.theme.lumo.Lumo;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalyticsDialog extends Dialog {

    private static final int CURRENT_SEASON = 2026;

    private enum MetricType {
        EPA("Off EPA"),
        SUCCESS("Success %"),
        HAVOC("Def Havoc"),
        ATS("ATS");

        final String label;
        MetricType(String label) { this.label = label; }
    }

    private final String sport;
    private final String weekLabel;
    private final boolean isPremium;
    private final TeamAtsService teamAtsService;
    private final List<TeamStatRankDTO> teamStats;
    private final List<MatchupAnalyticsDTO> matchups;
    private final Map<String, String> logoByTeamLower = new HashMap<>();
    private final Map<String, Map<String, TeamAtsSplit>> matchupAtsSplits;
    private final Map<AtsSituation, List<TeamAtsSplit>> atsRankCache = new HashMap<>();

    private MetricType activeMetric = MetricType.EPA;
    private AtsSituation activeSituation = AtsSituation.ALL_GAMES;
    private String searchQuery = "";
    private HorizontalLayout situationRow;
    private VerticalLayout rankListContainer;
    private final Map<MetricType, Button> metricButtons = new HashMap<>();

    public AnalyticsDialog(String sport,
                           String weekLabel,
                           boolean isPremium,
                           List<Game> weeklyGames,
                           GameAnalyticsService analyticsService,
                           TeamAtsService teamAtsService) {
        this.sport = sport;
        this.weekLabel = weekLabel;
        this.isPremium = isPremium;
        this.teamAtsService = teamAtsService;
        this.teamStats = analyticsService.getTeamStatsForSport(sport);
        this.matchups = analyticsService.getMatchupAnalyticsForGames(weeklyGames);

        // Index team logos for reuse on ATS rank rows
        for (TeamStatRankDTO t : teamStats) {
            if (t.teamName() != null && t.logoUrl() != null && !t.logoUrl().isBlank()) {
                logoByTeamLower.put(t.teamName().toLowerCase(), t.logoUrl());
            }
        }
        Set<String> matchupTeams = new HashSet<>();
        for (MatchupAnalyticsDTO m : matchups) {
            if (m.awayTeam() != null) {
                matchupTeams.add(m.awayTeam());
                if (m.awayLogo() != null && !m.awayLogo().isBlank()) {
                    logoByTeamLower.putIfAbsent(m.awayTeam().toLowerCase(), m.awayLogo());
                }
            }
            if (m.homeTeam() != null) {
                matchupTeams.add(m.homeTeam());
                if (m.homeLogo() != null && !m.homeLogo().isBlank()) {
                    logoByTeamLower.putIfAbsent(m.homeTeam().toLowerCase(), m.homeLogo());
                }
            }
        }

        // Single batch lookup for all teams on this week's matchup cards
        this.matchupAtsSplits = teamAtsService.getSplitsByTeam(sport, CURRENT_SEASON, matchupTeams);

        addThemeName(Lumo.DARK);
        setWidth("min(95vw, 680px)");
        setMaxHeight("88vh");
        setCloseOnOutsideClick(true);

        String displaySport = "NCAAF".equalsIgnoreCase(sport) ? "CFB" : "NFL";
        setHeaderTitle("📊 " + displaySport + " Analytics");

        if (isPremium) {
            Span proBadge = new Span("PRO");
            proBadge.getStyle()
                    .set("background", "linear-gradient(135deg, #f59e0b, #d97706)")
                    .set("color", "#0b0f19")
                    .set("font-size", "0.7rem")
                    .set("font-weight", "800")
                    .set("padding", "2px 8px")
                    .set("border-radius", "9999px")
                    .set("margin-right", "8px");
            getHeader().add(proBadge);
        }

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), e -> close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        getHeader().add(closeBtn);

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.addThemeVariants(TabSheetVariant.LUMO_TABS_CENTERED);
        tabs.add("🏆 Ranks (" + teamStats.size() + ")", buildTeamRanksTab());
        tabs.add("⚔️ " + weekLabel, buildMatchupsTab());

        add(tabs);
    }

    // ==================== TAB 1: TEAM RANKS ====================

    private VerticalLayout buildTeamRanksTab() {
        VerticalLayout wrapper = new VerticalLayout();
        wrapper.setPadding(false);
        wrapper.setSpacing(true);
        wrapper.setWidthFull();

        HorizontalLayout metricBar = new HorizontalLayout();
        metricBar.setWidthFull();
        metricBar.getStyle().set("flex-wrap", "wrap").set("gap", "6px");

        for (MetricType type : MetricType.values()) {
            Button pill = new Button(type.label, e -> {
                activeMetric = type;
                refreshMetricPills();
                situationRow.setVisible(activeMetric == MetricType.ATS);
                renderTeamRanks();
            });
            pill.getStyle()
                    .set("border-radius", "9999px")
                    .set("font-size", "0.78rem")
                    .set("font-weight", "600")
                    .set("padding", "0 10px")
                    .set("min-width", "0")
                    .set("flex", "1 1 auto");
            metricButtons.put(type, pill);
            metricBar.add(pill);
        }

        // Contextual Situation dropdown (visible only on ATS pill)
        Span situationLabel = new Span("Situation:");
        situationLabel.getStyle()
                .set("color", "#94a3b8")
                .set("font-size", "0.82rem")
                .set("font-weight", "600")
                .set("white-space", "nowrap");

        ComboBox<AtsSituation> situationSelect = new ComboBox<>();
        situationSelect.setItems(AtsSituation.values());
        situationSelect.setItemLabelGenerator(this::formatSituationLabel);
        situationSelect.setValue(activeSituation);
        situationSelect.setAllowCustomValue(false);
        situationSelect.setWidthFull();
        situationSelect.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                activeSituation = e.getValue();
                renderTeamRanks();
            }
        });

        situationRow = new HorizontalLayout(situationLabel, situationSelect);
        situationRow.setWidthFull();
        situationRow.setAlignItems(FlexComponent.Alignment.CENTER);
        situationRow.setFlexGrow(1, situationSelect);
        situationRow.getStyle().set("gap", "8px");
        situationRow.setVisible(false);

        TextField searchField = new TextField();
        searchField.setPlaceholder("Search team...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidthFull();
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> {
            searchQuery = e.getValue() == null ? "" : e.getValue().trim().toLowerCase();
            renderTeamRanks();
        });

        rankListContainer = new VerticalLayout();
        rankListContainer.setPadding(false);
        rankListContainer.setSpacing(false);
        rankListContainer.setWidthFull();
        rankListContainer.getStyle().set("gap", "8px");

        refreshMetricPills();
        renderTeamRanks();

        wrapper.add(metricBar, situationRow, searchField, rankListContainer);
        return wrapper;
    }

    private void refreshMetricPills() {
        metricButtons.forEach((type, btn) -> {
            if (type == activeMetric) {
                btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                btn.getStyle().set("background-color", "#2563eb").set("color", "#ffffff");
            } else {
                btn.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
                btn.getStyle().set("background-color", "#1e293b").set("color", "#cbd5e1");
            }
        });
    }

    private void renderTeamRanks() {
        rankListContainer.removeAll();

        if (activeMetric == MetricType.ATS) {
            renderAtsRanks();
            return;
        }

        if (teamStats.isEmpty()) {
            Span empty = new Span("No analytics synced yet for " + sport + ".");
            empty.getStyle().set("color", "#94a3b8").set("padding", "16px 0");
            rankListContainer.add(empty);
            return;
        }

        List<TeamStatRankDTO> sorted = new ArrayList<>(teamStats);
        sorted.sort(Comparator.comparing(
                this::getMetricValue,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        double maxVal = sorted.stream()
                .map(this::getMetricValue)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .max().orElse(1.0);
        double minVal = sorted.stream()
                .map(this::getMetricValue)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .min().orElse(0.0);
        double range = Math.max(maxVal - minVal, 0.0001);

        VerticalLayout lockedRows = new VerticalLayout();
        lockedRows.setPadding(false);
        lockedRows.setSpacing(false);
        lockedRows.setWidthFull();
        lockedRows.getStyle().set("gap", "8px");

        int hiddenCount = 0;

        for (int i = 0; i < sorted.size(); i++) {
            int rank = i + 1;
            TeamStatRankDTO team = sorted.get(i);

            if (!searchQuery.isEmpty() && !team.teamName().toLowerCase().contains(searchQuery)) {
                continue;
            }

            Div row = buildTeamRankRow(rank, team, minVal, range);

            if (isPremium || rank <= 5) {
                rankListContainer.add(row);
            } else {
                hiddenCount++;
                if (hiddenCount <= 4) {
                    lockedRows.add(row);
                }
            }
        }

        if (!isPremium && hiddenCount > 0) {
            rankListContainer.add(wrapWithPaywall(
                    lockedRows,
                    "Unlock Full 1–" + sorted.size() + " Stat Rankings",
                    "Upgrade to PRO to view complete EPA, Success Rate, Havoc, and ATS leaderboards."
            ));
        }
    }

    private void renderAtsRanks() {
        List<TeamAtsSplit> splits = atsRankCache.computeIfAbsent(
                activeSituation,
                sit -> teamAtsService.getRankedSplits(sport, CURRENT_SEASON, sit.name())
        );

        if (splits.isEmpty()) {
            Span empty = new Span("No ATS splits synced yet for " + sport + ".");
            empty.getStyle().set("color", "#94a3b8").set("padding", "16px 0");
            rankListContainer.add(empty);
            return;
        }

        VerticalLayout lockedRows = new VerticalLayout();
        lockedRows.setPadding(false);
        lockedRows.setSpacing(false);
        lockedRows.setWidthFull();
        lockedRows.getStyle().set("gap", "8px");

        int hiddenCount = 0;

        for (int i = 0; i < splits.size(); i++) {
            int rank = i + 1;
            TeamAtsSplit split = splits.get(i);

            if (!searchQuery.isEmpty() && !split.getTeamName().toLowerCase().contains(searchQuery)) {
                continue;
            }

            Div row = buildAtsRankRow(rank, split);

            if (isPremium || rank <= 5) {
                rankListContainer.add(row);
            } else {
                hiddenCount++;
                if (hiddenCount <= 4) {
                    lockedRows.add(row);
                }
            }
        }

        if (!isPremium && hiddenCount > 0) {
            rankListContainer.add(wrapWithPaywall(
                    lockedRows,
                    "Unlock Full 1–" + splits.size() + " ATS Rankings",
                    "Upgrade to PRO to view all situational ATS splits across every team."
            ));
        }
    }

    private Div buildAtsRankRow(int rank, TeamAtsSplit split) {
        int decided = split.getWins() + split.getLosses();
        double pct = decided > 0 ? Math.max(4, Math.min(100, split.getCoverPct())) : 0;

        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background-color", "#151d30")
                .set("border", "1px solid #22304d")
                .set("border-radius", "12px")
                .set("padding", "10px 12px")
                .set("box-sizing", "border-box");

        HorizontalLayout top = new HorizontalLayout();
        top.setWidthFull();
        top.setAlignItems(FlexComponent.Alignment.CENTER);
        top.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        top.setSpacing(false);

        HorizontalLayout left = new HorizontalLayout();
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.setSpacing(false);
        left.getStyle().set("gap", "8px").set("min-width", "0");

        Span rankBadge = new Span("#" + rank);
        rankBadge.getStyle()
                .set("min-width", "32px")
                .set("font-weight", "800")
                .set("font-size", "0.85rem")
                .set("color", (rank <= 5 && decided > 0) ? "#38bdf8" : "#94a3b8");
        left.add(rankBadge);

        String logoUrl = logoByTeamLower.get(split.getTeamName().toLowerCase());
        if (logoUrl != null && !logoUrl.isBlank()) {
            Image logo = new Image(logoUrl, split.getTeamName());
            logo.setWidth("22px");
            logo.setHeight("22px");
            logo.getStyle().set("flex-shrink", "0");
            left.add(logo);
        }

        Span nameSpan = new Span(split.getTeamName());
        nameSpan.getStyle().set("font-weight", "600").set("font-size", "0.9rem").set("color", "#f8fafc");
        left.add(nameSpan);

        HorizontalLayout right = new HorizontalLayout();
        right.setAlignItems(FlexComponent.Alignment.BASELINE);
        right.setSpacing(false);
        right.getStyle().set("gap", "8px").set("flex-shrink", "0");

        Span recordSpan = new Span(formatAtsRecord(split));
        String recordColor = decided == 0
                ? "#64748b"
                : (split.getCoverPct() >= 50.0 ? "#34d399" : "#f87171");
        recordSpan.getStyle()
                .set("font-weight", "700")
                .set("font-size", "0.9rem")
                .set("color", recordColor);

        Span pctSpan = new Span(decided > 0 ? String.format("%.1f%%", split.getCoverPct()) : "—");
        pctSpan.getStyle()
                .set("font-weight", "600")
                .set("font-size", "0.82rem")
                .set("color", "#94a3b8")
                .set("min-width", "44px")
                .set("text-align", "right");

        right.add(recordSpan, pctSpan);
        top.add(left, right);

        Div track = new Div();
        track.setWidthFull();
        track.getStyle()
                .set("height", "5px")
                .set("background-color", "#0b0f19")
                .set("border-radius", "9999px")
                .set("margin-top", "7px")
                .set("overflow", "hidden");

        Div fill = new Div();
        fill.getStyle()
                .set("height", "100%")
                .set("width", String.format("%.1f%%", pct))
                .set("background", (rank <= 10 && decided > 0)
                        ? "linear-gradient(90deg, #38bdf8, #34d399)"
                        : "#475569")
                .set("border-radius", "9999px");
        track.add(fill);

        card.add(top, track);
        return card;
    }

    private Div buildTeamRankRow(int rank, TeamStatRankDTO team, double minVal, double range) {
        Double val = getMetricValue(team);
        double pct = val == null ? 0 : Math.max(4, Math.min(100, ((val - minVal) / range) * 100.0));

        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background-color", "#151d30")
                .set("border", "1px solid #22304d")
                .set("border-radius", "12px")
                .set("padding", "10px 12px")
                .set("box-sizing", "border-box");

        HorizontalLayout top = new HorizontalLayout();
        top.setWidthFull();
        top.setAlignItems(FlexComponent.Alignment.CENTER);
        top.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        top.setSpacing(false);

        HorizontalLayout left = new HorizontalLayout();
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.setSpacing(false);
        left.getStyle().set("gap", "8px");

        Span rankBadge = new Span("#" + rank);
        rankBadge.getStyle()
                .set("min-width", "32px")
                .set("font-weight", "800")
                .set("font-size", "0.85rem")
                .set("color", rank <= 5 ? "#38bdf8" : "#94a3b8");
        left.add(rankBadge);

        if (team.logoUrl() != null && !team.logoUrl().isBlank()) {
            Image logo = new Image(team.logoUrl(), team.teamName());
            logo.setWidth("22px");
            logo.setHeight("22px");
            logo.getStyle().set("flex-shrink", "0");
            left.add(logo);
        }

        Span nameSpan = new Span(team.teamName());
        nameSpan.getStyle().set("font-weight", "600").set("font-size", "0.9rem").set("color", "#f8fafc");
        left.add(nameSpan);

        Span valSpan = new Span(formatMetric(val));
        valSpan.getStyle()
                .set("font-weight", "700")
                .set("font-size", "0.9rem")
                .set("color", getMetricColor(val));

        top.add(left, valSpan);

        Div track = new Div();
        track.setWidthFull();
        track.getStyle()
                .set("height", "5px")
                .set("background-color", "#0b0f19")
                .set("border-radius", "9999px")
                .set("margin-top", "7px")
                .set("overflow", "hidden");

        Div fill = new Div();
        fill.getStyle()
                .set("height", "100%")
                .set("width", String.format("%.1f%%", pct))
                .set("background", rank <= 10 ? "linear-gradient(90deg, #38bdf8, #34d399)" : "#475569")
                .set("border-radius", "9999px");
        track.add(fill);

        card.add(top, track);
        return card;
    }

    // ==================== TAB 2: WEEK MATCHUPS (SASSER SLIDERS) ====================

    private VerticalLayout buildMatchupsTab() {
        VerticalLayout container = new VerticalLayout();
        container.setPadding(false);
        container.setSpacing(false);
        container.setWidthFull();
        container.getStyle().set("gap", "12px");

        List<MatchupAnalyticsDTO> withData = matchups.stream()
                .filter(m -> m.awayEpa() != null && m.homeEpa() != null)
                .toList();

        if (withData.isEmpty()) {
            Span empty = new Span("No matchup analytics available for " + weekLabel + " yet.");
            empty.getStyle().set("color", "#94a3b8").set("padding", "16px 0");
            container.add(empty);
            return container;
        }

        VerticalLayout lockedCards = new VerticalLayout();
        lockedCards.setPadding(false);
        lockedCards.setSpacing(false);
        lockedCards.setWidthFull();
        lockedCards.getStyle().set("gap", "12px");

        int hidden = 0;
        for (int i = 0; i < withData.size(); i++) {
            Div card = buildMatchupCard(withData.get(i));
            if (isPremium || i == 0) {
                container.add(card);
            } else {
                hidden++;
                if (hidden <= 2) {
                    lockedCards.add(card);
                }
            }
        }

        if (!isPremium && hidden > 0) {
            container.add(wrapWithPaywall(
                    lockedCards,
                    "Unlock All " + withData.size() + " Matchup Breakdowns",
                    "Get head-to-head EPA, Success Rate, Havoc, situational ATS edges, and AI storylines for every game."
            ));
        }

        return container;
    }

    private Div buildMatchupCard(MatchupAnalyticsDTO m) {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background-color", "#151d30")
                .set("border", "1px solid #22304d")
                .set("border-radius", "14px")
                .set("padding", "14px")
                .set("box-sizing", "border-box");

        if (m.commenceTime() != null) {
            String timeText = m.commenceTime()
                    .atZone(ZoneId.of("America/Los_Angeles"))
                    .format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a z"));
            Span timeSpan = new Span(timeText);
            timeSpan.getStyle().set("font-size", "0.75rem").set("color", "#94a3b8").set("display", "block").set("margin-bottom", "8px");
            card.add(timeSpan);
        }

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set("margin-bottom", "12px");

        Span atSign = new Span("@");
        atSign.getStyle().set("color", "#64748b").set("font-weight", "700").set("flex-shrink", "0");

        header.add(
                buildTeamSideHeader(m.awayTeam(), m.awayLogo(), m.awaySpread(), false),
                atSign,
                buildTeamSideHeader(m.homeTeam(), m.homeLogo(), m.homeSpread(), true)
        );
        card.add(header);

        card.add(
                buildComparisonBar("Off EPA / Play", m.awayEpa(), m.homeEpa(), MetricType.EPA),
                buildComparisonBar("Success Rate", m.awaySuccessRate(), m.homeSuccessRate(), MetricType.SUCCESS),
                buildComparisonBar("Def Havoc Rate", m.awayHavoc(), m.homeHavoc(), MetricType.HAVOC)
        );

        // Contextual ATS comparison rows
        Map<String, TeamAtsSplit> awaySplits = matchupAtsSplits.get(m.awayTeam());
        Map<String, TeamAtsSplit> homeSplits = matchupAtsSplits.get(m.homeTeam());
        if (awaySplits != null || homeSplits != null) {
            Div divider = new Div();
            divider.setWidthFull();
            divider.getStyle()
                    .set("height", "1px")
                    .set("background-color", "#22304d")
                    .set("margin", "10px 0 8px 0");
            card.add(divider);

            TeamAtsSplit awayOverall = awaySplits != null ? awaySplits.get(AtsSituation.ALL_GAMES.name()) : null;
            TeamAtsSplit homeOverall = homeSplits != null ? homeSplits.get(AtsSituation.ALL_GAMES.name()) : null;
            card.add(buildAtsComparisonBar("Season ATS", awayOverall, homeOverall));

            // Match away/home situational split directly to the live spread
            boolean homeFav = (m.homeSpread() != null && m.homeSpread() < 0)
                    || (m.awaySpread() != null && m.awaySpread() > 0);
            boolean awayFav = (m.homeSpread() != null && m.homeSpread() > 0)
                    || (m.awaySpread() != null && m.awaySpread() < 0);

            String awaySitKey;
            String homeSitKey;
            String roleLabel;
            if (homeFav) {
                awaySitKey = AtsSituation.AWAY_DOG.name();
                homeSitKey = AtsSituation.HOME_FAV.name();
                roleLabel = "Away Dog vs Home Fav";
            } else if (awayFav) {
                awaySitKey = AtsSituation.AWAY_FAV.name();
                homeSitKey = AtsSituation.HOME_DOG.name();
                roleLabel = "Away Fav vs Home Dog";
            } else {
                awaySitKey = AtsSituation.AWAY.name();
                homeSitKey = AtsSituation.HOME.name();
                roleLabel = "Away vs Home ATS";
            }

            TeamAtsSplit awayRole = awaySplits != null ? awaySplits.get(awaySitKey) : null;
            TeamAtsSplit homeRole = homeSplits != null ? homeSplits.get(homeSitKey) : null;
            card.add(buildAtsComparisonBar(roleLabel, awayRole, homeRole));
        }

        if (m.aiStoryline() != null && !m.aiStoryline().isBlank()) {
            Div storyBox = new Div();
            storyBox.getStyle()
                    .set("margin-top", "10px")
                    .set("padding", "10px")
                    .set("background-color", "rgba(56, 189, 248, 0.08)")
                    .set("border-left", "3px solid #38bdf8")
                    .set("border-radius", "6px")
                    .set("font-size", "0.82rem")
                    .set("color", "#e2e8f0");
            storyBox.setText("✨ " + m.aiStoryline());
            card.add(storyBox);
        }

        return card;
    }

    private Div buildAtsComparisonBar(String label, TeamAtsSplit awaySplit, TeamAtsSplit homeSplit) {
        Div wrapper = new Div();
        wrapper.setWidthFull();
        wrapper.getStyle().set("margin-bottom", "8px");

        boolean awayHasGames = awaySplit != null && (awaySplit.getWins() + awaySplit.getLosses()) > 0;
        boolean homeHasGames = homeSplit != null && (homeSplit.getWins() + homeSplit.getLosses()) > 0;

        boolean awayWins = false;
        boolean homeWins = false;
        if (awayHasGames && homeHasGames) {
            if (awaySplit.getCoverPct() > homeSplit.getCoverPct()) {
                awayWins = true;
            } else if (homeSplit.getCoverPct() > awaySplit.getCoverPct()) {
                homeWins = true;
            } else if (awaySplit.getNetCovers() > homeSplit.getNetCovers()) {
                awayWins = true;
            } else if (homeSplit.getNetCovers() > awaySplit.getNetCovers()) {
                homeWins = true;
            }
        }

        HorizontalLayout labels = new HorizontalLayout();
        labels.setWidthFull();
        labels.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        labels.setSpacing(false);

        Span awaySpan = new Span(formatAtsCompact(awaySplit));
        awaySpan.getStyle().set("font-size", "0.78rem").set("font-weight", "700")
                .set("color", awayWins ? "#34d399" : "#94a3b8");

        Span titleSpan = new Span(label);
        titleSpan.getStyle().set("font-size", "0.74rem").set("color", "#cbd5e1").set("text-align", "center");

        Span homeSpan = new Span(formatAtsCompact(homeSplit));
        homeSpan.getStyle().set("font-size", "0.78rem").set("font-weight", "700")
                .set("color", homeWins ? "#34d399" : "#94a3b8");

        labels.add(awaySpan, titleSpan, homeSpan);

        double aWeight = awayHasGames ? Math.max(8.0, awaySplit.getCoverPct()) : 50.0;
        double hWeight = homeHasGames ? Math.max(8.0, homeSplit.getCoverPct()) : 50.0;
        double awayPct = (!awayHasGames && !homeHasGames) ? 50.0 : (aWeight / (aWeight + hWeight)) * 100.0;

        Div bar = new Div();
        bar.setWidthFull();
        bar.getStyle()
                .set("display", "flex")
                .set("height", "7px")
                .set("border-radius", "9999px")
                .set("overflow", "hidden")
                .set("margin-top", "4px")
                .set("gap", "2px");

        Div awayFill = new Div();
        awayFill.getStyle()
                .set("width", String.format("%.1f%%", awayPct))
                .set("background-color", awayWins ? "#34d399" : "#475569");

        Div homeFill = new Div();
        homeFill.getStyle()
                .set("width", String.format("%.1f%%", 100.0 - awayPct))
                .set("background-color", homeWins ? "#38bdf8" : "#475569");

        bar.add(awayFill, homeFill);
        wrapper.add(labels, bar);
        return wrapper;
    }

    private HorizontalLayout buildTeamSideHeader(String name, String logoUrl, Double spread, boolean alignRight) {
        HorizontalLayout side = new HorizontalLayout();
        side.setAlignItems(FlexComponent.Alignment.CENTER);
        side.setSpacing(false);
        side.getStyle().set("gap", "6px").set("flex", "1 1 0");

        String spreadStr = spread != null ? (spread > 0 ? " (+" + spread + ")" : " (" + spread + ")") : "";
        Span label = new Span(name + spreadStr);
        label.getStyle().set("font-weight", "700").set("font-size", "0.85rem").set("color", "#f8fafc");

        Image logo = null;
        if (logoUrl != null && !logoUrl.isBlank()) {
            logo = new Image(logoUrl, name);
            logo.setWidth("22px");
            logo.setHeight("22px");
            logo.getStyle().set("flex-shrink", "0");
        }

        if (alignRight) {
            label.getStyle().set("text-align", "right");
            side.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
            side.add(label);
            if (logo != null) side.add(logo);
        } else {
            side.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
            if (logo != null) side.add(logo);
            side.add(label);
        }
        return side;
    }

    private Div buildComparisonBar(String label, Double awayVal, Double homeVal, MetricType type) {
        Div wrapper = new Div();
        wrapper.setWidthFull();
        wrapper.getStyle().set("margin-bottom", "8px");

        boolean awayWins = awayVal != null && homeVal != null && awayVal > homeVal;
        boolean homeWins = awayVal != null && homeVal != null && homeVal > awayVal;

        HorizontalLayout labels = new HorizontalLayout();
        labels.setWidthFull();
        labels.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        labels.setSpacing(false);

        Span awaySpan = new Span(formatMetricByType(awayVal, type));
        awaySpan.getStyle().set("font-size", "0.78rem").set("font-weight", "700")
                .set("color", awayWins ? "#34d399" : "#94a3b8");

        Span titleSpan = new Span(label);
        titleSpan.getStyle().set("font-size", "0.75rem").set("color", "#cbd5e1");

        Span homeSpan = new Span(formatMetricByType(homeVal, type));
        homeSpan.getStyle().set("font-size", "0.78rem").set("font-weight", "700")
                .set("color", homeWins ? "#34d399" : "#94a3b8");

        labels.add(awaySpan, titleSpan, homeSpan);

        double aNorm = awayVal == null ? 0.5 : (type == MetricType.EPA ? Math.max(0.02, awayVal + 0.35) : Math.max(0.01, awayVal));
        double hNorm = homeVal == null ? 0.5 : (type == MetricType.EPA ? Math.max(0.02, homeVal + 0.35) : Math.max(0.01, homeVal));
        double awayPct = (aNorm / (aNorm + hNorm)) * 100.0;

        Div bar = new Div();
        bar.setWidthFull();
        bar.getStyle()
                .set("display", "flex")
                .set("height", "7px")
                .set("border-radius", "9999px")
                .set("overflow", "hidden")
                .set("margin-top", "4px")
                .set("gap", "2px");

        Div awayFill = new Div();
        awayFill.getStyle()
                .set("width", String.format("%.1f%%", awayPct))
                .set("background-color", awayWins ? "#34d399" : "#475569");

        Div homeFill = new Div();
        homeFill.getStyle()
                .set("width", String.format("%.1f%%", 100.0 - awayPct))
                .set("background-color", homeWins ? "#38bdf8" : "#475569");

        bar.add(awayFill, homeFill);
        wrapper.add(labels, bar);
        return wrapper;
    }

    // ==================== PAYWALL OVERLAY ====================

    private Div wrapWithPaywall(VerticalLayout blurredContent, String headline, String subtext) {
        Div shell = new Div();
        shell.setWidthFull();
        shell.getStyle()
                .set("position", "relative")
                .set("margin-top", "4px")
                .set("min-height", "210px");

        blurredContent.getStyle()
                .set("filter", "blur(6px)")
                .set("opacity", "0.45")
                .set("pointer-events", "none")
                .set("user-select", "none");

        VerticalLayout overlay = new VerticalLayout();
        overlay.setAlignItems(FlexComponent.Alignment.CENTER);
        overlay.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        overlay.getStyle()
                .set("position", "absolute")
                .set("inset", "0")
                .set("background", "rgba(11, 15, 25, 0.72)")
                .set("border", "1px solid rgba(56, 189, 248, 0.35)")
                .set("border-radius", "14px")
                .set("padding", "28px 20px 20px 20px")
                .set("text-align", "center");

        H3 title = new H3("🔒 " + headline);
        title.getStyle().set("margin", "0").set("color", "#f8fafc").set("font-size", "1.05rem");

        Span desc = new Span(subtext);
        desc.getStyle().set("color", "#cbd5e1").set("font-size", "0.85rem").set("max-width", "360px");

        Button unlockBtn = new Button("Unlock PRO Analytics", e -> openUnlockModal());
        unlockBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        unlockBtn.getStyle()
                .set("background", "linear-gradient(135deg, #f59e0b, #d97706)")
                .set("color", "#0b0f19")
                .set("font-weight", "800")
                .set("border-radius", "9999px");

        overlay.add(title, desc, unlockBtn);
        shell.add(blurredContent, overlay);
        return shell;
    }

    private void openUnlockModal() {
        Notification.show("PRO Unlock coming soon! Contact your commissioner to activate.", 4000, Notification.Position.MIDDLE);
    }

    // ==================== FORMATTING HELPERS ====================

    private String formatSituationLabel(AtsSituation sit) {
        if (sit == AtsSituation.DIVISION) {
            return "NCAAF".equalsIgnoreCase(sport) ? "Conference Games" : "Division Games";
        }
        return sit.getLabel();
    }

    private String formatAtsRecord(TeamAtsSplit split) {
        if (split == null) return "—";
        return split.getPushes() > 0
                ? split.getWins() + "-" + split.getLosses() + "-" + split.getPushes()
                : split.getWins() + "-" + split.getLosses();
    }

    private String formatAtsCompact(TeamAtsSplit split) {
        if (split == null) return "—";
        int decided = split.getWins() + split.getLosses();
        String rec = formatAtsRecord(split);
        return decided > 0 ? String.format("%s (%.0f%%)", rec, split.getCoverPct()) : rec;
    }

    private Double getMetricValue(TeamStatRankDTO team) {
        return switch (activeMetric) {
            case EPA -> team.epa();
            case SUCCESS -> team.successRate();
            case HAVOC -> team.havoc();
            case ATS -> null;
        };
    }

    private String formatMetric(Double val) {
        return formatMetricByType(val, activeMetric);
    }

    private String formatMetricByType(Double val, MetricType type) {
        if (val == null) return "—";
        return switch (type) {
            case EPA -> String.format("%+.3f", val);
            case SUCCESS, HAVOC -> String.format("%.1f%%", val * 100.0);
            case ATS -> "—";
        };
    }

    private String getMetricColor(Double val) {
        if (val == null) return "#94a3b8";
        if (activeMetric == MetricType.EPA) {
            return val >= 0 ? "#34d399" : "#f87171";
        }
        return "#38bdf8";
    }
}