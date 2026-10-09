package com.tuneflow.music.service;

import com.tuneflow.music.dto.CreateGenreRequest;
import com.tuneflow.music.dto.GenreResponse;

import java.util.List;
import java.util.UUID;

public interface GenreService {

    List<GenreResponse> listGenres();

    GenreResponse createGenre(CreateGenreRequest request);

    GenreResponse updateGenre(UUID id, CreateGenreRequest request);

    void deleteGenre(UUID id);
}
