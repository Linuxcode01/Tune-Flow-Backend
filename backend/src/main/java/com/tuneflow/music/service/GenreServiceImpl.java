package com.tuneflow.music.service;

import com.tuneflow.common.config.CacheConfig;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.util.HashUtils;
import com.tuneflow.music.dto.CreateGenreRequest;
import com.tuneflow.music.dto.GenreResponse;
import com.tuneflow.music.entity.Genre;
import com.tuneflow.music.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/** Genre reference data. Genres change rarely, so the whole list is cached. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenreServiceImpl implements GenreService {

    private final GenreRepository genreRepository;
    private final SongMapper songMapper;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_GENRES, key = "'all'")
    public List<GenreResponse> listGenres() {
        return genreRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(songMapper::toGenreResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_GENRES, allEntries = true)
    public GenreResponse createGenre(CreateGenreRequest request) {
        String slug = StringUtils.hasText(request.slug())
                ? HashUtils.slugify(request.slug())
                : HashUtils.slugify(request.name());
        if (slug.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Genre name must contain alphanumeric characters");
        }
        if (genreRepository.existsBySlug(slug)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Genre '" + slug + "' already exists");
        }
        Genre genre = genreRepository.save(Genre.builder()
                .name(request.name().trim())
                .slug(slug)
                .description(request.description())
                .coverImageUrl(request.coverImageObjectKey())
                .build());
        return songMapper.toGenreResponse(genre);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_GENRES, allEntries = true)
    public GenreResponse updateGenre(UUID id, CreateGenreRequest request) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.GENRE_NOT_FOUND));
        genre.setName(request.name().trim());
        if (StringUtils.hasText(request.slug())) {
            String slug = HashUtils.slugify(request.slug());
            if (!slug.equals(genre.getSlug()) && genreRepository.existsBySlug(slug)) {
                throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Genre '" + slug + "' already exists");
            }
            genre.setSlug(slug);
        }
        genre.setDescription(request.description());
        if (request.coverImageObjectKey() != null) {
            genre.setCoverImageUrl(request.coverImageObjectKey());
        }
        return songMapper.toGenreResponse(genreRepository.save(genre));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_GENRES, allEntries = true)
    public void deleteGenre(UUID id) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.GENRE_NOT_FOUND));
        genreRepository.delete(genre);
        log.info("Deleted genre {}", id);
    }
}
