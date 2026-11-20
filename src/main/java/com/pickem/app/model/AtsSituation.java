package com.pickem.app.model;

import java.util.Arrays;

public enum AtsSituation {
    ALL_GAMES("All Games"),
    HOME("As Home Team"),
    AWAY("As Away Team"),
    FAVORITE("As Favorite"),
    UNDERDOG("As Underdog"),
    HOME_FAV("As Home Favorite"),
    HOME_DOG("As Home Underdog"),
    AWAY_FAV("As Away Favorite"),
    AWAY_DOG("As Away Underdog"),
    AFTER_WIN("After A Win"),
    AFTER_LOSS("After A Loss"),
    AFTER_BYE("After A Bye"),
    REST_ADV("With Rest Advantage"),
    DIVISION("Division / Conference Games");

    private final String label;

    AtsSituation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AtsSituation fromKey(String key) {
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(key))
                .findFirst()
                .orElse(ALL_GAMES);
    }
}