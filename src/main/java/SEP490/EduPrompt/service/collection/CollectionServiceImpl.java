package SEP490.EduPrompt.service.collection;

import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.collection.UpdateCollectionRequest;
import SEP490.EduPrompt.dto.response.collection.CollectionResponse;
import SEP490.EduPrompt.dto.response.collection.CreateCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.PageCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.UpdateCollectionResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Collection;
import SEP490.EduPrompt.model.Group;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.CollectionRepository;
import SEP490.EduPrompt.repo.GroupMemberRepository;
import SEP490.EduPrompt.repo.GroupRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;


    private void validateAccess(Collection collection, UserPrincipal currentUser) {
        String currentRole = currentUser.getRole();
        UUID currentUserId = currentUser.getUserId();

        // deleted means only system admin can access
        if (collection.getIsDeleted()) {
            if (!Role.SYSTEM_ADMIN.name().equalsIgnoreCase(currentRole)) {
                throw new AccessDeniedException("You do not have permission to access this collection");
            }
            return;
        }

        // admin bypass
        if (Role.SYSTEM_ADMIN.name().equalsIgnoreCase(currentRole) || Role.SCHOOL_ADMIN.name().equalsIgnoreCase(currentRole)) {
            return;
        }

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
                boolean member = groupMemberRepository.existsByGroupIdAndUserIdAndStatusAndRoleIn(
                        collection.getGroup().getId(), currentUserId, "active", List.of("admin", "member")
                );
                if (!member) throw new AccessDeniedException("You are not a member of this group");
            }
            default -> throw new AccessDeniedException("Unsupported visibility");
        }
    }

    private boolean isAdmin(UserPrincipal user) {
        String role = user.getRole();
        return Role.SYSTEM_ADMIN.name().equalsIgnoreCase(role) || Role.SCHOOL_ADMIN.name().equalsIgnoreCase(role);
    }

    @Override
    @Transactional
    public CreateCollectionResponse createCollection(CreateCollectionRequest req, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();

        //check user's subscription to control total own collections

        Visibility vis = parseVisibility(req.visibility());
        Group group = null;
        if (vis == Visibility.GROUP) {
            if (req.groupId() == null) {
                throw new InvalidInputException("groupId is required for group visibility");
            }
            group = groupRepository.findById(req.groupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

            // creator must be a member of the group
            boolean isAllowed = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                    group.getId(), currentUserId, "active");
            if (!isAllowed && !isAdmin(currentUser)) {
                throw new AccessDeniedException("You must be a member of the group to create a group collection");
            }
        }

        Collection collection = Collection.builder()
                .name(req.name())
                .description(req.description())
                .visibility(req.visibility())
                .tags(req.tags() != null ? req.tags() : List.of())
                .group(group)
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .isDeleted(false)
                .build();

        //TODO: we might need to check collection tags here (not allow illegal tags pass throw)

        Collection saved = collectionRepository.save(collection);
        log.info("Collection created: {} by user: {}", saved.getId(), currentUserId);

        return CreateCollectionResponse.builder()
                .id(collection.getId())
                .name(collection.getName())
                .description(collection.getDescription())
                .visibility(collection.getVisibility())
                .tags(collection.getTags())
                .createdAt(collection.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public UpdateCollectionResponse updateCollection(UUID id, UpdateCollectionRequest req, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();

        Collection collection = collectionRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));

        boolean owner = collection.getCreatedBy().equals(currentUserId);
        if (!owner && !isAdmin(currentUser)) {
            throw new AccessDeniedException("Not allowed to update this collection");
        }

        //TODO: we might need to check collection tags here (not allow illegal tags pass throw)
        //partial update
        if (req.name() != null) collection.setName(req.name());
        if (req.description() != null) collection.setDescription(req.description());
        if (req.tags() != null) collection.setTags(req.tags());

        Visibility vis = parseVisibility(req.visibility());
        Group group = null;
        if (vis == Visibility.GROUP) {
            if (req.groupId() == null) {
                throw new InvalidInputException("groupId is required for group visibility");
            }
            group = groupRepository.findById(req.groupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

            // creator must be a member of the group
            boolean isAllowed = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                    group.getId(), currentUserId, "active");
            if (!isAllowed && !isAdmin(currentUser)) {
                throw new AccessDeniedException("You must be a member of the group to create a group collection");
            }
        }

        collection.setVisibility(req.visibility());
        collection.setGroup(group);

        collection.setUpdatedBy(currentUserId);
        collection.setUpdatedAt(Instant.now());

        Collection updatedCol = collectionRepository.save(collection);
        log.info("Collection updated: {} by user: {}", id, currentUserId);

        return UpdateCollectionResponse.builder()
                .id(updatedCol.getId())
                .name(updatedCol.getName())
                .description(updatedCol.getDescription())
                .visibility(updatedCol.getVisibility())
                .tags(updatedCol.getTags())
                .updatedAt(updatedCol.getUpdatedAt())
                .build();
    }

    @Override
    public CollectionResponse getCollectionById(UUID id, UserPrincipal currentUser) {
        Optional<Collection> opt = collectionRepository.findByIdAndIsDeletedFalse(id);
        if (opt.isEmpty()) {
            // still allow admins to see deleted resources via separate endpoint
            throw new ResourceNotFoundException("Collection not found");
        }
        Collection collection = opt.get();
        validateAccess(collection, currentUser);

        return CollectionResponse.builder()
                .id(collection.getId())
                .name(collection.getName())
                .description(collection.getDescription())
                .visibility(collection.getVisibility())
                .tags(collection.getTags())
                .createdAt(collection.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
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
    public PageCollectionResponse listMyCollections(UserPrincipal currentUser, Pageable pageable) {
        UUID currentUserId = currentUser.getUserId();
        Page<Collection> page = collectionRepository.findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(currentUserId, pageable);

        List<CollectionResponse> content = page.getContent().stream()
                .map(collection -> new CollectionResponse(
                        collection.getId(),
                        collection.getName(),
                        collection.getDescription(),
                        collection.getVisibility(),
                        collection.getTags(),
                        collection.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return PageCollectionResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    public PageCollectionResponse listPublicCollections(Pageable pageable) {
        Page<Collection> page = collectionRepository.findPublicCollections(pageable);

        List<CollectionResponse> content = page.getContent().stream()
                .map(collection -> new CollectionResponse(
                        collection.getId(),
                        collection.getName(),
                        collection.getDescription(),
                        collection.getVisibility(),
                        collection.getTags(),
                        collection.getCreatedAt()
                ))
                .collect(Collectors.toList());

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
        UUID currentUserId = currentUser.getUserId();
        return collectionRepository.countByCreatedByAndIsDeletedFalse(currentUserId);
    }

}