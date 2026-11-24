package com.romanosadchyi.smp.music_app.tabs.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * A plain-text tab with one line per string, stored in the database.
 */
@Entity
@DiscriminatorValue("ASCII")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AsciiTab extends Tab {

    @Column(name = "raw_text", columnDefinition = "TEXT")
    String rawText;
}
