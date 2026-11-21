package com.pickem.app.ui;

import com.pickem.app.model.League;
import com.pickem.app.repository.LeagueRepository;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.theme.lumo.Lumo;
import java.security.SecureRandom;

public class LeagueSettingsDialog extends Dialog {

    public LeagueSettingsDialog(League league, LeagueRepository leagueRepo) {
        setHeaderTitle("⚙️ League Settings");
        getElement().setAttribute("theme", Lumo.DARK);
        setWidth("400px");
        setMaxWidth("92vw");

        // Auto-generate and save an invite code for older leagues that don't have one yet
        String inviteCode = league.getInviteCode() != null ? league.getInviteCode().trim() : "";
        if (inviteCode.isEmpty()) {
            inviteCode = generateUniqueInviteCode(leagueRepo);
            league.setInviteCode(inviteCode);
            leagueRepo.save(league);
        }
        final String finalInviteCode = inviteCode;
        String leagueName = league.getName() != null ? league.getName() : "my Pick'Em league";

        // 1. Invite Players Section (Text + Share/Copy Link)
        Span inviteLabel = new Span("Invite Friends");
        inviteLabel.getStyle()
                .set("color", "#cbd5e1")
                .set("font-size", "0.875rem")
                .set("font-weight", "600");

        Button textInviteBtn = new Button("💬 Text Invite", e -> {
            UI.getCurrent().getPage().executeJs(
                    "const url = window.location.origin + '/join/' + $0;" +
                            "const msg = 'Join my Pick\\'Em league \"' + $1 + '\": ' + url;" +
                            "const ua = navigator.userAgent || '';" +
                            "const isIOS = /iPhone|iPad|iPod/i.test(ua);" +
                            "const isAndroid = /Android/i.test(ua);" +
                            "if (isIOS || isAndroid) {" +
                            "  const sep = isIOS ? '&' : '?';" +
                            "  window.location.href = 'sms:' + sep + 'body=' + encodeURIComponent(msg);" +
                            "  return 'sms';" +
                            "} else {" +
                            "  if (navigator.clipboard) { navigator.clipboard.writeText(msg); }" +
                            "  return 'copied';" +
                            "}",
                    finalInviteCode, leagueName
            ).then(String.class, result -> {
                if ("copied".equals(result)) {
                    Notification toast = Notification.show(
                            "Invite text copied! Paste it into Messages or your group chat.",
                            3500, Notification.Position.TOP_CENTER
                    );
                    toast.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                }
            });
        });
        textInviteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        textInviteBtn.setEnabled(!inviteCode.isEmpty());
        textInviteBtn.getStyle()
                .set("background-color", "#10b981")
                .set("color", "#0b0f19")
                .set("font-weight", "600")
                .set("flex-grow", "1");

        Button copyLinkBtn = new Button("🔗 Share / Copy", e -> {
            UI.getCurrent().getPage().executeJs(
                    "const url = window.location.origin + '/join/' + $0;" +
                            "const text = 'Join my Pick\\'Em league \"' + $1 + '\"';" +
                            "if (navigator.share) {" +
                            "  return navigator.share({ title: 'Pick\\'Em Invite', text: text, url: url })" +
                            "    .then(() => 'shared').catch(() => 'cancelled');" +
                            "} else if (navigator.clipboard) {" +
                            "  return navigator.clipboard.writeText(url).then(() => 'copied');" +
                            "}" +
                            "return 'unsupported';",
                    finalInviteCode, leagueName
            ).then(String.class, result -> {
                if ("copied".equals(result)) {
                    Notification toast = Notification.show(
                            "Invite link copied to clipboard!",
                            3000, Notification.Position.TOP_CENTER
                    );
                    toast.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                }
            });
        });
        copyLinkBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        copyLinkBtn.setEnabled(!inviteCode.isEmpty());
        copyLinkBtn.getStyle().set("flex-grow", "1");

        HorizontalLayout inviteActions = new HorizontalLayout(textInviteBtn, copyLinkBtn);
        inviteActions.setWidthFull();
        inviteActions.setSpacing(true);

        Span codeHint = new Span("Backup code: " + (inviteCode.isEmpty() ? "N/A" : inviteCode));
        codeHint.getStyle()
                .set("color", "#64748b")
                .set("font-size", "0.78rem")
                .set("margin-top", "-6px");

        // 2. Weekly Pick Limit
        IntegerField pickLimitField = new IntegerField("Weekly Pick Limit");
        pickLimitField.setMin(1);
        pickLimitField.setMax(10);
        pickLimitField.setStepButtonsVisible(true);
        pickLimitField.setValue(league.getPicksPerWeek() != null ? league.getPicksPerWeek() : 5);
        pickLimitField.setWidthFull();

        // 3. Group Parlay Toggle
        Checkbox parlayCheckbox = new Checkbox("Enable Group Parlays");
        parlayCheckbox.setValue(league.getEnableGroupParlay() != null ? league.getEnableGroupParlay() : false);

        // 4. Save Button
        Button saveBtn = new Button("Save Settings", e -> {
            league.setPicksPerWeek(pickLimitField.getValue());
            league.setEnableGroupParlay(parlayCheckbox.getValue());

            leagueRepo.save(league);

            Notification.show("League settings updated successfully!", 3000, Notification.Position.TOP_CENTER);
            close();
            UI.getCurrent().getPage().reload();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> close());

        VerticalLayout layout = new VerticalLayout(inviteLabel, inviteActions, codeHint, pickLimitField, parlayCheckbox);
        layout.setPadding(false);
        layout.setSpacing(true);
        add(layout);

        getFooter().add(cancelBtn, saveBtn);
    }
    private static String generateUniqueInviteCode(LeagueRepository leagueRepo) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Excludes confusing 0/O and 1/I
        SecureRandom random = new SecureRandom();
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (leagueRepo.findByInviteCode(code).isPresent());
        return code;
    }
}