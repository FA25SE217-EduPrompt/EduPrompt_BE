package SEP490.EduPrompt.service.admin;

import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.group.CreateGroupRequest;
import SEP490.EduPrompt.dto.response.auditLog.AuditLogResponse;
import SEP490.EduPrompt.dto.response.auditLog.PageAuditLogResponse;
import SEP490.EduPrompt.dto.response.collection.CollectionResponse;
import SEP490.EduPrompt.dto.response.collection.CreateCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.PageCollectionResponse;
import SEP490.EduPrompt.dto.response.group.CreateGroupResponse;
import SEP490.EduPrompt.dto.response.group.GroupResponse;
import SEP490.EduPrompt.dto.response.group.PageGroupResponse;
import SEP490.EduPrompt.dto.response.prompt.*;
import SEP490.EduPrompt.dto.response.tag.PageTagResponse;
import SEP490.EduPrompt.dto.response.tag.TagResponse;
import SEP490.EduPrompt.dto.response.user.PageUserResponse;
import SEP490.EduPrompt.dto.response.user.UserResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
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
import java.util.UUID;
import java.util.stream.Collectors;

import static SEP490.EduPrompt.enums.Visibility.parseVisibility;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemAdminServiceImpl implements SystemAdminService {

    private final UserRepository userRepository;
    private final PromptRepository promptRepository;
    private final PromptTagRepository promptTagRepository;
    private final CollectionRepository  collectionRepository;
    private final GroupRepository  groupRepository;
    private final CollectionTagRepository collectionTagRepository;
    private final PermissionService permissionService;
    private final TagRepository tagRepository;
    private final GroupMemberRepository  groupMemberRepository;

    //========================================================
    //======================LIST ALL==========================
    @Override
    public PageUserResponse listAllUser(UserPrincipal currentUser, Pageable pageable) {
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission do this!!");
        }

        Page<User> page = userRepository.findAll(pageable);
        List<UserResponse> content = page.getContent().stream()
                .map(users -> UserResponse.builder()
                        .id(users.getId())
                        .subscriptionTier(users.getSubscriptionTier())
                        .subscriptionTierId(users.getSubscriptionTierId())
                        .schoolId(users.getSchoolId())
                        .email(users.getEmail())
                        .role(users.getRole())
                        .firstName(users.getFirstName())
                        .lastName(users.getLastName())
                        .phoneNumber(users.getPhoneNumber())
                        .isActive(users.getIsActive())
                        .isVerified(users.getIsVerified())
                        .createdAt(users.getCreatedAt())
                        .updatedAt(users.getUpdatedAt())
                        .build())
                .toList();
        return PageUserResponse.builder()
                .content(content)
                .pageSize(page.getSize())
                .pageNumber(page.getNumber())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }

    @Override
    public PageCollectionResponse listAllCollection(UserPrincipal currentUser, Pageable pageable) {
        // Restrict to admins only
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission do this!!");
        }

        // Fetch all non-deleted collections
        Page<Collection> page = collectionRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(pageable);

        // Map to CollectionResponse
        List<CollectionResponse> content = page.getContent().stream()
                .map(collection -> CollectionResponse.builder()
                        .id(collection.getId())
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
    public PageGroupResponse listAllGroup(UserPrincipal currentUser, Pageable pageable) {
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission do this!!");
        }
        Page<Group> page;
        page = groupRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<GroupResponse> content = page.getContent().stream()
                .map(group -> GroupResponse.builder()
                        .name(group.getName())
                        .schoolId(group.getSchool() != null ? group.getSchool().getId() : null)
                        .isActive(group.getIsActive())
                        .createdAt(group.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PageGroupResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    public PageTagResponse listAllTag(UserPrincipal currentUser, Pageable pageable) {
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission do this!!");
        }
        Page<Tag> page;
        page = tagRepository.findAll(pageable);
        List<TagResponse> content = page.getContent().stream()
                .map(tag -> TagResponse.builder()
                        .id(tag.getId())
                        .type(tag.getType())
                        .value(tag.getValue())
                        .build())
                .toList();
        return PageTagResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    public PagePromptAllResponse listAllPrompt(UserPrincipal currentUser, Pageable pageable) {
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission do this!!");
        }
        Page<Prompt> page;
        page = promptRepository.findAll(pageable);
        List<PromptAllResponse> content = page.getContent().stream()
                .map(prompt -> PromptAllResponse.builder()
                        .id(prompt.getId())
                        .userId(prompt.getUserId())
                        .collectionId(prompt.getCollectionId())
                        .title(prompt.getTitle())
                        .description(prompt.getDescription())
                        .instruction(prompt.getInstruction())
                        .context(prompt.getContext())
                        .inputExample(prompt.getInputExample())
                        .outputFormat(prompt.getOutputFormat())
                        .constraints(prompt.getConstraints())
                        .visibility(prompt.getVisibility())
                        .createdBy(prompt.getCreatedBy())
                        .updatedBy(prompt.getUpdatedBy())
                        .createdAt(prompt.getCreatedAt())
                        .updatedAt(prompt.getUpdatedAt())
                        .isDeleted(prompt.getIsDeleted())
                        .deletedAt(prompt.getDeletedAt())
                        .currentVersionId(prompt.getCurrentVersion().getId())
                        .avgRating(prompt.getAvgRating())
                        .geminiFileId(prompt.getGeminiFileId())
                        .lastIndexedAt(prompt.getLastIndexedAt())
                        .indexingStatus(prompt.getIndexingStatus())
                        .tags(mapPromptTagsToTags(prompt.getId()))
                        .shareToken(prompt.getShareToken())
                        .build())
                .toList();
        return PagePromptAllResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    //========================================================
    //======================= CREATE =========================
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
                .id(collection.getId())
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
    public CreateGroupResponse createGroup(CreateGroupRequest req, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        User creator = userRepository.getReferenceById(currentUserId);


//        // SCHOOL_ADMIN and TEACHER must belong to a school
//        UUID schoolId = currentUser.getSchoolId();
//        if (schoolId == null && !Role.SYSTEM_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
//            throw new AccessDeniedException("You must be associated with a school to create a group");
//        }
//        School school = null;
//        if (schoolId != null) {
//            school = schoolRepository.findById(schoolId)
//                    .orElseThrow(() -> new ResourceNotFoundException("School not found"));
//        }

        Group group = Group.builder()
                .name(req.name())
                .school(null)
                .createdBy(creator)
                .updatedBy(creator)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Group saved = groupRepository.save(group);
        log.info("Group created: {} by user: {}", saved.getId(), currentUserId);

        // Automatically add creator as group admin
        groupMemberRepository.save(GroupMember.builder()
                .group(saved)
                .user(creator)
                .role("admin")
                .status("active")
                .joinedAt(Instant.now())
                .build());

        return CreateGroupResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .schoolId(saved.getSchool() != null ? saved.getSchool().getId() : null)
                .isActive(saved.getIsActive())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    //HELPER
    private List<Tag> mapCollectionTagsToTags(UUID collectionId) {
        List<CollectionTag> collectionTags = collectionTagRepository.findByCollectionId(collectionId);
        return collectionTags.stream()
                .map(CollectionTag::getTag)
                .collect(Collectors.toList());
    }
    private List<Tag> mapPromptTagsToTags(UUID promptId) {
        List<PromptTag> promptTags = promptTagRepository.findByPromptId(promptId);
        return promptTags.stream()
                .map(PromptTag::getTag)
                .collect(Collectors.toList());
    }
}
