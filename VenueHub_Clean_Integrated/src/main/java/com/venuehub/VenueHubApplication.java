package com.venuehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class VenueHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(VenueHubApplication.class, args);
    }

    @Bean
    CommandLineRunner ensureVenueMapColumn(JdbcTemplate jdbc) {
        return args -> {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='venues' AND column_name='map_url'", Integer.class);
            if (count == null || count == 0) jdbc.execute("ALTER TABLE venues ADD COLUMN map_url TEXT NULL");
        };
    }
}
