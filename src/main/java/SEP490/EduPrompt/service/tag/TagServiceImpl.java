package SEP490.EduPrompt.service.tag;

import SEP490.EduPrompt.dto.request.tag.CreateTagBatchRequest;
import SEP490.EduPrompt.dto.response.tag.TagResponse;
import SEP490.EduPrompt.model.Tag;
import SEP490.EduPrompt.repo.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagServiceImpl implements TagService {
    private final TagRepository tagRepository;

    @Override
    @Transactional
    public List<TagResponse> createBatch(CreateTagBatchRequest request) {
        log.info("Creating {} tags (deduplicating by lowercase type+value)", request.tags().size());

        if (request.tags().isEmpty()) {
            return List.of();
        }

        // Step 1: Normalize to lowercase only (no trim)
        Map<String, CreateTagBatchRequest.CreateTagRequest> unique = new LinkedHashMap<>();
        for (var r : request.tags()) {
            String key = r.type().toLowerCase() + "::" + r.value().toLowerCase();
            unique.putIfAbsent(key, r); // keep first occurrence
        }

        // Step 2: Load all existing tags
        List<Tag> existing = tagRepository.findAll();

        // Step 3: Build map of existing: lowercase(type + value) -> Tag
        Map<String, Tag> existingMap = existing.stream()
                .collect(Collectors.toMap(
                        t -> t.getType().toLowerCase() + "::" + t.getValue().toLowerCase(),
                        t -> t,
                        (a, b) -> a
                ));

        // Step 4: Filter out duplicates
        List<Tag> toSave = unique.entrySet().stream()
                .filter(e -> !existingMap.containsKey(e.getKey()))
                .map(e -> {
                    var r = e.getValue();
                    return Tag.builder()
                            .type(r.type())   // preserve original casing
                            .value(r.value())
                            .build();
                })
                .toList();

        // Step 5: Save new tags
        List<Tag> saved = toSave.isEmpty() ? List.of() : tagRepository.saveAll(toSave);

        // Step 6: Build response
        List<TagResponse> result = unique.values().stream()
                .map(r -> {
                    String key = r.type().toLowerCase() + "::" + r.value().toLowerCase();
                    Tag tag = existingMap.getOrDefault(key,
                            saved.stream()
                                    .filter(s -> (s.getType().toLowerCase() + "::" + s.getValue().toLowerCase()).equals(key))
                                    .findFirst()
                                    .orElse(null));
                    return tag != null ? new TagResponse(tag.getId(), tag.getType(), tag.getValue()) : null;
                })
                .toList();

        log.info("Created {} new tags, {} skipped (already exist)", saved.size(), unique.size() - saved.size());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TagResponse> filterTags(List<String> types, Pageable pageable) {
        log.info("Filtering tags with types: {}", types);

        Page<Tag> page;

        if (types == null || types.isEmpty() || types.stream().allMatch(String::isBlank)) {
            // No filter → return all
            page = tagRepository.findAll(pageable);
        } else {
            // Convert to lowercase for case-insensitive match
            List<String> lowerTypes = types.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::toLowerCase)
                    .toList();

            if (lowerTypes.isEmpty()) {
                page = Page.empty(pageable);
            } else {
                page = tagRepository.findByTypeInIgnoreCase(lowerTypes, pageable);
            }
        }

        return page.map(t -> new TagResponse(t.getId(), t.getType(), t.getValue()));
    }

    @Override
    @Transactional
    public List<Tag> findAllByIdIn(List<java.util.UUID> ids) {
        return tagRepository.findAllByIdIn(ids);
    }

    // Optional: preserve original capitalization style (e.g. "Math" → "Math", not "math")
    private String normalizeCase(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }
}
