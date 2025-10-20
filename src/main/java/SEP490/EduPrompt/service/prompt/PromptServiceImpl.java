package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptCollectionRequest;
import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptMetadataRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptVisibilityRequest;
import SEP490.EduPrompt.dto.response.prompt.PaginatedPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptResponse;
import SEP490.EduPrompt.dto.response.prompt.TagDTO;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final PromptRepository promptRepository;
    private final CollectionRepository collectionRepository;
    private final GroupMemberRepository groupMemberRepository;
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
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

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
            throw new IllegalStateException("Cannot add prompt to deleted collection");
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
    public PaginatedPromptResponse getPrivatePrompts(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId) {
        // For PRIVATE, only the current user can access their own prompts
        UUID targetUserId = userId != null ? userId : currentUser.getUserId();
        if (!currentUser.getUserId().equals(targetUserId) && !permissionService.isAdmin(currentUser)) {
            throw new AccessDeniedException("Cannot access private prompts of another user");
        }
        return getPromptsByVisibility(Visibility.PRIVATE.name(), currentUser, pageable, targetUserId, collectionId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedPromptResponse getSchoolPrompts(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId) {
        // Check if user has a school affiliation
        if (currentUser.getSchoolId() == null) {
            throw new AccessDeniedException("User must have a school affiliation to access school prompts");
        }
        return getPromptsByVisibility(Visibility.SCHOOL.name(), currentUser, pageable, userId, collectionId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedPromptResponse getGroupPrompts(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId) {
        // Use group membership-aware query
        if (userId != null || collectionId != null) {
            // If filters are provided, fall back to standard query and filter in memory
            return getPromptsByVisibility(Visibility.GROUP.name(), currentUser, pageable, userId, collectionId);
        }
        Page<Prompt> promptPage = promptRepository.findGroupPromptsByUserId(currentUser.getUserId(), pageable);
        return mapToPaginatedResponse(promptPage, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedPromptResponse getPublicPrompts(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId) {
        return getPromptsByVisibility(Visibility.PUBLIC.name(), currentUser, pageable, userId, collectionId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedPromptResponse getPromptsByCreatedAtAsc(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId) {
        // Validate userId permission
        if (userId != null && !currentUser.getUserId().equals(userId) && !permissionService.isAdmin(currentUser)) {
            throw new AccessDeniedException("Cannot access prompts of another user");
        }
        // Validate collectionId permission
        if (collectionId != null && !permissionService.canAccessCollection(currentUser, collectionId)) {
            throw new AccessDeniedException("Cannot access prompts in this collection");
        }

        Page<Prompt> promptPage;
        //Checks if both userId and collectionId parameters are provided
        if (userId != null && collectionId != null) {
            promptPage = promptRepository.findByUserIdAndCollectionIdAndIsDeletedFalse(userId, collectionId, pageable);

            //If only userId is provided (and collectionId is null),
        } else if (userId != null) {
            promptPage = promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable);

            //If only collectionId is provided (and userId is null)
        } else if (collectionId != null) {
            promptPage = promptRepository.findByCollectionIdAndIsDeletedFalse(collectionId, pageable);

            //If none are provided
        } else {
            promptPage = promptRepository.findByIsDeletedFalseOrderByCreatedAtAsc(pageable);
        }
        return mapToPaginatedResponse(promptPage, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedPromptResponse getPromptsByUpdatedAtAsc(UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId) {
        // Validate userId permission
        if (userId != null && !currentUser.getUserId().equals(userId) && !permissionService.isAdmin(currentUser)) {
            throw new AccessDeniedException("Cannot access prompts of another user");
        }
        // Validate collectionId permission
        if (collectionId != null && !permissionService.canAccessCollection(currentUser, collectionId)) {
            throw new AccessDeniedException("Cannot access prompts in this collection");
        }

        Page<Prompt> promptPage;
        if (userId != null && collectionId != null) {
            promptPage = promptRepository.findByUserIdAndCollectionIdAndIsDeletedFalse(userId, collectionId, pageable);
        } else if (userId != null) {
            promptPage = promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable);
        } else if (collectionId != null) {
            promptPage = promptRepository.findByCollectionIdAndIsDeletedFalse(collectionId, pageable);
        } else {
            promptPage = promptRepository.findByIsDeletedFalseOrderByUpdatedAtAsc(pageable);
        }
        return mapToPaginatedResponse(promptPage, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public PaginatedPromptResponse getPromptsByCollectionId(UserPrincipal currentUser, Pageable pageable, UUID collectionId) {
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

        // Update metadata fields
        prompt.setTitle(request.getTitle());
        prompt.setDescription(request.getDescription());
        prompt.setInstruction(request.getInstruction());
        prompt.setContext(request.getContext());
        prompt.setInputExample(request.getInputExample());
        prompt.setOutputFormat(request.getOutputFormat());
        prompt.setConstraints(request.getConstraints());
        prompt.setUpdatedAt(Instant.now());
        prompt.setUpdatedBy(currentUser.getUserId());

        // Handle tags
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            // Validate all tag IDs exist
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            if (tags.size() != request.getTagIds().size()) {
                throw new ResourceNotFoundException("One or more tags not found!!");
            }

            // Remove existing PromptTag entries
            promptTagRepository.deleteByPromptId(promptId);

            // Create new PromptTag entries
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
        } else {
            // If no tags provided, remove all existing tags
            promptTagRepository.deleteByPromptId(promptId);
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
                .orElseThrow(() -> new EntityNotFoundException("Prompt not found with ID: " + promptId));

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
                throw new IllegalArgumentException("GROUP visibility requires a collection");
            }
            if (request.getCollectionId() != null) {
                // Move standalone prompt to a collection
                collection = collectionRepository.findById(request.getCollectionId())
                        .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + request.getCollectionId()));
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
                throw new IllegalArgumentException("Current collection must be associated with a group for GROUP visibility");
            } else if (!permissionService.isGroupMember(currentUser, collection.getGroup().getId())) {
                throw new AccessDeniedException("You must be a member of the group to set GROUP visibility");
            }
            // Validate collection visibility only if not removing
            if (collection != null) {
                permissionService.validateCollectionVisibility(collection, newVisibility);
            }
        } else if (newVisibility.equals(Visibility.SCHOOL.name())) {
            if (currentUser.getSchoolId() == null) {
                throw new IllegalArgumentException("User must have a school affiliation for SCHOOL visibility");
            }
            if (collection != null) {
                permissionService.validateCollectionVisibility(collection, newVisibility);
            }
            if (request.getCollectionId() != null) {
                collection = collectionRepository.findById(request.getCollectionId())
                        .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + request.getCollectionId()));
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
                        .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + request.getCollectionId()));
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


    //Helper method function
    private PaginatedPromptResponse getPromptsByVisibility(String visibility, UserPrincipal currentUser, Pageable pageable, UUID userId, UUID collectionId) {
        // Validate filters
        if (userId != null && !currentUser.getUserId().equals(userId) && !permissionService.isAdmin(currentUser)) {
            throw new AccessDeniedException("Cannot access prompts of another user");
        }
        if (collectionId != null && !permissionService.canAccessCollection(currentUser, collectionId)) {
            throw new AccessDeniedException("Cannot access prompts in this collection");
        }

        Page<Prompt> promptPage;
        if (userId != null && collectionId != null) {
            promptPage = promptRepository.findByVisibilityAndUserIdAndCollectionIdAndIsDeletedFalse(visibility, userId, collectionId, pageable);
        } else if (userId != null) {
            promptPage = promptRepository.findByVisibilityAndUserIdAndIsDeletedFalse(visibility, userId, pageable);
        } else if (collectionId != null) {
            promptPage = promptRepository.findByVisibilityAndCollectionIdAndIsDeletedFalse(visibility, collectionId, pageable);
        } else if (visibility.equals(Visibility.PRIVATE.name())) {
            // For PRIVATE, only fetch current user's prompts
            promptPage = promptRepository.findByVisibilityAndIsDeletedFalseAndUserId(visibility, currentUser.getUserId(), pageable);
        } else {
            promptPage = promptRepository.findByVisibilityAndIsDeletedFalse(visibility, pageable);
        }
        return mapToPaginatedResponse(promptPage, currentUser);
    }

    private PaginatedPromptResponse mapToPaginatedResponse(Page<Prompt> promptPage, UserPrincipal currentUser) {
        List<PromptResponse> promptResponses = promptPage.getContent().stream()
                .filter(prompt -> permissionService.canAccessPrompt(prompt, currentUser))
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
                            .instruction(prompt.getInstruction())
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
                .instruction(prompt.getInstruction())
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
