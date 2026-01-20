package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.*;
import SEP490.EduPrompt.dto.response.prompt.*;
import SEP490.EduPrompt.enums.GroupStatus;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.client.QuotaExceededException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.Collection;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final PromptRepository promptRepository;
    private final PromptViewLogRepository promptViewLogRepository;
    private final CollectionRepository collectionRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final SchoolRepository schoolRepository;
    private final TagRepository tagRepository;
    private final PromptTagRepository promptTagRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final PromptVersionRepository promptVersionRepository;
    private final PromptVersionService promptVersionService;

    @Value("${share_url}")
    private String shareUrl;

    // ======================================================================//
    // ==========================CREATE PROMPT===============================//
    @Override
    @Transactional
    public DetailPromptResponse createStandalonePrompt(CreatePromptRequest dto, UserPrincipal currentUser) {
        // Fetch User entity
        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Optional<UserQuota> userQuotaOptional = userQuotaRepository.findByUserId(currentUser.getUserId());
        UserQuota userQuota;
        if (userQuotaOptional.isPresent()) {
            userQuota = userQuotaOptional.get();
        } else {
            throw new ResourceNotFoundException("User not register a subscription yet");
        }
        // Permission check
        if (!permissionService.canCreatePrompt(currentUser)) {
            throw new AccessDeniedException("You do not have permission to create a prompt");
        }

        if (userQuota.getPromptActionRemaining() <= 0) {
            throw new QuotaExceededException(QuotaType.INDIVIDUAL, userQuota.getQuotaResetDate(),
                    userQuota.getPromptActionRemaining());
        }

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
        userQuota.setPromptActionRemaining(userQuota.getPromptActionRemaining() - 1);
        userQuotaRepository.save(userQuota);

        // Build response
        return buildPromptResponse(savedPrompt);
    }

    @Override
    @Transactional
    public DetailPromptResponse createPromptInCollection(CreatePromptCollectionRequest dto, UserPrincipal currentUser) {
        // Fetch User entity
        User user = userRepository.getReferenceById(currentUser.getUserId());

        Optional<UserQuota> userQuotaOptional = userQuotaRepository.findByUserId(currentUser.getUserId());
        UserQuota userQuota;
        if (userQuotaOptional.isPresent()) {
            userQuota = userQuotaOptional.get();
        } else {
            throw new ResourceNotFoundException("User Subscription was not available");
        }

        // Permission check
        if (!permissionService.canCreatePrompt(currentUser)) {
            throw new AccessDeniedException("You do not have permission to create a prompt");
        }

        if (userQuota.getPromptActionRemaining() <= 0) {
            throw new QuotaExceededException(QuotaType.INDIVIDUAL, userQuota.getQuotaResetDate(),
                    userQuota.getPromptActionRemaining());
        }

        // Validate and default visibility
        String promptVisibility = Visibility.parseVisibility(dto.getVisibility()).name();

        // Fetch Collection
        Collection collection = collectionRepository.findByIdAndUserId(dto.getCollectionId(), currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found or not owned by user"));

        if (collection.getIsDeleted()) {
            throw new InvalidActionException("Cannot add prompt to deleted collection");
        }

        // Validate visibility rules
        // Collection Visibility is the highest priority (Prompt must follow collection
        // Visibility)
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

        userQuota.setPromptActionRemaining(userQuota.getPromptActionRemaining() - 1);
        userQuotaRepository.save(userQuota);

        // Build response
        return buildPromptResponse(savedPrompt);
    }

    // ======================================================================//
    // ============================GET PROMPT================================//
    @Override
    @Transactional(readOnly = true)
    public PaginatedDetailPromptResponse getMyPrompts(UserPrincipal currentUser, Pageable pageable) {
        // Fetch prompts for the current user
        Page<Prompt> promptPage = promptRepository.findByUserIdAndIsDeletedFalse(currentUser.getUserId(), pageable);

        // Batch fetch tags for all prompts
        List<UUID> promptIds = promptPage.getContent().stream().map(Prompt::getId).toList();
        List<PromptTag> allPromptTags = promptTagRepository.findByPromptIdIn(promptIds);

        // Group tags by prompt ID
        Map<UUID, List<Tag>> tagsByPromptId = allPromptTags.stream()
                .collect(Collectors.groupingBy(
                        pt -> pt.getPrompt().getId(),
                        Collectors.mapping(PromptTag::getTag, Collectors.toList())));

        // Build response
        List<DetailPromptResponse> detailPromptRespons = promptPage.getContent().stream()
                .map(prompt -> {
                    List<Tag> tags = tagsByPromptId.getOrDefault(prompt.getId(), Collections.emptyList());

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
                })
                .collect(Collectors.toList());

        return PaginatedDetailPromptResponse.builder()
                .content(detailPromptRespons)
                .page(promptPage.getNumber())
                .size(promptPage.getSize())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedPromptResponse getNonPrivatePrompts(UserPrincipal currentUser, Pageable pageable) {
        Specification<Prompt> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user", JoinType.LEFT);
                root.fetch("collection", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("visibility").in(Visibility.PUBLIC.name(), Visibility.SCHOOL.name(),
                    Visibility.GROUP.name()));
            predicates.add(cb.equal(root.get("isDeleted"), false));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Prompt> promptPage = promptRepository.findAll(spec, pageable);
        List<PromptResponse> promptResponses = promptPage.getContent().stream()
                .filter(prompt -> permissionService.canFilterPrompt(prompt, currentUser))
                .map(this::buildGetPromptResponse)
                .collect(Collectors.toList());

        return PaginatedPromptResponse.builder()
                .content(promptResponses)
                .page(promptPage.getNumber())
                .size(promptPage.getSize())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public PaginatedPromptResponse getPromptsByUserId(UserPrincipal currentUser, Pageable pageable, UUID userId) {
        if (userId == null) {
            throw new InvalidInputException("User ID must not be null");
        }
        // Only allow accessing own prompts or admin access
        if (!currentUser.getUserId().equals(userId) && !permissionService.isAdmin(currentUser)) {
            throw new AccessDeniedException("Cannot access prompts of another user");
        }
        Page<Prompt> promptPage = promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable);
        return mapToPaginatedResponse(promptPage, currentUser);
    }

    @Override
    @Transactional
    public PaginatedPromptResponse getPromptsByCollectionId(UserPrincipal currentUser, Pageable pageable,
                                                            UUID collectionId) {
        if (collectionId == null) {
            throw new InvalidInputException("Collection ID must not be null");
        }
        // Check collection access
        if (!permissionService.canAccessCollection(currentUser, collectionId)) {
            throw new AccessDeniedException("Cannot access prompts in this collection");
        }
        Page<Prompt> promptPage = promptRepository.findByCollectionIdAndIsDeletedFalse(collectionId, pageable);
        return mapToPaginatedResponse(promptPage, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedPromptResponse filterPrompts(PromptFilterRequest request,
                                                 UserPrincipal currentUser,
                                                 Pageable pageable) {

        // VALIDATION – runs BEFORE any DB call
        validateFilterRequest(request, currentUser);

        // BUILD SPECIFICATION – pure OR logic
        Specification<Prompt> spec = buildCorrectSpecification(request, currentUser);

        // EXECUTE – single paginated query
        Page<Prompt> promptPage = promptRepository.findAll(spec, pageable);

        // MAP
        List<PromptResponse> responses = promptPage.getContent().stream()
                .map(this::buildGetPromptResponse)
                .toList();

        return PaginatedPromptResponse.builder()
                .content(responses)
                .page(promptPage.getNumber())
                .size(promptPage.getSize())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public DetailPromptResponse getPromptById(UUID promptId, UserPrincipal currentUser) {
        // Fetch prompt
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found with ID: " + promptId));

        // Check if prompt is deleted and user has permission to view deleted prompts
        if (prompt.getIsDeleted() != null && prompt.getIsDeleted() && !permissionService.isSystemAdmin(currentUser)) {
            throw new ResourceNotFoundException("Prompt not found or has been deleted");
        }

        // Check visibility and permissions
        permissionService.validatePromptAccess(prompt, currentUser);

        // Build and return response with only requested fields
        return buildPromptResponse(prompt);
    }

    // ======================================================================//
    // ==========================UPDATE PROMPT===============================//
    @Override
    @Transactional
    public DetailPromptResponse updatePromptMetadata(UUID promptId, UpdatePromptMetadataRequest request,
                                                     UserPrincipal currentUser) {
        // Fetch prompt
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found!!"));

        // Permission check
        if (!permissionService.canEditPrompt(currentUser, prompt)) {
            throw new AccessDeniedException("You do not have permission to edit this prompt!!");
        }

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

        // Permission check
        if (!permissionService.canEditPrompt(currentUser, prompt)) {
            throw new AccessDeniedException("You do not have permission to edit this prompt");
        }

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
                if (collection.getGroupId() == null) {
                    throw new IllegalArgumentException(
                            "Collection must be associated with a group for GROUP visibility");
                }
                if (!permissionService.canEditCollection(currentUser, collection)) {
                    throw new AccessDeniedException("You do not have permission to add prompts to this collection");
                }
                if (!permissionService.isGroupMember(currentUser, collection.getGroupId())) {
                    throw new AccessDeniedException("You must be a member of the group to set GROUP visibility");
                }
                prompt.setCollection(collection);
            } else if (collection != null && collection.getGroupId() == null) {
                throw new InvalidActionException(
                        "Current collection must be associated with a group for GROUP visibility");
            } else if (collection != null && !permissionService.isGroupMember(currentUser, collection.getGroupId())) {
                throw new AccessDeniedException("You must be a member of the group to set GROUP visibility");
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
                if (!permissionService.canEditCollection(currentUser, collection)) {
                    throw new AccessDeniedException("You do not have permission to add prompts to this collection");
                }
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
                if (!permissionService.canEditCollection(currentUser, collection)) {
                    throw new AccessDeniedException("You do not have permission to add prompts to this collection");
                }
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

    // ======================================================================//
    // ========================SOFT DELETE PROMPT============================//
    @Override
    @Transactional
    public void softDeletePrompt(UUID promptId, UserPrincipal currentUser) {
        // Fetch prompt
        log.info("User with id {} attempt to delete prompt with id {}", currentUser.getUserId(), promptId);
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found with ID: " + promptId));

        // Prevent deletion of already deleted prompts unless SYSTEM_ADMIN
        if (prompt.getIsDeleted() != null && prompt.getIsDeleted() && !permissionService.isSystemAdmin(currentUser)) {
            throw new ResourceNotFoundException("Prompt not found or already deleted");
        }

        // Check permission
        if (!permissionService.canEditPrompt(currentUser, prompt)) {
            throw new AccessDeniedException("You do not have permission to delete this prompt");
        }

        // Mark as deleted
        prompt.setIsDeleted(true);
        prompt.setDeletedAt(Instant.now());

        // Save changes
        promptRepository.save(prompt);
        log.info("Prompt with id {} has been successfully deleted", promptId);
    }

    // ======================================================================//
    // ===========================PROMPT VIEW LOG============================//

    @Override
    public boolean hasUserViewedPrompt(UserPrincipal currentUser, UUID promptId) {
        return promptViewLogRepository.findPromptViewLogByPromptIdAndUserId(promptId, currentUser.getUserId())
                .isPresent();
    }

    @Transactional(readOnly = true)
    @Override
    public List<ViewedPromptItem> hasUserViewedPrompts(UserPrincipal principal, HasViewedPromptRequest request) {
        UUID userId = principal.getUserId();

        Set<UUID> viewedPromptIds = promptViewLogRepository.findAllByUserId(userId).stream()
                .map(PromptViewLog::getPrompt)
                .map(Prompt::getId)
                .collect(Collectors.toSet());

        return request.promptIds().stream()
                .map(id -> new ViewedPromptItem(id, viewedPromptIds.contains(id)))
                .toList();
    }

    @Override
    public PromptViewLogResponse logPromptView(UserPrincipal currentUser, CreatePromptViewLogRequest request) {
        User user = userRepository.getReferenceById(currentUser.getUserId());
        Prompt prompt = promptRepository.findById(request.promptId())
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found with ID: " + request.promptId()));

        Optional<UserQuota> userQuotaOptional = userQuotaRepository.findByUserId(currentUser.getUserId());
        UserQuota userQuota;
        if (userQuotaOptional.isPresent()) {
            userQuota = userQuotaOptional.get();
        } else {
            throw new ResourceNotFoundException("User not register a subscription yet");
        }

        PromptViewLog viewLog = promptViewLogRepository
                .findPromptViewLogByPromptIdAndUserId(request.promptId(), currentUser.getUserId())
                .orElseGet(() -> {
                    if (userQuota.getPromptUnlockRemaining() <= 0) {
                        throw new QuotaExceededException(QuotaType.INDIVIDUAL, userQuota.getQuotaResetDate(),
                                userQuota.getPromptUnlockRemaining());
                    }
                    PromptViewLog newLog = PromptViewLog.builder()
                            .user(user)
                            .prompt(prompt)
                            .createdAt(Instant.now())
                            .build();
                    userQuota.setPromptUnlockRemaining(userQuota.getPromptUnlockRemaining() - 1);
                    userQuotaRepository.save(userQuota);
                    return promptViewLogRepository.save(newLog);
                });
        return toResponse(viewLog);
    }

    @Transactional
    @Override
    public boolean logPromptViews(UserPrincipal principal, PromptViewLogCreateRequest request) {
        UUID userId = principal.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean allSuccess = true;

        for (UUID promptId : request.promptIds()) {
            try {
                Prompt prompt = promptRepository.findById(promptId)
                        .orElseThrow(() -> new ResourceNotFoundException("Prompt not found"));

                permissionService.validatePromptAccess(prompt, principal);

                Optional<PromptViewLog> existingLog = promptViewLogRepository.findByUserIdAndPromptId(userId, promptId);

                if (existingLog.isEmpty()) {
                    PromptViewLog newLog = PromptViewLog.builder()
                            .user(user)
                            .prompt(prompt)
                            .createdAt(Instant.now())
                            .build();
                    promptViewLogRepository.save(newLog);
                }
            } catch (Exception e) {
                allSuccess = false;
                log.error("Failed to log view for prompt {} by user {}: {}", promptId, userId, e.getMessage());
            }
        }

        return allSuccess;
    }

    // ======================================================================//
    // ==========================PROMPT VERSIONING===========================//
    @Override
    @Transactional
    public PromptVersionResponse createPromptVersion(UUID promptId, CreatePromptVersionRequest request,
                                                     UserPrincipal currentUser) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found with ID: " + promptId));

        if (!permissionService.canEditPrompt(currentUser, prompt)) {
            throw new AccessDeniedException("You do not have permission to edit this prompt");
        }

        PromptVersion savedVersion = promptVersionService.createVersion(prompt, request, currentUser.getUserId(), null);

        return toPromptVersionResponse(savedVersion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptVersionResponse> getPromptVersions(UUID promptId, UserPrincipal currentUser) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found with ID: " + promptId));

        if (!permissionService.canAccessPrompt(prompt, currentUser)) {
            throw new AccessDeniedException("You do not have permission to view this prompt");
        }

        List<PromptVersion> versions = promptVersionRepository.findByPromptIdOrderByVersionNumberDesc(promptId);
        return versions.stream()
                .map(this::toPromptVersionResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DetailPromptResponse rollbackToVersion(UUID promptId, UUID versionId, UserPrincipal currentUser) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found with ID: " + promptId));

        if (!permissionService.canEditPrompt(currentUser, prompt)) {
            throw new AccessDeniedException("You do not have permission to edit this prompt");
        }

        PromptVersion targetVersion = promptVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found with ID: " + versionId));

        if (!targetVersion.getPrompt().getId().equals(promptId)) {
            throw new InvalidActionException("Version does not belong to this prompt");
        }

        // Update prompt content from version
        prompt.setInstruction(targetVersion.getInstruction());
        prompt.setContext(targetVersion.getContext());
        prompt.setInputExample(targetVersion.getInputExample());
        prompt.setOutputFormat(targetVersion.getOutputFormat());
        prompt.setConstraints(targetVersion.getConstraints());

        // Update metadata
        prompt.setCurrentVersion(targetVersion);
        prompt.setUpdatedAt(Instant.now());
        prompt.setUpdatedBy(currentUser.getUserId());

        Prompt updatedPrompt = promptRepository.save(prompt);

        return buildPromptResponse(updatedPrompt);
    }

    // ======================================================================//
    // ============================PROMPT SHARING============================//
    @Override
    public String sharePrompt(UUID promptId, UserPrincipal currentUser) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found"));

        // Ownership check
        if (!prompt.getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("You do not own this prompt");
        }

        // Generate token if none exists
        if (prompt.getShareToken() == null) {
            prompt.setShareToken(UUID.randomUUID());
            prompt.setUpdatedAt(Instant.now());
            prompt.setUpdatedBy(currentUser.getUserId());
            promptRepository.save(prompt);
        }

        // Generate shareable link
        return shareUrl + prompt.getId() + "?token=" + prompt.getShareToken();
    }

    @Override
    public PromptShareResponse getSharedPrompt(UUID promptId, UUID token) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found"));

        if (prompt.getIsDeleted()) {
            throw new ResourceNotFoundException("Prompt not found");
        }

        if (token == null || !token.equals(prompt.getShareToken())) {
            throw new AccessDeniedException("Access denied");
        }
        return PromptShareResponse.builder()
                .id(prompt.getId())
                .title(prompt.getTitle())
                .description(prompt.getDescription())
                .instruction(prompt.getInstruction())
                .context(prompt.getContext())
                .inputExample(prompt.getInputExample())
                .outputFormat(prompt.getOutputFormat())
                .constraints(prompt.getConstraints())
                .shareToken(prompt.getShareToken())
                .build();
    }

    @Override
    public void revokeShare(UUID promptId, UserPrincipal currentUser) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found"));

        if (!permissionService.canAccessPrompt(prompt, currentUser)) {
            throw new AccessDeniedException("You do not own this prompt");
        }

        prompt.setShareToken(null);
        prompt.setUpdatedAt(Instant.now());
        prompt.setUpdatedBy(currentUser.getUserId());
        promptRepository.save(prompt);
    }

    //

    @Transactional(readOnly = true)
    @Override
    public PaginatedGroupSharedPromptResponse getGroupSharedPrompts(UserPrincipal currentUser, Pageable pageable) {
        UUID currentUserId = currentUser.getUserId();

        List<GroupMember> memberships = groupMemberRepository.findByUserIdAndStatus(currentUserId, GroupStatus.ACTIVE.name());
        Set<UUID> groupIds = memberships.stream()
                .map(GroupMember::getGroup)
                .map(Group::getId)
                .collect(Collectors.toSet());

        if (groupIds.isEmpty()) {
            throw new ResourceNotFoundException("User not in any group!!");
        }

        Page<Prompt> promptPage = promptRepository.findGroupSharedPrompts(groupIds, Visibility.GROUP.name(), pageable);

        List<GroupSharedPromptResponse> content = promptPage.getContent().stream()
                .map(prompt -> {
                    String userName = prompt.getUser() != null
                            ? prompt.getUser().getFirstName() + " " + prompt.getUser().getLastName()
                            : "Unknown";
                    UUID collectionId = prompt.getCollectionId();
                    UUID groupId = prompt.getCollection() != null ? prompt.getCollection().getGroupId() : null;

                    return GroupSharedPromptResponse.builder()
                            .id(prompt.getId())
                            .title(prompt.getTitle())
                            .description(prompt.getDescription())
                            .outputFormat(prompt.getOutputFormat())
                            .visibility(prompt.getVisibility())
                            .fullName(userName)
                            .collectionId(collectionId)
                            .groupId(groupId)
                            .createdAt(prompt.getCreatedAt())
                            .updatedAt(prompt.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        // Step 4: Build paginated response, matching existing mapToPaginatedResponse style
        return PaginatedGroupSharedPromptResponse.builder()
                .content(content)
                .page(promptPage.getNumber())
                .size(promptPage.getSize())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

    @Transactional
    @Override
    public AddPromptToCollectionResponse addPromptToCollection(AddPromptToCollectionRequest request, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();

        // Fetch the existing prompt
        Prompt prompt = promptRepository.findById(request.promptId())
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found"));

        // Check ownership: User can only add their own prompt
        if (!prompt.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You can only add your own prompts to collections");
        }

        // Check if prompt is already in a collection
        if (prompt.getCollectionId() != null) {
            throw new InvalidActionException("Prompt is already assigned to a collection. Cannot add to another.");
        }

        // Check if prompt is deleted
        if (prompt.getIsDeleted()) {
            throw new InvalidActionException("Cannot add a deleted prompt to a collection");
        }

        // Permission check for creating/editing prompts
        if (!permissionService.canCreatePrompt(currentUser)) {
            throw new AccessDeniedException("You do not have permission to modify prompts");
        }

        // Fetch the collection
        Collection collection = collectionRepository.findById(request.collectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));

        // Check collection ownership: User can only add to their own collection
        if (!collection.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You can only add prompts to your own collections");
        }

        // Check if collection is deleted
        if (collection.getIsDeleted()) {
            throw new InvalidActionException("Cannot add prompt to a deleted collection");
        }

        String newVisibility = collection.getVisibility();

        permissionService.validateCollectionVisibility(collection, newVisibility);
        
        if (newVisibility.equals(Visibility.GROUP.name()) && collection.getGroup() != null) {
            Group group = groupRepository.findById(collection.getGroup().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
            if (!groupMemberRepository.existsByGroupIdAndUserIdAndStatus(group.getId(), currentUserId, GroupStatus.ACTIVE.name())) {
                throw new AccessDeniedException("You must be an active member of the group for GROUP visibility");
            }
        }

        if (newVisibility.equals(Visibility.SCHOOL.name()) && currentUser.getSchoolId() == null) {
            throw new InvalidInputException("User must have a school affiliation for SCHOOL visibility");
        }

        // Update prompt
        prompt.setCollection(collection);
        prompt.setVisibility(newVisibility);
        prompt.setUpdatedBy(currentUserId);
        prompt.setUpdatedAt(Instant.now());

        Prompt updatedPrompt = promptRepository.save(prompt);

        // No quota decrement as per request

        log.info("Prompt {} added to collection {} by user {}", request.promptId(), request.collectionId(), currentUserId);

        return AddPromptToCollectionResponse.builder()
                .id(updatedPrompt.getId())
                .collectionId(updatedPrompt.getCollectionId())
                .title(updatedPrompt.getTitle())
                .description(updatedPrompt.getDescription())
                .visibility(updatedPrompt.getVisibility())
                .updatedAt(updatedPrompt.getUpdatedAt())
                .build();
    }

    // Helper method function

    private PromptVersionResponse toPromptVersionResponse(PromptVersion version) {
        return PromptVersionResponse.builder()
                .id(version.getId())
                .promptId(version.getPrompt().getId())
                .instruction(version.getInstruction())
                .context(version.getContext())
                .inputExample(version.getInputExample())
                .outputFormat(version.getOutputFormat())
                .constraints(version.getConstraints())
                .editorId(version.getEditorId())
                .versionNumber(version.getVersionNumber())
                .isAiGenerated(version.getIsAiGenerated())
                .createdAt(version.getCreatedAt())
                .build();
    }

    private PromptViewLogResponse toResponse(PromptViewLog log) {
        return PromptViewLogResponse.builder()
                .id(log.getId())
                .userId(log.getUser().getId())
                .promptId(log.getPrompt().getId())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private PaginatedPromptResponse mapToPaginatedResponse(Page<Prompt> promptPage, UserPrincipal currentUser) {
        List<PromptResponse> promptResponses = promptPage.getContent().stream()
                .filter(prompt -> permissionService.canAccessPrompt(prompt, currentUser))
                .map(prompt -> {
                    String userName = prompt.getUser() != null
                            ? prompt.getUser().getFirstName() + " " + prompt.getUser().getLastName()
                            : "Unknown";
                    String collectionName = prompt.getCollection() != null
                            ? prompt.getCollection().getName()
                            : null;

                    return PromptResponse.builder()
                            .id(prompt.getId())
                            .title(prompt.getTitle())
                            .description(prompt.getDescription())
                            .outputFormat(prompt.getOutputFormat())
                            .visibility(prompt.getVisibility())
                            .fullName(userName)
                            .collectionName(collectionName)
                            .createdAt(prompt.getCreatedAt())
                            .updatedAt(prompt.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return PaginatedPromptResponse.builder()
                .content(promptResponses)
                .page(promptPage.getNumber())
                .size(promptPage.getSize())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

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

    private PromptResponse buildGetPromptResponse(Prompt prompt) {
        String userName = prompt.getUser() != null
                ? prompt.getUser().getFirstName() + " " + prompt.getUser().getLastName()
                : "Unknown";
        String collectionName = prompt.getCollection() != null
                ? prompt.getCollection().getName()
                : null;
        return PromptResponse.builder()
                .id(prompt.getId())
                .title(prompt.getTitle())
                .description(prompt.getDescription())
                .outputFormat(prompt.getOutputFormat())
                .visibility(prompt.getVisibility())
                .fullName(userName)
                .collectionName(collectionName)
                .createdAt(prompt.getCreatedAt())
                .updatedAt(prompt.getUpdatedAt())
                .build();
    }

    private void validateFilterRequest(PromptFilterRequest req, UserPrincipal user) {
        if (Boolean.TRUE.equals(req.includeDeleted()) && !permissionService.isSystemAdmin(user)) {
            throw new AccessDeniedException("Only SYSTEM_ADMIN can include deleted prompts");
        }

        if (nonBlank(req.collectionName()) && !collectionRepository.existsByNameIgnoreCase(req.collectionName())) {
            throw new ResourceNotFoundException("Collection not found: " + req.collectionName());
        }

        if (nonBlank(req.schoolName()) && !schoolRepository.existsByNameIgnoreCase(req.schoolName())) {
            throw new ResourceNotFoundException("School not found: " + req.schoolName());
        }

        if (nonBlank(req.groupName()) && !groupRepository.existsByNameIgnoreCase(req.groupName())) {
            throw new ResourceNotFoundException("Group not found: " + req.groupName());
        }

        if (req.tagTypes() != null && !req.tagTypes().isEmpty() && !allSingleLetter(req.tagTypes())) {
            Set<String> existing = tagRepository.findAllByTypeIn(req.tagTypes()).stream()
                    .map(Tag::getType)
                    .collect(Collectors.toSet());
            if (!existing.containsAll(req.tagTypes())) {
                throw new ResourceNotFoundException("One or more tag types not found");
            }
        }

        if (req.tagValues() != null && !req.tagValues().isEmpty() && !allSingleLetter(req.tagValues())) {
            Set<String> existing = tagRepository.findAllByValueIn(req.tagValues()).stream()
                    .map(Tag::getValue)
                    .collect(Collectors.toSet());
            if (!existing.containsAll(req.tagValues())) {
                throw new ResourceNotFoundException("One or more tag values not found");
            }
        }
    }

    private Specification<Prompt> buildCorrectSpecification(PromptFilterRequest req, UserPrincipal user) {
        return (root, query, cb) -> {
            List<Predicate> baseAnd = new ArrayList<>();
            List<Predicate> searchOr = new ArrayList<>();

            // --- Eager fetch (only if NOT a count query) ---
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user", JoinType.LEFT);
                root.fetch("collection", JoinType.LEFT);
                query.distinct(true);
            }

            // === BASE FILTERS (ALWAYS APPLIED) ===
            // Visibility: PUBLIC/SCHOOL/GROUP + PRIVATE (if owner)
            Expression<String> visibilityUpper = cb.upper(root.get("visibility"));
            Predicate publicVis = visibilityUpper.in(
                    Visibility.PUBLIC.name(), Visibility.SCHOOL.name(), Visibility.GROUP.name());
            Predicate privateVis = cb.and(
                    visibilityUpper.in(Visibility.PRIVATE.name()),
                    cb.equal(root.get("createdBy"), user.getUserId()));
            baseAnd.add(cb.or(publicVis, privateVis));

            // isDeleted
            boolean includeDeleted = Boolean.TRUE.equals(req.includeDeleted());
            baseAnd.add(cb.equal(root.get("isDeleted"), includeDeleted));

            // === SEARCH FILTERS (OR'd together) ===
            if (req.createdBy() != null) {
                searchOr.add(cb.equal(root.get("createdBy"), req.createdBy()));
            }
            if (nonBlank(req.title())) {
                String pattern = "%" + req.title().toLowerCase() + "%";
                searchOr.add(cb.like(cb.lower(root.get("title")), pattern));
            }
            if (nonBlank(req.collectionName())) {
                searchOr.add(cb.like(
                        cb.lower(root.get("collection").get("name")),
                        "%" + req.collectionName().toLowerCase() + "%"));
            }
            if (nonBlank(req.schoolName())) {
                Join<Prompt, User> userJoin = root.join("user", JoinType.LEFT);
                Join<User, School> schoolJoin = userJoin.join("school", JoinType.LEFT);
                searchOr.add(cb.like(cb.lower(schoolJoin.get("name")), "%" + req.schoolName().toLowerCase() + "%"));
            }
            if (nonBlank(req.groupName())) {
                Join<Prompt, Collection> collJoin = root.join("collection", JoinType.LEFT);
                Join<Collection, Group> groupJoin = collJoin.join("group", JoinType.LEFT);
                searchOr.add(cb.like(cb.lower(groupJoin.get("name")), "%" + req.groupName().toLowerCase() + "%"));
            }
            if (req.tagTypes() != null && !req.tagTypes().isEmpty()) {
                searchOr.add(buildTagPredicate(root, query, cb, "type", req.tagTypes()));
            }
            if (req.tagValues() != null && !req.tagValues().isEmpty()) {
                searchOr.add(buildTagPredicate(root, query, cb, "value", req.tagValues()));
            }

            // === COMBINE ===
            if (!searchOr.isEmpty()) {
                baseAnd.add(cb.or(searchOr.toArray(Predicate[]::new)));
                return cb.and(baseAnd.toArray(Predicate[]::new));
            } else {
                return cb.and(baseAnd.toArray(Predicate[]::new));
            }
        };
    }

    private Predicate buildTagPredicate(Root<Prompt> root,
                                        CriteriaQuery<?> query,
                                        CriteriaBuilder cb,
                                        String field, // "type" or "value"
                                        List<String> values) {

        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<PromptTag> pt = subquery.from(PromptTag.class);
        Path<String> tagPath = pt.get("tag").get(field);

        Predicate tagCond;
        if (allSingleLetter(values)) {
            Predicate[] likes = values.stream()
                    .map(v -> cb.like(cb.lower(tagPath), "%" + v.toLowerCase() + "%"))
                    .toArray(Predicate[]::new);
            tagCond = cb.or(likes);
        } else {
            tagCond = tagPath.in(values);
        }

        subquery.select(pt.get("prompt").get("id")).where(tagCond);
        return root.get("id").in(subquery);
    }

    private boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private boolean allSingleLetter(List<String> list) {
        return list != null && list.stream().allMatch(s -> s.length() == 1);
    }
}
