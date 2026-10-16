package com.romanosadchyi.smp.music_app.music.internal.config;

import com.romanosadchyi.smp.music_app.music.internal.entity.Genre;
import com.romanosadchyi.smp.music_app.music.internal.repository.GenreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferenceDataSeederTest {

    @Mock
    GenreRepository genreRepository;

    @InjectMocks
    ReferenceDataSeeder referenceDataSeeder;

    @Test
    void run_NoGenres_SeedsGenres() {
        when(genreRepository.count()).thenReturn(0L);

        referenceDataSeeder.run();

        verify(genreRepository, times(25)).save(any(Genre.class));
    }

    @Test
    void run_GenresExist_DoesNothing() {
        when(genreRepository.count()).thenReturn(3L);

        referenceDataSeeder.run();

        verify(genreRepository, never()).save(any());
    }
}
