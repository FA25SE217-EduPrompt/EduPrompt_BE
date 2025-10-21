package SEP490.EduPrompt.service.collection;

import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.collection.UpdateCollectionRequest;
import SEP490.EduPrompt.dto.response.collection.CollectionResponse;
import SEP490.EduPrompt.dto.response.collection.CreateCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.PageCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.UpdateCollectionResponse;
import SEP490.EduPrompt.dto.response.prompt.TagDTO;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static SEP490.EduPrompt.enums.Visibility.parseVisibility;


/**
 * Service implementation for managing prompt collections.
 * <p>
 * Responsibilities:
 * - Create, update, retrieve, and soft-delete collections.
 * - Enforce visibility and access control rules.
 * - Handle collection ownership and membership verification.
 * - Support pagination for personal and public collections.
 * <p>
 * Access Rules Summary:
 * - Admins (SYSTEM_ADMIN, SCHOOL_ADMIN):
 * → can update/delete any collection.
 * → SYSTEM_ADMIN only can access deleted items.
 * - Regular users (TEACHER):
 * → can manage only their own collections.
 * → must be group member to create/view group-visibility collections.
 * - Visibility:
 * → PRIVATE: owner only.
 * → PUBLIC: everyone.
 * → SCHOOL: teachers from same school.
 * → GROUP: active group members (role=admin/member).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionServiceImpl implements CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionTagRepository collectionTagRepository;
    private final PermissionService permissionService;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final TagRepository tagRepository;


    private boolean isAdmin(UserPrincipal user) {
        String role = user.getRole();
        return isSystemAdmin(role) || isSchoolAdmin(role);
    }

    private boolean isSystemAdmin(String userRole) {
        return Role.SYSTEM_ADMIN.name().equalsIgnoreCase(userRole);
    }

    private boolean isSchoolAdmin(String userRole) {
        return Role.SCHOOL_ADMIN.name().equalsIgnoreCase(userRole);
    }

    private void validateAccess(Collection collection, UserPrincipal currentUser) {
        String currentRole = currentUser.getRole();
        UUID currentUserId = currentUser.getUserId();

        // deleted means only system admin can access
        if (collection.getIsDeleted()) {
            if (!isSystemAdmin(currentRole)) {
                throw new AccessDeniedException("You do not have permission to access this collection");
            }
            return;
        }

        // admin bypass
        if (isAdmin(currentUser)) return;


        Visibility vis = parseVisibility(collection.getVisibility());

        switch (vis) {
            case PRIVATE -> {
                if (!collection.getCreatedBy().equals(currentUserId)) {
                    throw new AccessDeniedException("Only owner can access private collection");
                }
            }
            case PUBLIC -> {
                // anybody
            }
            case SCHOOL -> {
                UUID creatorId = collection.getCreatedBy();
                UUID creatorSchoolId = userRepository.findById(creatorId).map(User::getSchoolId).orElse(null);
                UUID currentSchoolId = currentUser.getSchoolId();
                if (creatorSchoolId == null || !creatorSchoolId.equals(currentSchoolId)) {
                    throw new AccessDeniedException("Only teachers from the same school can access this collection");
                }
            }
            case GROUP -> {
                if (collection.getGroup() == null)
                    throw new AccessDeniedException("Group not found for this collection");
                boolean member = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                        collection.getGroup().getId(),
                        currentUserId,
                        "active");
                if (!member) throw new AccessDeniedException("You are not a member of this group");
            }
            default -> throw new AccessDeniedException("Unsupported visibility");
        }
    }

    @Override
    @Transactional
    public CreateCollectionResponse createCollection(CreateCollectionRequest request, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Visibility vis = parseVisibility(request.visibility());

        Group group = null;
        if (vis == Visibility.GROUP) {
            if (request.groupId() == null) {
                throw new InvalidInputException("Group ID required for group visibility");
            }
            group = groupRepository.findById(request.groupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
            // Verify membership for creation
            boolean isMember = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                    group.getId(), currentUserId, "active");
            if (!isMember) {
                throw new AccessDeniedException("You must be a group member to create a group-visible collection");
            }
        }
        List<Tag> tags = new ArrayList<>();
        if (request.tags() != null && !request.tags().isEmpty()) {
            tags = tagRepository.findAllById(request.tags());
            if (tags.size() != request.tags().size()) {
                throw new ResourceNotFoundException("One or more tags not found");
            }
        }

        Collection collection = Collection.builder()
                .user(user)
                .name(request.name())
                .description(request.description())
                .visibility(request.visibility().toUpperCase())
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .isDeleted(false)
                .group(group)
                .build();
        Collection saved = collectionRepository.save(collection);

        // Create PromptTag entries
        if (!tags.isEmpty()) {
            List<CollectionTag> collectionTags = tags.stream()
                    .map(tag -> CollectionTag.builder()
                            .id(CollectionTagId.builder()
                                    .collectionId(saved.getId())
                                    .tagId(tag.getId())
                                    .build())
                            .collection(saved)
                            .tag(tag)
                            .createdAt(Instant.now())
                            .build())
                    .collect(Collectors.toList());
            collectionTagRepository.saveAll(collectionTags);
        }
        log.info("Collection created: {} by user: {}", saved.getId(), currentUserId);

        return CreateCollectionResponse.builder()
                .name(collection.getName())
                .description(collection.getDescription())
                .visibility(collection.getVisibility().toUpperCase())
                .tags(tags.stream()
                        .map(tag -> TagDTO.builder()
                                .type(tag.getType())
                                .value(tag.getValue())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(collection.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public UpdateCollectionResponse updateCollection(UUID id, UpdateCollectionRequest request, UserPrincipal currentUser) {
        log.info("Updating collection: {} by user: {}", id, currentUser.getUserId());

        // Fetch collection
        Collection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found with ID: " + id));

        // Prevent updates to deleted collections unless SYSTEM_ADMIN
        if (collection.getIsDeleted() && !isSystemAdmin(currentUser.getRole())) {
            throw new ResourceNotFoundException("Collection not found or deleted");
        }

        // Check permission to edit collection
        if (!permissionService.canEditCollection(currentUser, collection)) {
            throw new AccessDeniedException("You do not have permission to edit this collection");
        }

        // Validate visibility
        String newVisibility;
        try {
            newVisibility = Visibility.parseVisibility(request.visibility()).name();
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid visibility value: " + request.visibility());
        }

        // Handle GROUP visibility
        Group group = collection.getGroup();
        if (Visibility.GROUP.name().equals(newVisibility)) {
            UUID groupId = request.groupId();
            if (groupId == null) {
                throw new InvalidActionException("GROUP visibility requires a groupId");
            }
            group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + groupId));
            if (!groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, currentUser.getUserId(), "active")) {
                throw new AccessDeniedException("You must be an active member of the group to set GROUP visibility");
            }
        }

        // Handle SCHOOL visibility
        if (Visibility.SCHOOL.name().equals(newVisibility) && currentUser.getSchoolId() == null) {
            throw new IllegalArgumentException("User must have a school affiliation for SCHOOL visibility");
        }

        // Update collection fields
        if (request.name() != null) {
            collection.setName(request.name());
        }
        if (request.description() != null) {
            collection.setDescription(request.description());
        }
        collection.setVisibility(newVisibility);
        collection.setGroup(group);
        collection.setUpdatedBy(currentUser.getUserId());
        collection.setUpdatedAt(Instant.now());

        // Handle tags
        if (request.tags() != null && !request.tags().isEmpty()) {
            // Validate tags
            List<Tag> tags = tagRepository.findAllById(request.tags());
            if (tags.size() != request.tags().size()) {
                throw new ResourceNotFoundException("One or more tags not found");
            }
            // Delete existing tags
            collectionTagRepository.deleteByCollectionId(id);
            // Create new CollectionTag entries
            List<CollectionTag> newCollectionTags = tags.stream()
                    .map(tag -> CollectionTag.builder()
                            .id(CollectionTagId.builder()
                                    .collectionId(id)
                                    .tagId(tag.getId())
                                    .build())
                            .collection(collection)
                            .tag(tag)
                            .createdAt(Instant.now())
                            .build())
                    .collect(Collectors.toList());
            collectionTagRepository.saveAll(newCollectionTags);
        } else {
            // Clear tags if none provided
            collectionTagRepository.deleteByCollectionId(id);
        }

        // Save updated collection
        Collection updatedCollection = collectionRepository.save(collection);

        // Build response
        return UpdateCollectionResponse.builder()
                .name(updatedCollection.getName())
                .description(updatedCollection.getDescription())
                .visibility(updatedCollection.getVisibility())
                .tags(mapCollectionTagsToTags(updatedCollection.getId()))
                .build();
    }

    @Override
    public void softDeleteCollection(UUID id, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        User currentUserEntity = userRepository.getReferenceById(currentUserId);

        Optional<Collection> opt = collectionRepository.findByIdAndIsDeletedFalse(id);
        if (opt.isEmpty()) {
            throw new ResourceNotFoundException("Collection not found");
        }
        Collection collection = opt.get();

        boolean owner = collection.getCreatedBy().equals(currentUserId);
        // only owner and admins can perform delete
        if (!owner && !isAdmin(currentUser)) {
            throw new AccessDeniedException("Not allowed to delete this collection");
        }

        collection.setIsDeleted(true);
        collection.setDeletedAt(Instant.now());
        collection.setDeletedBy(currentUserEntity);

        collectionRepository.save(collection);
        log.info("Collection soft-deleted: {} by user: {}", id, currentUserId);
    }

    @Override
    @Transactional
    public PageCollectionResponse listMyCollections(UserPrincipal currentUser, Pageable pageable) {
        UUID currentUserId = currentUser.getUserId();
        log.info("Retrieving collections for user: {} with pageable: {}", currentUserId, pageable);

        // Fetch collections created by the user, excluding deleted
        Page<Collection> page = collectionRepository.findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(currentUserId, pageable);

        // Map to CollectionResponse
        List<CollectionResponse> content = page.getContent().stream()
                .map(collection -> CollectionResponse.builder()
                        .name(collection.getName())
                        .description(collection.getDescription())
                        .visibility(collection.getVisibility())
                        .tags(mapCollectionTagsToTags(collection.getId()))
                        .createdAt(collection.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        // Build paginated response
        return PageCollectionResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    @Transactional
    public PageCollectionResponse listPublicCollections(Pageable pageable) {
        log.info("Retrieving public collections with pageable: {}", pageable);

        // Fetch collections with PUBLIC visibility, excluding deleted
        Page<Collection> page = collectionRepository.findByVisibilityAndIsDeletedFalseOrderByCreatedAtDesc("public", pageable);

        // Map to CollectionResponse
        List<CollectionResponse> content = page.getContent().stream()
                .map(collection -> CollectionResponse.builder()
                        .name(collection.getName())
                        .description(collection.getDescription())
                        .visibility(collection.getVisibility())
                        .tags(mapCollectionTagsToTags(collection.getId()))
                        .createdAt(collection.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        // Build paginated response
        return PageCollectionResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    @Transactional
    public PageCollectionResponse listAllCollections(UserPrincipal currentUser, Pageable pageable) {
        log.info("Retrieving all collections for user: {} with pageable: {}", currentUser.getUserId(), pageable);

        // Restrict to admins only
        if (!isAdmin(currentUser)) {
            throw new AccessDeniedException("Only admins can access all collections");
        }

        // Fetch all non-deleted collections
        Page<Collection> page = collectionRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(pageable);

        // Map to CollectionResponse
        List<CollectionResponse> content = page.getContent().stream()
                .map(collection -> CollectionResponse.builder()
                        .name(collection.getName())
                        .description(collection.getDescription())
                        .visibility(collection.getVisibility())
                        .tags(mapCollectionTagsToTags(collection.getId()))
                        .createdAt(collection.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        // Build paginated response
        return PageCollectionResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    @Transactional
    public PageCollectionResponse listAllCollectionsForAdmin(UserPrincipal currentUser, Pageable pageable) {
        log.info("Retrieving all collections (including deleted) for user: {} with pageable: {}", currentUser.getUserId(), pageable);

        // Restrict to SYSTEM_ADMIN only
        if (!isSystemAdmin(currentUser.getRole())) {
            throw new AccessDeniedException("Only SYSTEM_ADMIN can access all collections including deleted");
        }

        // Fetch all collections, including deleted
        Page<Collection> page = collectionRepository.findAll(pageable);

        // Map to CollectionResponse
        List<CollectionResponse> content = page.getContent().stream()
                .map(collection -> CollectionResponse.builder()
                        .name(collection.getName())
                        .description(collection.getDescription())
                        .visibility(collection.getVisibility())
                        .tags(mapCollectionTagsToTags(collection.getId()))
                        .createdAt(collection.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        // Build paginated response
        return PageCollectionResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    public long countMyCollections(UserPrincipal currentUser) {
        return 0;
    }

    // Helper method
    @Transactional
    protected List<Tag> mapCollectionTagsToTags(UUID collectionId) {
        List<CollectionTag> collectionTags = collectionTagRepository.findByCollectionId(collectionId);
        return collectionTags.stream()
                .map(CollectionTag::getTag)
                .collect(Collectors.toList());


//    @Override
//    @Transactional
//    public UpdateCollectionResponse updateCollection(UUID id, UpdateCollectionRequest req, UserPrincipal currentUser) {
//        UUID currentUserId = currentUser.getUserId();
//
//        Collection collection = collectionRepository.findByIdAndIsDeletedFalse(id)
//                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));
//
//        boolean owner = collection.getCreatedBy().equals(currentUserId);
//        if (!owner && !isAdmin(currentUser)) {
//            throw new AccessDeniedException("Not allowed to update this collection");
//        }
//
//        //TODO: we might need to check collection tags here (not allow illegal tags pass throw)
//        // partial update
//        if (req.name() != null) collection.setName(req.name());
//        if (req.description() != null) collection.setDescription(req.description());
//
//        if (req.tags() != null) {
//            collection.getCollectionTags().clear();
//            for (AddTagRequest tagReq : req.tags()) {
//                Tag tag = tagRepository.findByTypeAndValue(tagReq.type(), tagReq.value())
//                        .orElseGet(() -> tagRepository.save(
//                                Tag.builder()
//                                        .type(tagReq.type())
//                                        .value(tagReq.value())
//                                        .build()));
//                CollectionTag ct = CollectionTag.builder()
//                        .collection(collection)
//                        .tag(tag)
//                        .createdAt(Instant.now())
//                        .build();
//                collection.getCollectionTags().add(ct);
//            }
//        }
//
//        if (req.visibility() != null) {
//            Visibility vis = parseVisibility(req.visibility());
//            Group group = null;
//            if (vis == Visibility.GROUP) {
//                if (req.groupId() == null)
//                    throw new InvalidInputException("groupId is required for group visibility");
//                group = groupRepository.findById(req.groupId())
//                        .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
//                boolean isAllowed = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
//                        group.getId(),
//                        currentUserId,
//                        "active"
//                );
//                if (!isAllowed && !isAdmin(currentUser)) {
//                    throw new AccessDeniedException("You must be a member of the group to create a group collection");
//                }
//            }
//            collection.setVisibility(req.visibility());
//            collection.setGroup(group);
//        }
//
//        collection.setUpdatedBy(currentUserId);
//        collection.setUpdatedAt(Instant.now());
//        Collection updatedCol = collectionRepository.save(collection);
//
//        log.info("Collection updated: {} by user: {}", id, currentUserId);
//
//        return UpdateCollectionResponse.builder()
//                .id(updatedCol.getId())
//                .name(updatedCol.getName())
//                .description(updatedCol.getDescription())
//                .visibility(updatedCol.getVisibility())
//                .tags(updatedCol.getTags())
//                .updatedAt(updatedCol.getUpdatedAt())
//                .build();
//    }
//
//    @Override
//    @Transactional
//    public CollectionResponse getCollectionById(UUID id, UserPrincipal currentUser) {
//        Optional<Collection> opt = collectionRepository.findByIdAndIsDeletedFalse(id);
//        if (opt.isEmpty()) {
//            // still allow admins to see deleted resources via separate endpoint
//            throw new ResourceNotFoundException("Collection not found");
//        }
//        Collection collection = opt.get();
//        validateAccess(collection, currentUser);
//
//        return CollectionResponse.builder()
//                .id(collection.getId())
//                .name(collection.getName())
//                .description(collection.getDescription())
//                .visibility(collection.getVisibility())
//                .tags(collection.getTags())
//                .createdAt(collection.getCreatedAt())
//                .build();
//    }
//
//    @Override
//    @Transactional
//    public void softDeleteCollection(UUID id, UserPrincipal currentUser) {
//        UUID currentUserId = currentUser.getUserId();
//        User currentUserEntity = userRepository.getReferenceById(currentUserId);
//
//        Optional<Collection> opt = collectionRepository.findByIdAndIsDeletedFalse(id);
//        if (opt.isEmpty()) {
//            throw new ResourceNotFoundException("Collection not found");
//        }
//        Collection collection = opt.get();
//
//        boolean owner = collection.getCreatedBy().equals(currentUserId);
//        // only owner and admins can perform delete
//        if (!owner && !isAdmin(currentUser)) {
//            throw new AccessDeniedException("Not allowed to delete this collection");
//        }
//
//        collection.setIsDeleted(true);
//        collection.setDeletedAt(Instant.now());
//        collection.setDeletedBy(currentUserEntity);
//
//        collectionRepository.save(collection);
//        log.info("Collection soft-deleted: {} by user: {}", id, currentUserId);
//    }
//
//    @Override
//    @Transactional
//    public PageCollectionResponse listMyCollections(UserPrincipal currentUser, Pageable pageable) {
//        UUID currentUserId = currentUser.getUserId();
//        Page<Collection> page = collectionRepository.findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(currentUserId, pageable);
//
//        List<CollectionResponse> content = page.getContent().stream()
//                .map(collection -> new CollectionResponse(
//                        collection.getId(),
//                        collection.getName(),
//                        collection.getDescription(),
//                        collection.getVisibility(),
//                        collection.getTags(),
//                        collection.getCreatedAt()
//                ))
//                .collect(Collectors.toList());
//
//        return PageCollectionResponse.builder()
//                .content(content)
//                .totalElements(page.getTotalElements())
//                .totalPages(page.getTotalPages())
//                .pageNumber(page.getNumber())
//                .pageSize(page.getSize())
//                .build();
//    }
//
//    @Override
//    @Transactional
//    public PageCollectionResponse listPublicCollections(Pageable pageable) {
//        Page<Collection> page = collectionRepository.findPublicCollections(pageable);
//
//        List<CollectionResponse> content = page.getContent().stream()
//                .map(collection -> new CollectionResponse(
//                        collection.getId(),
//                        collection.getName(),
//                        collection.getDescription(),
//                        collection.getVisibility(),
//                        collection.getTags(),
//                        collection.getCreatedAt()
//                ))
//                .collect(Collectors.toList());
//
//        return PageCollectionResponse.builder()
//                .content(content)
//                .totalElements(page.getTotalElements())
//                .totalPages(page.getTotalPages())
//                .pageNumber(page.getNumber())
//                .pageSize(page.getSize())
//                .build();
//    }
//
//    @Override
//    @Transactional
//    public long countMyCollections(UserPrincipal currentUser) {
//        UUID currentUserId = currentUser.getUserId();
//        return collectionRepository.countByCreatedByAndIsDeletedFalse(currentUserId);
//    }

    }
}