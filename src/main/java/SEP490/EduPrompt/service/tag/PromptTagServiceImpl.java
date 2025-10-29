package SEP490.EduPrompt.service.tag;

import SEP490.EduPrompt.dto.request.tag.AddTagsToPromptRequest;
import SEP490.EduPrompt.dto.request.tag.RemoveTagFromPromptRequest;
import SEP490.EduPrompt.dto.response.tag.BatchAddResultResponse;
import SEP490.EduPrompt.dto.response.tag.TagRelationResponse;
import SEP490.EduPrompt.dto.response.tag.TagResponse;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptTag;
import SEP490.EduPrompt.model.PromptTagId;
import SEP490.EduPrompt.model.Tag;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.PromptTagRepository;
import SEP490.EduPrompt.repo.TagRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromptTagServiceImpl implements PromptTagService {

    private final PromptTagRepository promptTagRepository;
    private final PromptRepository promptRepository;
    private final TagRepository tagRepository;
    private final PermissionService permissionService;

    @Override
    @Transactional
    public BatchAddResultResponse addTags(UUID promptId, AddTagsToPromptRequest request, UserPrincipal user) {
        log.info("Adding {} tags to prompt {} by user {}", request.tagIds().size(), promptId, user.getUserId());

        Prompt prompt = promptRepository.findActiveById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found: " + promptId));

        permissionService.canEditPrompt(user, prompt);

        List<UUID> tagIds = request.tagIds();
        List<Tag> tags = tagRepository.findAllByIdIn(tagIds);
        if (tags.size() != tagIds.size()) {
            Set<UUID> found = tags.stream().map(Tag::getId).collect(Collectors.toSet());
            List<UUID> missing = tagIds.stream().filter(id -> !found.contains(id)).toList();
            throw new ResourceNotFoundException("Tags not found: " + missing);
        }

        List<PromptTag> existing = promptTagRepository.findExisting(promptId, tagIds);
        Set<UUID> attached = existing.stream().map(pt -> pt.getTag().getId()).collect(Collectors.toSet());

        List<PromptTag> toSave = tags.stream()
                .filter(t -> !attached.contains(t.getId()))
                .map(t -> PromptTag.builder()
                        .id(new PromptTagId(promptId, t.getId()))
                        .prompt(prompt)
                        .tag(t)
                        .build())
                .toList();

        if (toSave.isEmpty()) {
            return new BatchAddResultResponse(promptId, List.of());
        }

        List<PromptTag> saved = promptTagRepository.saveAll(toSave);
        List<TagRelationResponse> added = saved.stream()
                .map(pt -> new TagRelationResponse(pt.getTag().getId(), pt.getCreatedAt()))
                .toList();

        log.info("Added {} new tags to prompt {}", added.size(), promptId);
        return new BatchAddResultResponse(promptId, added);
    }

    @Override
    @Transactional
    public void removeTag(UUID promptId, RemoveTagFromPromptRequest request, UserPrincipal user) {
        UUID tagId = request.tagId();
        log.info("Removing tag {} from prompt {} by user {}", tagId, promptId, user.getUserId());

        Prompt prompt = promptRepository.findActiveById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found: " + promptId));

        permissionService.canEditPrompt(user, prompt);

        PromptTagId id = new PromptTagId(promptId, tagId);
        if (!promptTagRepository.existsById(id)) {
            throw new ResourceNotFoundException("Tag not attached to prompt");
        }

        promptTagRepository.deleteById(id);
        log.info("Tag {} removed from prompt {}", tagId, promptId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> getTagsForPrompt(UUID promptId, UserPrincipal user) {
        log.info("Fetching tags for prompt {} by user {}", promptId, user.getUserId());

        Prompt prompt = promptRepository.findActiveById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found: " + promptId));

        permissionService.canAccessPrompt(prompt, user);

        return promptTagRepository.findByPrompt_Id(promptId).stream()
                .map(pt -> new TagResponse(pt.getTag().getId(), pt.getTag().getType(), pt.getTag().getValue()))
                .toList();
    }
}
