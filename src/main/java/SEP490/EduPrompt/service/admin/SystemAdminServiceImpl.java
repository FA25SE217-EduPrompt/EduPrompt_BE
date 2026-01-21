package SEP490.EduPrompt.service.admin;

import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.collection.UpdateCollectionRequest;
import SEP490.EduPrompt.dto.request.group.CreateGroupRequest;
import SEP490.EduPrompt.dto.request.group.UpdateGroupRequest;
import SEP490.EduPrompt.dto.request.prompt.CreatePromptCollectionRequest;
import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptMetadataRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptVisibilityRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.PageTeacherTokenUsageLogResponse;
import SEP490.EduPrompt.dto.request.systemAdmin.SchoolSubscriptionTokenStatusResponse;
import SEP490.EduPrompt.dto.request.systemAdmin.TeacherTokenMonthlyUsageResponse;
import SEP490.EduPrompt.dto.request.tag.CreateTagBatchRequest;
import SEP490.EduPrompt.dto.response.collection.CollectionResponse;
import SEP490.EduPrompt.dto.response.collection.CreateCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.PageCollectionResponse;
import SEP490.EduPrompt.dto.response.collection.UpdateCollectionResponse;
import SEP490.EduPrompt.dto.response.group.CreateGroupResponse;
import SEP490.EduPrompt.dto.response.group.GroupResponse;
import SEP490.EduPrompt.dto.response.group.PageGroupResponse;
import SEP490.EduPrompt.dto.response.group.UpdateGroupResponse;
import SEP490.EduPrompt.dto.response.payment.MonthlyPaymentSummaryResponse;
import SEP490.EduPrompt.dto.response.payment.PagePaymentAdminResponse;
import SEP490.EduPrompt.dto.response.payment.PaymentAdminListResponse;
import SEP490.EduPrompt.dto.response.prompt.*;
import SEP490.EduPrompt.dto.response.tag.PageTagResponse;
import SEP490.EduPrompt.dto.response.tag.TagResponse;
import SEP490.EduPrompt.dto.response.teacherTokenUsed.TeacherTokenUsageLogResponse;
import SEP490.EduPrompt.dto.response.user.PageUserResponse;
import SEP490.EduPrompt.dto.response.user.UserResponse;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.Collection;
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
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static SEP490.EduPrompt.enums.Visibility.parseVisibility;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemAdminServiceImpl implements SystemAdminService {

    private final UserRepository userRepository;
    private final PromptRepository promptRepository;
    private final PromptTagRepository promptTagRepository;
    private final CollectionRepository collectionRepository;
    private final GroupRepository groupRepository;
    private final CollectionTagRepository collectionTagRepository;
    private final PermissionService permissionService;
    private final TagRepository tagRepository;
    private final PaymentRepository paymentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SchoolSubscriptionRepository schoolSubscriptionRepository;
    private final TeacherTokenUsageLogRepository teacherTokenUsageLogRepository;
    private final PromptScoreRepository promptScoreRepository;

    // ========================================================
    // ======================LIST ALL==========================
    @Override
    @Transactional
    public PageUserResponse listAllUser(UserPrincipal currentUser, Pageable pageable) {
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission do this!!");
        }

        Page<User> page = userRepository.findAll(pageable);
        List<UserResponse> content = page.getContent().stream()
                .map(users -> UserResponse.builder()
                        .id(users.getId())
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
    @Transactional
    public PageCollectionResponse listAllCollection(UserPrincipal currentUser, Pageable pageable) {
        // Restrict to admins only
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission do this!!");
        }

        // Fetch all non-deleted collections
        Page<Collection> page = collectionRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(pageable);

        List<UUID> collectionIds = page.getContent().stream().map(Collection::getId).toList();
        List<CollectionTag> allTags = collectionTagRepository.findByCollectionIdIn(collectionIds);
        Map<UUID, List<Tag>> tagsByCollectionId = allTags.stream()
                .collect(Collectors.groupingBy(
                        ct -> ct.getCollection().getId(),
                        Collectors.mapping(CollectionTag::getTag, Collectors.toList())));

        // Map to CollectionResponse
        List<CollectionResponse> content = page.getContent().stream()
                .map(collection -> CollectionResponse.builder()
                        .id(collection.getId())
                        .name(collection.getName())
                        .description(collection.getDescription())
                        .visibility(collection.getVisibility())
                        .tags(tagsByCollectionId.getOrDefault(collection.getId(), Collections.emptyList()))
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
    public PageGroupResponse listAllGroup(UserPrincipal currentUser, Pageable pageable) {
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission do this!!");
        }
        Page<Group> page;
        page = groupRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<GroupResponse> content = page.getContent().stream()
                .map(group -> GroupResponse.builder()
                        .id(group.getId())
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
    @Transactional
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
    @Transactional
    public PagePromptAllResponse listAllPrompt(UserPrincipal currentUser, Pageable pageable) {
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission do this!!");
        }
        Page<Prompt> page;
        page = promptRepository.findAll(pageable);

        List<UUID> promptIds = page.getContent().stream().map(Prompt::getId).toList();
        List<PromptTag> allTags = promptTagRepository.findByPromptIdIn(promptIds);
        Map<UUID, List<Tag>> tagsByPromptId = allTags.stream()
                .collect(Collectors.groupingBy(
                        pt -> pt.getPrompt().getId(),
                        Collectors.mapping(PromptTag::getTag, Collectors.toList())));

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
                        .currentVersionId(prompt.getCurrentVersionId())
                        .avgRating(prompt.getAvgRating())
                        .geminiFileId(prompt.getGeminiFileId())
                        .lastIndexedAt(prompt.getLastIndexedAt())
                        .indexingStatus(prompt.getIndexingStatus())
                        .tags(tagsByPromptId.getOrDefault(prompt.getId(), Collections.emptyList()))
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

    // ========================================================
    // ======================= CREATE =========================
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

        // // SCHOOL_ADMIN and TEACHER must belong to a school
        // UUID schoolId = currentUser.getSchoolId();
        // if (schoolId == null &&
        // !Role.SYSTEM_ADMIN.name().equalsIgnoreCase(currentUser.getRole())) {
        // throw new AccessDeniedException("You must be associated with a school to
        // create a group");
        // }
        // School school = null;
        // if (schoolId != null) {
        // school = schoolRepository.findById(schoolId)
        // .orElseThrow(() -> new ResourceNotFoundException("School not found"));
        // }

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

    @Override
    @Transactional
    public DetailPromptResponse createStandalonePrompt(CreatePromptRequest dto, UserPrincipal currentUser) {
        // Fetch User entity
        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String promptVisibility = Visibility.parseVisibility(dto.getVisibility()).name();

        // Standalone prompt cannot have GROUP visibility
        if (promptVisibility.equals(Visibility.GROUP.name())) {
            throw new InvalidInputException("GROUP visibility requires a collection with a group");
        }

        // SCHOOL visibility requires school affiliation
        if (promptVisibility.equals(Visibility.SCHOOL.name()) && currentUser.getSchoolId() == null) {
            throw new InvalidInputException("User must have a school affiliation for SCHOOL visibility");
        }

        // Validate tags
        // Check if the tag id exist in the DB
        /*
         * If the user includes tag IDs in the request, fetch those tags from the
         * database.
         * If any of the tag IDs don’t exist, stop and throw an error —
         * don’t allow the prompt to be created with invalid tags
         */
        List<Tag> tags = new ArrayList<>();
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            tags = tagRepository.findAllById(dto.getTagIds());
            if (tags.size() != dto.getTagIds().size()) {
                throw new ResourceNotFoundException("One or more tags not found");
            }
        }

        // Build Prompt
        Prompt prompt = Prompt.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .instruction(dto.getInstruction())
                .context(dto.getContext())
                .inputExample(dto.getInputExample())
                .outputFormat(dto.getOutputFormat())
                .constraints(dto.getConstraints())
                .user(user)
                .createdBy(currentUser.getUserId())
                .updatedBy(currentUser.getUserId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .isDeleted(false)
                .visibility(promptVisibility)
                .collection(null)
                .build();

        Prompt savedPrompt = promptRepository.save(prompt);

        // Create PromptTag entries
        if (!tags.isEmpty()) {
            List<PromptTag> promptTags = tags.stream()
                    .map(tag -> PromptTag.builder()
                            .id(PromptTagId.builder()
                                    .promptId(savedPrompt.getId())
                                    .tagId(tag.getId())
                                    .build())
                            .prompt(savedPrompt)
                            .tag(tag)
                            .createdAt(Instant.now())
                            .build())
                    .collect(Collectors.toList());
            promptTagRepository.saveAll(promptTags);
        }

        // Build response
        return buildPromptResponse(savedPrompt);
    }

    @Override
    @Transactional
    public DetailPromptResponse createPromptInCollection(CreatePromptCollectionRequest dto, UserPrincipal currentUser) {
        // Fetch User entity
        User user = userRepository.getReferenceById(currentUser.getUserId());

        // Validate and default visibility
        String promptVisibility = Visibility.parseVisibility(dto.getVisibility()).name();

        // Fetch Collection
        Collection collection = collectionRepository.findByIdAndUserId(dto.getCollectionId(), currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found or not owned by user"));

        if (collection.getIsDeleted()) {
            throw new InvalidActionException("Cannot add prompt to deleted collection");
        }

        permissionService.validateCollectionVisibility(collection, promptVisibility);

        // GROUP visibility → check group membership
        if (promptVisibility.equals(Visibility.GROUP.name()) && collection.getGroup() != null) {
            Group group = groupRepository.findById(collection.getGroup().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

            if (!groupMemberRepository.existsByGroupIdAndUserId(group.getId(), user.getId())) {
                throw new AccessDeniedException("User is not a member of the collection's group");
            }
        }

        // SCHOOL visibility → check school affiliation
        if (promptVisibility.equals(Visibility.SCHOOL.name())) {
            if (currentUser.getSchoolId() == null) {
                throw new InvalidInputException("User must have a school affiliation for SCHOOL visibility");
            }
            User collectionOwner = collection.getUser();
            if (!currentUser.getSchoolId().equals(collectionOwner.getSchoolId())) {
                throw new InvalidInputException("User's school does not match collection owner's school");
            }
        }

        // Validate tags
        List<Tag> tags = new ArrayList<>();
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            tags = tagRepository.findAllById(dto.getTagIds());
            if (tags.size() != dto.getTagIds().size()) {
                throw new ResourceNotFoundException("One or more tags not found");
            }
        }

        // Build Prompt
        Prompt prompt = Prompt.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .instruction(dto.getInstruction())
                .context(dto.getContext())
                .inputExample(dto.getInputExample())
                .outputFormat(dto.getOutputFormat())
                .constraints(dto.getConstraints())
                .user(user)
                .createdBy(currentUser.getUserId())
                .updatedBy(currentUser.getUserId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .isDeleted(false)
                .visibility(promptVisibility)
                .collection(collection)
                .build();

        Prompt savedPrompt = promptRepository.save(prompt);

        // Create PromptTag entries
        if (!tags.isEmpty()) {
            List<PromptTag> promptTags = tags.stream()
                    .map(tag -> PromptTag.builder()
                            .id(PromptTagId.builder()
                                    .promptId(savedPrompt.getId())
                                    .tagId(tag.getId())
                                    .build())
                            .prompt(savedPrompt)
                            .tag(tag)
                            .createdAt(Instant.now())
                            .build())
                    .collect(Collectors.toList());
            promptTagRepository.saveAll(promptTags);
        }

        // Build response
        return buildPromptResponse(savedPrompt);
    }

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
                        (a, b) -> a));

        // Step 4: Filter out duplicates
        List<Tag> toSave = unique.entrySet().stream()
                .filter(e -> !existingMap.containsKey(e.getKey()))
                .map(e -> {
                    var r = e.getValue();
                    return Tag.builder()
                            .type(r.type()) // preserve original casing
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
                                    .filter(s -> (s.getType().toLowerCase() + "::" + s.getValue().toLowerCase())
                                            .equals(key))
                                    .findFirst()
                                    .orElse(null));
                    return tag != null ? new TagResponse(tag.getId(), tag.getType(), tag.getValue()) : null;
                })
                .toList();

        log.info("Created {} new tags, {} skipped (already exist)", saved.size(), unique.size() - saved.size());
        return result;
    }

    // ========================================================
    // ======================= UPDATE =========================
    @Override
    @Transactional
    public DetailPromptResponse updatePromptMetadata(UUID promptId, UpdatePromptMetadataRequest request,
                                                     UserPrincipal currentUser) {
        // Fetch prompt
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found!!"));

        // Update only provided fields
        if (request.getTitle() != null) {
            prompt.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            prompt.setDescription(request.getDescription());
        }
        if (request.getInstruction() != null) {
            prompt.setInstruction(request.getInstruction());
        }
        if (request.getContext() != null) {
            prompt.setContext(request.getContext());
        }
        if (request.getInputExample() != null) {
            prompt.setInputExample(request.getInputExample());
        }
        if (request.getOutputFormat() != null) {
            prompt.setOutputFormat(request.getOutputFormat());
        }
        if (request.getConstraints() != null) {
            prompt.setConstraints(request.getConstraints());
        }

        // Update timestamp and user
        prompt.setUpdatedAt(Instant.now());
        prompt.setUpdatedBy(currentUser.getUserId());

        // Handle tags if provided
        if (request.getTagIds() != null) {
            // Remove existing PromptTag entries
            promptTagRepository.deleteByPromptId(promptId);

            // If tagIds are provided and not empty, validate and create new PromptTag
            // entries
            if (!request.getTagIds().isEmpty()) {
                List<Tag> tags = tagRepository.findAllById(request.getTagIds());
                if (tags.size() != request.getTagIds().size()) {
                    throw new ResourceNotFoundException("One or more tags not found!!");
                }

                List<PromptTag> newPromptTags = tags.stream()
                        .map(tag -> PromptTag.builder()
                                .id(PromptTagId.builder()
                                        .promptId(promptId)
                                        .tagId(tag.getId())
                                        .build())
                                .prompt(prompt)
                                .tag(tag)
                                .createdAt(Instant.now())
                                .build())
                        .collect(Collectors.toList());
                promptTagRepository.saveAll(newPromptTags);
            }
        }

        // Save updated prompt
        Prompt updatedPrompt = promptRepository.save(prompt);

        // Build response
        return buildPromptResponse(updatedPrompt);
    }

    @Override
    @Transactional
    public DetailPromptResponse updatePromptVisibility(UUID promptId, UpdatePromptVisibilityRequest request,
                                                       UserPrincipal currentUser) {
        // Fetch prompt
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found with ID: " + promptId));

        // Validate visibility
        String newVisibility;
        try {
            newVisibility = Visibility.parseVisibility(request.getVisibility()).name();
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid visibility value: " + request.getVisibility());
        }

        // Handle visibility transitions
        Collection collection = prompt.getCollection();
        boolean removeFromCollection = false;

        if (newVisibility.equals(Visibility.PRIVATE.name()) || newVisibility.equals(Visibility.PUBLIC.name())) {
            // For PRIVATE or PUBLIC, check if current collection allows it
            if (collection != null) {
                try {
                    permissionService.validateCollectionVisibility(collection, newVisibility);
                } catch (IllegalArgumentException e) {
                    // If validation fails, automatically remove from collection to make it
                    // standalone
                    removeFromCollection = true;
                }
            }
        }

        if (removeFromCollection) {
            prompt.setCollection(null);
        } else if (newVisibility.equals(Visibility.GROUP.name())) {
            // GROUP visibility requires a collection with a group
            if (collection == null && request.getCollectionId() == null) {
                throw new InvalidInputException("GROUP visibility requires a collection");
            }
            if (request.getCollectionId() != null) {
                // Move standalone prompt to a collection
                collection = collectionRepository.findById(request.getCollectionId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Collection not found with ID: " + request.getCollectionId()));
                if (collection.getGroup() == null) {
                    throw new IllegalArgumentException(
                            "Collection must be associated with a group for GROUP visibility");
                }

                prompt.setCollection(collection);
            } else if (collection.getGroup() == null) {
                throw new InvalidActionException(
                        "Current collection must be associated with a group for GROUP visibility");
            }

            // Validate collection visibility only if not removing
            if (collection != null) {
                permissionService.validateCollectionVisibility(collection, newVisibility);
            }
        } else if (newVisibility.equals(Visibility.SCHOOL.name())) {
            if (currentUser.getSchoolId() == null) {
                throw new InvalidInputException("User must have a school affiliation for SCHOOL visibility");
            }
            if (collection != null) {
                permissionService.validateCollectionVisibility(collection, newVisibility);
            }
            if (request.getCollectionId() != null) {
                collection = collectionRepository.findById(request.getCollectionId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Collection not found with ID: " + request.getCollectionId()));

                permissionService.validateCollectionVisibility(collection, newVisibility);
                prompt.setCollection(collection);
            }
        } else {
            if (collection != null) {
                permissionService.validateCollectionVisibility(collection, newVisibility);
            }
            if (request.getCollectionId() != null) {
                collection = collectionRepository.findById(request.getCollectionId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Collection not found with ID: " + request.getCollectionId()));

                permissionService.validateCollectionVisibility(collection, newVisibility);
                prompt.setCollection(collection);
            }
        }

        // Update visibility
        prompt.setVisibility(newVisibility);
        prompt.setUpdatedAt(Instant.now());
        prompt.setUpdatedBy(currentUser.getUserId());

        // Save updated prompt
        Prompt updatedPrompt = promptRepository.save(prompt);

        // Build and return response
        return buildPromptResponse(updatedPrompt);
    }

    @Override
    @Transactional
    public UpdateCollectionResponse updateCollection(UUID id, UpdateCollectionRequest request,
                                                     UserPrincipal currentUser) {
        log.info("Updating collection: {} by user: {}", id, currentUser.getUserId());

        // Fetch collection
        Collection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found with ID: " + id));

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
                .tags(collectionTagRepository.findByCollection_Id(updatedCollection.getId()).stream()
                        .map(CollectionTag::getTag)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional
    public UpdateGroupResponse updateGroup(UUID id, UpdateGroupRequest req, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        Group group = groupRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        // Track if any changes were made
        boolean updated = false;

        // Update name if provided
        if (req.name() != null && !req.name().isBlank()) {
            group.setName(req.name());
            updated = true;
        }

        // Update isActive if provided
        if (req.isActive() != null && !req.isActive().equals(group.getIsActive())) {
            group.setIsActive(req.isActive());
            updated = true;
        }

        // Update group metadata if changes were made
        if (updated) {
            group.setUpdatedBy(userRepository.getReferenceById(currentUserId));
            group.setUpdatedAt(Instant.now());
            groupRepository.save(group);
            log.info("Group updated: {} by user: {}", id, currentUserId);
        } else {
            log.info("No changes applied to group: {} by user: {}", id, currentUserId);
        }

        return UpdateGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .schoolId(group.getSchoolId())
                .isActive(group.getIsActive())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    // ========================================================
    // ======================= DELETE =========================
    @Override
    @Transactional
    public void softDeletePrompt(UUID promptId, UserPrincipal currentUser) {
        // Fetch prompt
        log.info("User with id {} attempt to delete prompt with id {}", currentUser.getUserId(), promptId);
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found with ID: " + promptId));

        if (!prompt.getIsDeleted()) {
            prompt.setIsDeleted(true);
            prompt.setDeletedAt(Instant.now());
        } else {
            throw new InvalidActionException("Prompt is deleted.");
        }
        // Save changes
        promptRepository.save(prompt);
        log.info("Prompt with id {} has been successfully deleted", promptId);
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

        if (!collection.getIsDeleted()) {
            collection.setIsDeleted(true);
            collection.setDeletedAt(Instant.now());
            collection.setDeletedBy(currentUserEntity);
        } else {
            throw new InvalidActionException("Collection is deleted.");
        }

        collectionRepository.save(collection);
        log.info("Collection soft-deleted: {} by user: {}", id, currentUserId);
    }

    @Override
    public void softDeleteGroup(UUID id, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        Group group = groupRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        if (group.getIsActive()) {
            group.setIsActive(false);
            group.setUpdatedBy(userRepository.getReferenceById(currentUserId));
            group.setUpdatedAt(Instant.now());
        } else {
            throw new InvalidActionException("Group is deleted.");
        }

        groupRepository.save(group);
        log.info("Group soft-deleted: {} by user: {}", id, currentUserId);
    }

    //
    @Override
    @Transactional
    public List<MonthlyPaymentSummaryResponse> getMonthlyPaymentSummary(UserPrincipal currentUser) {
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission to view payment summary");
        }

        // Using query with grouping (you need to add this query to PaymentRepository)
        List<Object[]> rawSummary = paymentRepository.getMonthlyPaymentSummaryRaw();

        return rawSummary.stream()
                .map(row -> {
                    int year  = ((Number) row[0]).intValue();
                    int month = ((Number) row[1]).intValue();

                    long totalCount   = ((Number) row[2]).longValue();
                    long totalAmount  = row[3] != null ? ((Number) row[3]).longValue() : 0L;
                    long pendingCount = ((Number) row[4]).longValue();
                    long successCount = ((Number) row[5]).longValue();
                    long failedCount  = row[6] != null ? ((Number) row[6]).longValue() : 0L;

                    double avg = successCount > 0 ? (double) totalAmount / successCount : 0.0;

                    return MonthlyPaymentSummaryResponse.builder()
                            .year(year)
                            .month(month)
                            .monthName(Month.of(month).name())
                            .totalAmount(totalAmount)
                            .totalTransactions(totalCount)
                            .successfulCount(successCount)
                            .pendingCount(pendingCount)
                            .failedCount(failedCount)
                            .averageAmount(avg)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PagePaymentAdminResponse listAllPayments(
            UserPrincipal currentUser,
            Pageable pageable,
            String status,
            String yearMonth) {

        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission to view payments");
        }

        Page<Payment> page;

        // Optional filters - simple version (you can make more sophisticated later)
        if (StringUtils.hasText(yearMonth) && yearMonth.matches("\\d{4}-\\d{2}")) {
            String[] parts = yearMonth.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);

            LocalDate startDate = LocalDate.of(year, month, 1);
            LocalDate endDate   = startDate.plusMonths(1);

            Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant end   = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant();

            page = paymentRepository.findByCreatedAtBetween(start, end, pageable);
        } else if (StringUtils.hasText(status)) {
            page = paymentRepository.findByStatus(status.trim().toUpperCase(), pageable);
        } else {
            page = paymentRepository.findAll(pageable);
        }

        List<PaymentAdminListResponse> content = page.getContent().stream()
                .map(p -> {
                    User u = p.getUser();
                    String fullName = (u.getFirstName() != null ? u.getFirstName() + " " : "") +
                            (u.getLastName() != null ? u.getLastName() : "");

                    SubscriptionTier tier = p.getTier();

                    return PaymentAdminListResponse.builder()
                            .id(p.getId())
                            .userId(p.getUserId())
                            .email(u.getEmail())
                            .fullName(fullName.trim().isEmpty() ? null : fullName.trim())
                            .tierId(tier != null ? tier.getId() : null)
                            .tierName(tier != null ? tier.getName() : null)
                            .amount(p.getAmount())
                            .orderInfo(p.getOrderInfo())
                            .status(p.getStatus())
                            .createdAt(p.getCreatedAt())
                            .paidAt(p.getPaidAt())
                            .build();
                })
                .collect(Collectors.toList());

        log.info("Admin fetched {} payments (page {}, size {}, total {})",
                content.size(), page.getNumber(), page.getSize(), page.getTotalElements());

        return PagePaymentAdminResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    @Override
    @Transactional
    public List<SchoolSubscriptionTokenStatusResponse> getSchoolTokenStatus(
            UserPrincipal currentUser, boolean activeOnly) {

        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission to view school token status");
        }

        List<SchoolSubscription> subscriptions;
        if (activeOnly) {
            subscriptions = schoolSubscriptionRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        } else {
            subscriptions = schoolSubscriptionRepository.findAllByOrderByCreatedAtDesc();
        }

        return subscriptions.stream()
                .map(sub -> {
                    School school = sub.getSchool();
                    int used = sub.getSchoolTokenPool() - sub.getSchoolTokenRemaining();

                    return SchoolSubscriptionTokenStatusResponse.builder()
                            .id(sub.getId())
                            .schoolId(school.getId())
                            .schoolName(school.getName())
                            .schoolTokenPool(sub.getSchoolTokenPool())
                            .schoolTokenRemaining(sub.getSchoolTokenRemaining())
                            .tokensUsed(used < 0 ? 0 : used)
                            .quotaResetDate(sub.getQuotaResetDate())
                            .isActive(sub.getIsActive())
                            .endDate(sub.getEndDate())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PageTeacherTokenUsageLogResponse listAllTeacherTokenUsage(
            UserPrincipal currentUser, Pageable pageable) {

        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission to view token usage logs");
        }

        Page<TeacherTokenUsageLog> page = teacherTokenUsageLogRepository
                .findAllByOrderByUsedAtDesc(pageable);

        return mapToPageResponse(page);
    }

    @Override
    @Transactional
    public PageTeacherTokenUsageLogResponse listTokenUsageBySubscription(
            UserPrincipal currentUser, UUID subscriptionId, Pageable pageable) {

        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission to view token usage logs");
        }

        Page<TeacherTokenUsageLog> page = teacherTokenUsageLogRepository
                .findBySchoolSubscriptionIdOrderByUsedAtDesc(subscriptionId, pageable);

        return mapToPageResponse(page);
    }

    @Override
    @Transactional
    public List<TeacherTokenMonthlyUsageResponse> getMonthlyTokenUsageSummary(
            UserPrincipal currentUser) {

        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("You do not have permission to view token usage summary");
        }

        // You need this query in the repository
        List<Object[]> raw = teacherTokenUsageLogRepository.getMonthlyUsageSummaryRaw();

        return raw.stream()
                .map(row -> {
                    int year = ((Number) row[0]).intValue();
                    int month = ((Number) row[1]).intValue();
                    long totalTokens = ((Number) row[2]).longValue();
                    long entryCount = ((Number) row[3]).longValue();
                    long uniqueUsers = ((Number) row[4]).longValue();

                    return TeacherTokenMonthlyUsageResponse.builder()
                            .year(year)
                            .month(month)
                            .monthName(Month.of(month).name())
                            .totalTokensUsed(totalTokens)
                            .usageCount(entryCount)
                            .uniqueTeachers(uniqueUsers)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // Private helper (reuse for both list endpoints)
    private PageTeacherTokenUsageLogResponse mapToPageResponse(Page<TeacherTokenUsageLog> page) {
        List<TeacherTokenUsageLogResponse> content = page.getContent().stream()
                .map(log -> {
                    User teacher = log.getUser();
                    String fullName = (teacher.getFirstName() != null ? teacher.getFirstName() + " " : "") +
                            (teacher.getLastName() != null ? teacher.getLastName() : "");

                    return TeacherTokenUsageLogResponse.builder()
                            .id(log.getId())
                            .userId(log.getUserId())
                            .schoolSubscriptionId(log.getSchoolSubscriptionId())
                            .subscriptionTierId(log.getSubscriptionTierId())
                            .tokensUsed(log.getTokensUsed())
                            .usedAt(log.getUsedAt())
                            .build();
                })
                .collect(Collectors.toList());

        log.info("Admin fetched {} token usage logs (page {}, size {}, total {})",
                content.size(), page.getNumber(), page.getSize(), page.getTotalElements());

        return PageTeacherTokenUsageLogResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }

    @Transactional
    @Override
    public PagePromptScoreResponse getPromptsWithScores(Pageable pageable) {

        Page<PromptScoreResponse> pageResult =
                promptScoreRepository.findPromptsWithOverallScore(pageable);

        return new PagePromptScoreResponse(
                pageResult.getContent(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getNumber(),
                pageResult.getSize()
        );
    }

    //========================================================
    private DetailPromptResponse buildPromptResponse(Prompt prompt) {
        List<Tag> tags = promptTagRepository.findByPromptId(prompt.getId()).stream()
                .map(PromptTag::getTag)
                .toList();
        String userName = prompt.getUser() != null
                ? prompt.getUser().getFirstName() + " " + prompt.getUser().getLastName()
                : "Unknown";
        String collectionName = prompt.getCollection() != null
                ? prompt.getCollection().getName()
                : null;

        return DetailPromptResponse.builder()
                .id(prompt.getId())
                .ownerId(prompt.getUserId())
                .title(prompt.getTitle())
                .instruction(prompt.getInstruction())
                .description(prompt.getDescription())
                .context(prompt.getContext())
                .instruction(prompt.getInstruction())
                .inputExample(prompt.getInputExample())
                .outputFormat(prompt.getOutputFormat())
                .constraints(prompt.getConstraints())
                .visibility(prompt.getVisibility())
                .fullName(userName)
                .collectionName(collectionName)
                .tags(tags.stream()
                        .map(tag -> TagDTO.builder()
                                .type(tag.getType())
                                .value(tag.getValue())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(prompt.getCreatedAt())
                .updatedAt(prompt.getUpdatedAt())
                .build();
    }
}
