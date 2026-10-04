package com.pickem.app.ui;

import com.pickem.app.model.GroupParlayPick;
import com.pickem.app.model.Player;
import com.pickem.app.repository.PlayerRepository;
import com.pickem.app.service.ConferenceService;
import com.pickem.app.service.GameSyncService;
import com.pickem.app.service.OddsService;
import com.pickem.app.service.ParlayService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GroupParlayDialog extends Dialog {

    private final ParlayService parlayService;
    private final PlayerRepository playerRepo;
    private final OddsService oddsService;
    private final ConferenceService conferenceService;
    private final GameSyncService gameSyncService;
    private final Runnable onParlayUpdated;

    private final VerticalLayout slotsContainer = new VerticalLayout();
    private final String loggedInEmail;
    private final boolean isAdmin;

    public GroupParlayDialog(int weekNumber, ParlayService parlayService, PlayerRepository playerRepo,
                             OddsService oddsService, ConferenceService conferenceService,
                             GameSyncService gameSyncService, Runnable onParlayUpdated) {
        this.parlayService = parlayService;
        this.playerRepo = playerRepo;
        this.oddsService = oddsService;
        this.conferenceService = conferenceService;
        this.gameSyncService = gameSyncService;
        this.onParlayUpdated = onParlayUpdated;

        // Capture Auth Context for permissions
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User oauth2User) {
            this.loggedInEmail = oauth2User.getAttribute("email");
            String loggedInName = oauth2User.getAttribute("name");
            this.isAdmin = (loggedInName != null && loggedInName.toLowerCase().contains("azich"));
        } else {
            this.loggedInEmail = null;
            this.isAdmin = false;
        }

        getElement().setAttribute("theme", "dark");
        setWidth("100%");
        setMaxWidth("520px");

        setHeaderTitle("Weekly 5-Leg Parlay");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // --- PARLAY DUTY BANNER ---
        String dutyNames = parlayService.getParlayDutyNames(weekNumber);
        Div banner = new Div();
        banner.setWidthFull();
        banner.getStyle()
                .set("background-color", "#1e293b")
                .set("border", "1px solid #334155")
                .set("border-radius", "8px")
                .set("padding", "10px 14px")
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "center")
                .set("box-sizing", "border-box");

        Span dutyLabel = new Span("🚨 Parlay Duty:");
        dutyLabel.getStyle().set("color", "#f87171").set("font-weight", "700");

        Span dutyNameSpan = new Span(dutyNames);
        dutyNameSpan.getStyle().set("color", "#f8fafc").set("font-weight", "600");

        banner.add(dutyLabel, dutyNameSpan);
        content.add(banner);

        // Slots
        slotsContainer.setPadding(false);
        slotsContainer.setSpacing(true);
        slotsContainer.setWidthFull();
        content.add(slotsContainer);

        renderSlots(weekNumber);

        add(content);

        Button closeBtn = new Button("Close", e -> close());
        closeBtn.getStyle().set("color", "#94a3b8").set("background", "transparent");
        getFooter().add(closeBtn);
    }

    private void renderSlots(int weekNumber) {
        slotsContainer.removeAll();

        List<Player> allPlayers = playerRepo.findAll();
        List<GroupParlayPick> currentPicks = parlayService.getPicksForWeek(weekNumber);
        Map<Long, GroupParlayPick> pickByPlayerId = currentPicks.stream()
                .collect(Collectors.toMap(p -> p.getPlayer().getId(), Function.identity()));

        for (Player player : allPlayers) {
            GroupParlayPick pick = pickByPlayerId.get(player.getId());

            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.getStyle()
                    .set("background-color", "#151d30")
                    .set("border", "1px solid #22304d")
                    .set("border-radius", "8px")
                    .set("padding", "8px 12px")
                    .set("min-height", "46px");

            // Player Name
            Span playerName = new Span(player.getName());
            playerName.setWidth("90px");
            playerName.getStyle()
                    .set("font-weight", "600")
                    .set("color", "#e2e8f0")
                    .set("flex-shrink", "0");

            boolean canEdit = this.isAdmin || (this.loggedInEmail != null && this.loggedInEmail.equalsIgnoreCase(player.getEmail()));

            if (pick != null) {
                // RENDER SELECTED PICK
                HorizontalLayout pickDetails = new HorizontalLayout();
                pickDetails.setAlignItems(FlexComponent.Alignment.CENTER);
                pickDetails.setSpacing(true);

                if (pick.getLogoUrl() != null && !pick.getLogoUrl().isBlank()) {
                    Image logo = new Image(pick.getLogoUrl(), "Logo");
                    logo.setWidth("20px");
                    logo.setHeight("20px");
                    logo.getStyle().set("flex-shrink", "0");
                    pickDetails.add(logo);
                }

                Span selectionText = new Span(pick.getSelection());
                selectionText.getStyle()
                        .set("font-weight", "600")
                        .set("color", "#38bdf8")
                        .set("font-size", "0.9rem");

                Span sportBadge = new Span(pick.getSport());
                sportBadge.getStyle()
                        .set("background", "#334155")
                        .set("color", "#94a3b8")
                        .set("border-radius", "4px")
                        .set("padding", "2px 6px")
                        .set("font-size", "0.7rem")
                        .set("font-weight", "700");

                pickDetails.add(selectionText, sportBadge);

                Button changeBtn = new Button("Edit", e -> openSelectionModal(player, weekNumber));
                changeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
                changeBtn.setEnabled(canEdit);
                changeBtn.getStyle().set("margin-left", "auto");

                row.add(playerName, pickDetails, changeBtn);
                row.expand(pickDetails);
            } else {
                // PENDING SLOT
                Span pending = new Span("Pick pending...");
                pending.getStyle().set("color", "#64748b").set("font-style", "italic").set("font-size", "0.85rem");

                Button pickBtn = new Button("Select Leg", e -> openSelectionModal(player, weekNumber));
                pickBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                pickBtn.setEnabled(canEdit);
                pickBtn.getStyle().set("margin-left", "auto");

                row.add(playerName, pending, pickBtn);
                row.expand(pending);
            }

            slotsContainer.add(row);
        }
    }

    private void openSelectionModal(Player player, int weekNumber) {
        ParlayPickSelectionDialog selectionDialog = new ParlayPickSelectionDialog(
                player, weekNumber, parlayService, oddsService, conferenceService, gameSyncService, () -> {
            renderSlots(weekNumber);
            if (onParlayUpdated != null) {
                onParlayUpdated.run();
            }
        });
        selectionDialog.open();
    }
}