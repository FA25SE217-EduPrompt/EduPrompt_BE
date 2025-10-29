package SEP490.EduPrompt.service.tag;

import SEP490.EduPrompt.dto.request.tag.AddTagsToCollectionRequest;
import SEP490.EduPrompt.dto.request.tag.RemoveTagFromCollectionRequest;
import SEP490.EduPrompt.dto.response.tag.BatchAddResultResponse;
import SEP490.EduPrompt.dto.response.tag.TagRelationResponse;
import SEP490.EduPrompt.dto.response.tag.TagResponse;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.CollectionRepository;
import SEP490.EduPrompt.repo.CollectionTagRepository;
import SEP490.EduPrompt.repo.TagRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionTagServiecImpl implements CollectionTagService {
    private final CollectionTagRepository collectionTagRepository;
    private final CollectionRepository collectionRepository;
    private final TagRepository tagRepository;
    private final PermissionService permissionService;

    @Override
    @Transactional
    public BatchAddResultResponse addTags(UUID collectionId, AddTagsToCollectionRequest request, UserPrincipal user) {
        log.info("Adding {} tags to prompt {} by user {}", request.tagIds().size(), collectionId, user.getUserId());

        Collection collection = collectionRepository.findActiveById(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found: " + collectionId));

        permissionService.canEditCollection(user, collection);

        List<UUID> tagIds = request.tagIds();
        List<Tag> tags = tagRepository.findAllByIdIn(tagIds);
        if (tags.size() != tagIds.size()) {
            Set<UUID> found = tags.stream().map(Tag::getId).collect(Collectors.toSet());
            List<UUID> missing = tagIds.stream().filter(id -> !found.contains(id)).toList();
            throw new ResourceNotFoundException("Tags not found: " + missing);
        }

        List<CollectionTag> existing = collectionTagRepository.findExisting(collectionId, tagIds);
        Set<UUID> attached = existing.stream().map(pt -> pt.getTag().getId()).collect(Collectors.toSet());

        List<CollectionTag> toSave = tags.stream()
                .filter(t -> !attached.contains(t.getId()))
                .map(t -> CollectionTag.builder()
                        .id(new CollectionTagId(collectionId, t.getId()))
                        .collection(collection)
                        .tag(t)
                        .build())
                .toList();

        if (toSave.isEmpty()) {
            return new BatchAddResultResponse(collectionId, List.of());
        }

        List<CollectionTag> saved = collectionTagRepository.saveAll(toSave);
        List<TagRelationResponse> added = saved.stream()
                .map(pt -> new TagRelationResponse(pt.getTag().getId(), pt.getCreatedAt()))
                .toList();

        log.info("Added {} new tags to prompt {}", added.size(), collectionId);
        return new BatchAddResultResponse(collectionId, added);
    }

    @Override
    @Transactional
    public void removeTag(UUID collectionId, RemoveTagFromCollectionRequest request, UserPrincipal user) {
        UUID tagId = request.tagId();
        log.info("Removing tag {} from collection {} by user {}", tagId, collectionId, user.getUserId());

        Collection collection = collectionRepository.findActiveById(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

        permissionService.canEditCollection(user, collection);

        CollectionTagId id = new CollectionTagId(collectionId, tagId);
        if (!collectionTagRepository.existsById(id)) {
            throw new ResourceNotFoundException("Tag not attached to collection");
        }

        collectionTagRepository.deleteById(id);
        log.info("Tag {} removed from collection {}", tagId, collectionId);
    }

    @Override
    @Transactional
    public List<TagResponse> getTagsForCollection(UUID collectionId, UserPrincipal user) {
        log.info("Fetching tags for collection {} by user {}", collectionId, user.getUserId());

        permissionService.canAccessCollection(user, collectionId);

        return collectionTagRepository.findByCollection_Id(collectionId).stream()
                .map(ct -> new TagResponse(ct.getTag().getId(), ct.getTag().getType(), ct.getTag().getValue()))
                .toList();
    }
}
