package com.pickem.app.ui;

import com.pickem.app.model.League;
import com.pickem.app.model.LeagueMember;
import com.pickem.app.model.Player;
import com.pickem.app.repository.LeagueMemberRepository;
import com.pickem.app.repository.LeagueRepository;
import com.pickem.app.repository.PlayerRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinSession;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public class CreateLeagueDialog extends Dialog {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public CreateLeagueDialog(LeagueRepository leagueRepository,
                              LeagueMemberRepository leagueMemberRepository,
                              PlayerRepository playerRepository,
                              Long currentPlayerId,
                              Runnable onLeagueCreated) {

        setHeaderTitle("Create New League");
        setCloseOnOutsideClick(true);
        setCloseOnEsc(true);

        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(false);
        layout.setWidth("350px");

        // 1. League Name Input
        TextField leagueNameField = new TextField("League Name");
        leagueNameField.setPlaceholder("e.g. Office Showdown 2026");
        leagueNameField.setWidthFull();
        leagueNameField.setRequired(true);

        // 2. Auto-generated Join Code (editable just in case they want a custom one)
        TextField joinCodeField = new TextField("Join Code");
        joinCodeField.setValue(generateRandomCode(6));
        joinCodeField.setWidthFull();
        joinCodeField.setHelperText("Share this code with friends so they can join.");

        // 3. Admin Setting: Enable/Disable Group Parlay Feature
        Checkbox parlayToggle = new Checkbox("Enable Group Parlay Feature");
        parlayToggle.setValue(true);
        parlayToggle.setHelperText("Shows the blue weekly parlay tracking button on the board.");

        // 4. Admin Setting: Picks Per Week Selector
        Select<Integer> picksPerWeekSelect = new Select<>();
        picksPerWeekSelect.setLabel("Picks Per Week");

        // Generate a list of options from 1 to 15 (or whatever maximum you prefer)
        List<Integer> pickOptions = java.util.stream.IntStream.rangeClosed(1, 15).boxed().toList();
        picksPerWeekSelect.setItems(pickOptions);

        // Custom item renderer to add the "Premium" label for numbers > 10
        picksPerWeekSelect.setItemLabelGenerator(item -> {
            if (item > 10) {
                return item + " Picks (Premium)";
            }
            return item + " Picks";
        });

        picksPerWeekSelect.setValue(5); // Default value
        picksPerWeekSelect.setWidthFull();

        layout.add(leagueNameField, joinCodeField, parlayToggle, picksPerWeekSelect);

        // Buttons
        // Buttons
        Button saveBtn = new Button("Create League", event -> {
            String name = leagueNameField.getValue();
            String code = joinCodeField.getValue().trim().toUpperCase();

            if (name == null || name.isEmpty() || code.isEmpty()) {
                leagueNameField.setInvalid(true);
                return;
            }

            // Save new League to DB
            League newLeague = new League();
            newLeague.setName(name);
            newLeague.setInviteCode(code);
            newLeague.setOwnerId(currentPlayerId);
            newLeague.setAdminId(currentPlayerId); // Ensures you are marked as the admin
            newLeague.setEnableGroupParlay(parlayToggle.getValue());
            newLeague.setPicksPerWeek(picksPerWeekSelect.getValue());
            newLeague.setCreatedAt(Instant.now()); // CORRECTED: Sets the League creation time

            League savedLeague = leagueRepository.save(newLeague);

            // Automatically link the creator as a member of this new league
            Player creator = playerRepository.findById(currentPlayerId).orElse(null);
            if (creator != null) {
                LeagueMember member = new LeagueMember();
                member.setLeague(savedLeague);
                member.setPlayer(creator);
                member.setJoinedAt(Instant.now()); // CORRECTED: Sets the Member join time
                leagueMemberRepository.save(member);
            }

            // Update session and trigger UI refresh
            VaadinSession.getCurrent().setAttribute("league_id", savedLeague.getId());
            VaadinSession.getCurrent().setAttribute("league_name", savedLeague.getName());

            close();
            if (onLeagueCreated != null) {
                onLeagueCreated.run();
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout buttons = new HorizontalLayout(cancelBtn, saveBtn);
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttons.setWidthFull();

        getFooter().add(buttons);
        add(layout);
    }

    private String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}