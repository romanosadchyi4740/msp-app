package com.romanosadchyi.smp.music_app.tabs.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * A Guitar Pro file kept in object storage; the browser parses it.
 */
@Entity
@DiscriminatorValue("GUITAR_PRO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GuitarProTab extends Tab {

    @Column(name = "file_key")
    String fileKey;

    /**
     * Such as {@code GP5} or {@code GPX}.
     */
    @Column(name = "gp_version")
    String gpVersion;
}
