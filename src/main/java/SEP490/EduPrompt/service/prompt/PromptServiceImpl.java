package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.response.prompt.PromptResponse;
import SEP490.EduPrompt.dto.response.prompt.TagDTO;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
                throw new EntityNotFoundException("One or more tags not found");
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
        return PromptResponse.builder()
                .id(savedPrompt.getId())
                .title(savedPrompt.getTitle())
                .description(savedPrompt.getDescription())
                .instruction(savedPrompt.getInstruction())
                .context(savedPrompt.getContext())
                .inputExample(savedPrompt.getInputExample())
                .outputFormat(savedPrompt.getOutputFormat())
                .constraints(savedPrompt.getConstraints())
                .visibility(savedPrompt.getVisibility())
                .userId(savedPrompt.getUser().getId())
                .collectionId(null)
                .tags(tags.stream()
                        .map(tag -> TagDTO.builder()
                                .id(tag.getId())
                                .type(tag.getType())
                                .value(tag.getValue())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(savedPrompt.getCreatedAt())
                .updatedAt(savedPrompt.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public PromptResponse createPromptInCollection(CreatePromptRequest dto, UserPrincipal currentUser) {
        // Fetch User entity
        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Permission check
        if (!permissionService.canCreatePrompt(currentUser)) {
            throw new AccessDeniedException("You do not have permission to create a prompt");
        }

        // Validate and default visibility
        String promptVisibility = Visibility.parseVisibility(dto.getVisibility()).name();

        // Fetch Collection
        Collection collection = collectionRepository.findByIdAndUserId(dto.getCollectionId(), currentUser.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Collection not found or not owned by user"));

        if (collection.getIsDeleted()) {
            throw new IllegalStateException("Cannot add prompt to deleted collection");
        }

        // Validate visibility rules
        // Collection Visibility is the highest priority (Prompt must follow collection Visibility)
        permissionService.validateCollectionVisibility(collection, promptVisibility);

        // GROUP visibility → check group membership
        if (promptVisibility.equals(Visibility.GROUP.name()) && collection.getGroup() != null) {
            Group group = groupRepository.findById(collection.getGroup().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Group not found"));

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
                throw new EntityNotFoundException("One or more tags not found");
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
        return PromptResponse.builder()
                .id(savedPrompt.getId())
                .title(savedPrompt.getTitle())
                .description(savedPrompt.getDescription())
                .instruction(savedPrompt.getInstruction())
                .context(savedPrompt.getContext())
                .inputExample(savedPrompt.getInputExample())
                .outputFormat(savedPrompt.getOutputFormat())
                .constraints(savedPrompt.getConstraints())
                .visibility(savedPrompt.getVisibility())
                .userId(savedPrompt.getUser().getId())
                .collectionId(collection.getId())
                .tags(tags.stream()
                        .map(tag -> TagDTO.builder()
                                .id(tag.getId())
                                .type(tag.getType())
                                .value(tag.getValue())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(savedPrompt.getCreatedAt())
                .updatedAt(savedPrompt.getUpdatedAt())
                .build();
    }
}
