package com.pickem.app.config;

import com.pickem.app.model.Player;
import com.pickem.app.repository.PlayerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initPlayers(PlayerRepository repo) {
        return args -> {
            // Only seed the database if the players table is empty
            if (repo.count() == 0) {
                Player jake = new Player(); jake.setName("Jake");
                Player brad = new Player(); brad.setName("Brad");
                Player ben = new Player(); ben.setName("Ben");
                Player tmerr = new Player(); tmerr.setName("Tmerr");
                Player brett = new Player(); brett.setName("Brett");

                repo.saveAll(List.of(jake, brad, ben, tmerr, brett));
                System.out.println(">>> Players successfully seeded into Supabase!");
            }
        };
    }
}