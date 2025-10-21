package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.*;
import SEP490.EduPrompt.dto.response.prompt.*;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final PromptRepository promptRepository;
    private final CollectionRepository collectionRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SchoolRepository schoolRepository;
    private final TagRepository tagRepository;
    private final PromptTagRepository promptTagRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @Override
    @Transactional
    public PromptResponse createStandalonePrompt(CreatePromptRequest dto, UserPrincipal currentUser) {
        // Fetch User entity
        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Permission check
        if (!permissionService.canCreatePrompt(currentUser)) {
            throw new AccessDeniedException("You do not have permission to create a prompt");
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
           If the user includes tag IDs in the request, fetch those tags from the database.
           If any of the tag IDs don’t exist, stop and throw an error —
           don’t allow the prompt to be created with invalid tags
        */
        List<Tag> tags = new ArrayList<>();
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            tags = tagRepository.findAllById(dto.getTagIds());
            if (tags.size() != dto.getTagIds().size()) {
                throw new ResourceNotFoundException("One or more tags not found");
            }
        }

        // Build Prompt
        Prompt.PromptBuilder promptBuilder = Prompt.builder()
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
                .collection(null);

        Prompt savedPrompt = promptRepository.save(promptBuilder.build());

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
    public PromptResponse createPromptInCollection(CreatePromptCollectionRequest dto, UserPrincipal currentUser) {
        // Fetch User entity
        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Permission check
        if (!permissionService.canCreatePrompt(currentUser)) {
            throw new AccessDeniedException("You do not have permission to create a prompt");
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
        // Collection Visibility is the highest priority (Prompt must follow collection Visibility)
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
        Prompt.PromptBuilder promptBuilder = Prompt.builder()
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
                .collection(collection);

        Prompt savedPrompt = promptRepository.save(promptBuilder.build());

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
    @Transactional(readOnly = true)
    public PaginatedPromptResponse getMyPrompts(UserPrincipal currentUser, Pageable pageable) {
        // Fetch prompts for the current user
        Page<Prompt> promptPage = promptRepository.findByUserIdAndIsDeletedFalse(currentUser.getUserId(), pageable);

        // Build response
        List<PromptResponse> promptResponses = promptPage.getContent().stream()
                .map(prompt -> {
                    List<Tag> tags = promptTagRepository.findByPromptId(prompt.getId()).stream()
                            .map(PromptTag::getTag)
                            .toList();

                    String userName = prompt.getUser() != null
                            ? prompt.getUser().getFirstName() + " " + prompt.getUser().getLastName()
                            : "Unknown";

                    String collectionName = prompt.getCollection() != null
                            ? prompt.getCollection().getName()
                            : null;

                    return PromptResponse.builder()
                            .title(prompt.getTitle())
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

        return PaginatedPromptResponse.builder()
                .content(promptResponses)
                .page(promptPage.getNumber())
                .size(promptPage.getSize())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public GetPaginatedPromptResponse getSchoolPrompts(UserPrincipal currentUser, Pageable pageable) {
        // Fetch prompts
        Page<Prompt> promptPage = promptRepository.findByVisibilityAndIsDeletedFalse(Visibility.SCHOOL.name(), pageable);

        // Build response
        List<GetPromptResponse> promptResponses = promptPage.getContent().stream()
                .map(prompt -> {
                    String userName = prompt.getUser() != null
                            ? prompt.getUser().getFirstName() + " " + prompt.getUser().getLastName()
                            : "Unknown";

                    String collectionName = prompt.getCollection() != null
                            ? prompt.getCollection().getName()
                            : null;

                    return GetPromptResponse.builder()
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

        return GetPaginatedPromptResponse.builder()
                .content(promptResponses)
                .page(promptPage.getNumber())
                .size(promptPage.getSize())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public GetPaginatedPromptResponse getGroupPrompts(UserPrincipal currentUser, Pageable pageable) {
        // Fetch prompts
        Page<Prompt> promptPage = promptRepository.findByVisibilityAndIsDeletedFalse(Visibility.GROUP.name(), pageable);

        // Build response
        List<GetPromptResponse> promptResponses = promptPage.getContent().stream()
                .map(prompt -> {
                    String userName = prompt.getUser() != null
                            ? prompt.getUser().getFirstName() + " " + prompt.getUser().getLastName()
                            : "Unknown";

                    String collectionName = prompt.getCollection() != null
                            ? prompt.getCollection().getName()
                            : null;

                    return GetPromptResponse.builder()
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

        return GetPaginatedPromptResponse.builder()
                .content(promptResponses)
                .page(promptPage.getNumber())
                .size(promptPage.getSize())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GetPaginatedPromptResponse getPublicPrompts(UserPrincipal currentUser, Pageable pageable) {
        return getPromptsByVisibility(Visibility.PUBLIC.name(), currentUser, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public GetPaginatedPromptResponse getPromptsByUserId(UserPrincipal currentUser, Pageable pageable, UUID userId) {
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
    @Transactional(readOnly = true)
    public GetPaginatedPromptResponse getPromptsByCollectionId(UserPrincipal currentUser, Pageable pageable, UUID collectionId) {
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
    @Transactional
    public PromptResponse updatePromptMetadata(UUID promptId, UpdatePromptMetadataRequest request, UserPrincipal currentUser) {
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

            // If tagIds are provided and not empty, validate and create new PromptTag entries
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
    public PromptResponse updatePromptVisibility(UUID promptId, UpdatePromptVisibilityRequest request, UserPrincipal currentUser) {
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
                    // If validation fails, automatically remove from collection to make it standalone
                    removeFromCollection = true;
                }
            }
        }

        if (removeFromCollection) {
            prompt.setCollection(null);
            collection = null;
        } else if (newVisibility.equals(Visibility.GROUP.name())) {
            // GROUP visibility requires a collection with a group
            if (collection == null && request.getCollectionId() == null) {
                throw new InvalidInputException("GROUP visibility requires a collection");
            }
            if (request.getCollectionId() != null) {
                // Move standalone prompt to a collection
                collection = collectionRepository.findById(request.getCollectionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Collection not found with ID: " + request.getCollectionId()));
                if (collection.getGroup() == null) {
                    throw new IllegalArgumentException("Collection must be associated with a group for GROUP visibility");
                }
                if (!permissionService.canEditCollection(currentUser, collection)) {
                    throw new AccessDeniedException("You do not have permission to add prompts to this collection");
                }
                if (!permissionService.isGroupMember(currentUser, collection.getGroup().getId())) {
                    throw new AccessDeniedException("You must be a member of the group to set GROUP visibility");
                }
                prompt.setCollection(collection);
            } else if (collection.getGroup() == null) {
                throw new InvalidActionException("Current collection must be associated with a group for GROUP visibility");
            } else if (!permissionService.isGroupMember(currentUser, collection.getGroup().getId())) {
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
                        .orElseThrow(() -> new ResourceNotFoundException("Collection not found with ID: " + request.getCollectionId()));
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
                        .orElseThrow(() -> new ResourceNotFoundException("Collection not found with ID: " + request.getCollectionId()));
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

    @Override
    @Transactional
    public void softDeletePrompt(UUID promptId, UserPrincipal currentUser) {
        // Fetch prompt
        log.info("User with id " + currentUser.getUserId() + " attempt to delete prompt with id " + promptId);
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
        log.info("Prompt with id " + promptId + " has been successfully deleted");
    }

    @Override
    @Transactional
    public PaginatedPromptResponse filterPrompts(PromptFilterRequest request, UserPrincipal currentUser, Pageable pageable) {
        // Validate inputs
        if (request.includeDeleted() != null && request.includeDeleted() && !permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("Only SYSTEM_ADMIN can include deleted prompts");
        }
        if (request.collectionName() != null && !collectionRepository.existsByNameIgnoreCase(request.collectionName())) {
            throw new ResourceNotFoundException("Collection not found with name: " + request.collectionName());
        }
        if (request.tagTypes() != null && !request.tagTypes().isEmpty()) {
            List<String> foundTagTypes = tagRepository.findAllByTypeIn(request.tagTypes()).stream()
                    .map(Tag::getType)
                    .distinct()
                    .toList();
            if (foundTagTypes.size() != request.tagTypes().size()) {
                throw new ResourceNotFoundException("One or more tag types not found");
            }
        }
        if (request.schoolName() != null && !schoolRepository.existsByNameIgnoreCase(request.schoolName())) {
            throw new ResourceNotFoundException("School not found with name: " + request.schoolName());
        }
        if (request.groupName() != null && !groupRepository.existsByNameIgnoreCase(request.groupName())) {
            throw new ResourceNotFoundException("Group not found with name: " + request.groupName());
        }

        // Build Specification
        Specification<Prompt> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Ensure related entities are fetched to avoid lazy loading
            root.fetch("user");
            root.fetch("collection", jakarta.persistence.criteria.JoinType.LEFT);

            // Filter by createdBy
            if (request.createdBy() != null) {
                predicates.add(cb.equal(root.get("createdBy"), request.createdBy()));
            }

            // Filter by collectionName
            if (request.collectionName() != null) {
                predicates.add(cb.equal(cb.lower(root.get("collection").get("name")), request.collectionName().toLowerCase()));
            }

            // Filter by tagTypes
            if (request.tagTypes() != null && !request.tagTypes().isEmpty()) {
                assert query != null;
                Subquery<UUID> subquery = query.subquery(UUID.class);
                jakarta.persistence.criteria.Root<PromptTag> promptTagRoot = subquery.from(PromptTag.class);
                subquery.select(promptTagRoot.get("prompt").get("id"))
                        .where(promptTagRoot.get("tag").get("type").in(request.tagTypes()));
                predicates.add(root.get("id").in(subquery));
            }

            // Filter by schoolName (prompt owner's school)
            if (request.schoolName() != null) {
                Join<Prompt, User> userJoin = root.join("user");
                Join<User, School> schoolJoin = userJoin.join("school");
                predicates.add(cb.equal(cb.lower(schoolJoin.get("name")), request.schoolName().toLowerCase()));
            }

            // Filter by groupName (prompt collection's group)
            if (request.groupName() != null) {
                Join<Prompt, Collection> collectionJoin = root.join("collection", jakarta.persistence.criteria.JoinType.LEFT);
                Join<Collection, Group> groupJoin = collectionJoin.join("group", jakarta.persistence.criteria.JoinType.LEFT);
                predicates.add(cb.equal(cb.lower(groupJoin.get("name")), request.groupName().toLowerCase()));
            }

            // Filter by title
            if (request.title() != null && !request.title().isBlank()) {
                String searchPattern = "%" + request.title().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), searchPattern),
                        cb.like(cb.lower(root.get("description")), searchPattern)
                ));
            }

            // Filter by isDeleted
            if (request.includeDeleted() != null && request.includeDeleted()) {
                predicates.add(cb.equal(root.get("isDeleted"), true));
            } else {
                predicates.add(cb.equal(root.get("isDeleted"), false));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Fetch prompts
        Page<Prompt> promptPage = promptRepository.findAll(spec, pageable);

        // Filter accessible prompts
        List<PromptResponse> promptResponses = promptPage.getContent().stream()
                .filter(prompt -> permissionService.canAccessPrompt(prompt, currentUser))
                .map(this::buildPromptResponse)
                .collect(Collectors.toList());

        // Build response
        return PaginatedPromptResponse.builder()
                .content(promptResponses)
                .page(promptPage.getNumber())
                .size(promptPage.getSize())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PromptResponse getPromptById(UUID promptId, UserPrincipal currentUser) {
        // Fetch prompt
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found with ID: " + promptId));

        // Check if prompt is deleted and user has permission to view deleted prompts
        if (prompt.getIsDeleted() != null && prompt.getIsDeleted() && !permissionService.isSystemAdmin(currentUser)) {
            throw new ResourceNotFoundException("Prompt not found or has been deleted");
        }

        // Check visibility and permissions
        switch (Visibility.valueOf(prompt.getVisibility())) {
            case PRIVATE:
                if (!currentUser.getUserId().equals(prompt.getCreatedBy()) && !permissionService.isAdmin(currentUser)) {
                    throw new AccessDeniedException("You do not have permission to view this private prompt");
                }
                break;
            case GROUP:
                if (prompt.getCollection() == null || prompt.getCollection().getGroup() == null) {
                    throw new ResourceNotFoundException("Group not found for this prompt");
                }
                if (!permissionService.isGroupMember(currentUser, prompt.getCollection().getGroup().getId())) {
                    throw new AccessDeniedException("You are not a member of the group associated with this prompt");
                }
                break;
            case SCHOOL:
                if (currentUser.getSchoolId() == null) {
                    throw new AccessDeniedException("You must have a school affiliation to view this prompt");
                }
                User promptOwner = userRepository.findById(prompt.getCreatedBy())
                        .orElseThrow(() -> new ResourceNotFoundException("Prompt owner not found"));
                if (!currentUser.getSchoolId().equals(promptOwner.getSchoolId())) {
                    throw new AccessDeniedException("You do not belong to the same school as the prompt owner");
                }
                break;
            case PUBLIC:
                // No additional checks needed for public prompts
                break;
            default:
                throw new InvalidInputException("Invalid visibility value: " + prompt.getVisibility());
        }

        // Build and return response with only requested fields
        return PromptResponse.builder()
                .title(prompt.getTitle())
                .description(prompt.getDescription())
                .instruction(prompt.getInstruction())
                .context(prompt.getContext())
                .inputExample(prompt.getInputExample())
                .outputFormat(prompt.getOutputFormat())
                .constraints(prompt.getConstraints())
                .visibility(prompt.getVisibility())
                .build();
    }

    //Helper method function
    private GetPaginatedPromptResponse getPromptsByVisibility(String visibility, UserPrincipal currentUser, Pageable pageable) {
        Page<Prompt> promptPage;
        if (visibility.equals(Visibility.PRIVATE.name())) {
            // For PRIVATE, only fetch current user's prompts
            promptPage = promptRepository.findByVisibilityAndIsDeletedFalseAndUserId(visibility, currentUser.getUserId(), pageable);
        } else {
            promptPage = promptRepository.findByVisibilityAndIsDeletedFalse(visibility, pageable);
        }
        return mapToPaginatedResponse(promptPage, currentUser);
    }

    private GetPaginatedPromptResponse mapToPaginatedResponse(Page<Prompt> promptPage, UserPrincipal currentUser) {
        List<GetPromptResponse> promptResponses = promptPage.getContent().stream()
                .filter(prompt -> permissionService.canAccessPrompt(prompt, currentUser))
                .map(prompt -> {
                    String userName = prompt.getUser() != null
                            ? prompt.getUser().getFirstName() + " " + prompt.getUser().getLastName()
                            : "Unknown";
                    String collectionName = prompt.getCollection() != null
                            ? prompt.getCollection().getName()
                            : null;

                    return GetPromptResponse.builder()
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

        return GetPaginatedPromptResponse.builder()
                .content(promptResponses)
                .page(promptPage.getNumber())
                .size(promptPage.getSize())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

    private PromptResponse buildPromptResponse(Prompt prompt) {
        List<Tag> tags = promptTagRepository.findByPromptId(prompt.getId()).stream()
                .map(PromptTag::getTag)
                .toList();
        String userName = prompt.getUser() != null
                ? prompt.getUser().getFirstName() + " " + prompt.getUser().getLastName()
                : "Unknown";
        String collectionName = prompt.getCollection() != null
                ? prompt.getCollection().getName()
                : null;

        return PromptResponse.builder()
                .title(prompt.getTitle())
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
    }
}
