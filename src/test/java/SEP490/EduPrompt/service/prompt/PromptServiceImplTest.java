package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.request.prompt.PromptFilterRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptMetadataRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptVisibilityRequest;
import SEP490.EduPrompt.dto.response.prompt.DetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedDetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedPromptResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock
    private PromptRepository promptRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private PromptTagRepository promptTagRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private PromptServiceImpl promptService;

    private UserPrincipal teacherPrincipal;
    private UserPrincipal studentPrincipal;
    private UserPrincipal systemAdminPrincipal;
    private User user;
    private School school;
    private Collection collection;
    private Group group;
    private Prompt prompt;
    private Tag tag;
    private UUID promptId;
    private UUID userId;
    private UUID schoolId;
    private UUID collectionId;
    private UUID groupId;
    private UUID tagId;

    @BeforeEach
    void setUp() {
        promptId = UUID.randomUUID();
        userId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        tagId = UUID.randomUUID();

        // Initialize user
        user = User.builder()
                .id(userId)
                .firstName("Test")
                .lastName("User")
                .schoolId(schoolId)
                .build();

        // Initialize school
        school = School.builder()
                .id(schoolId)
                .build();

        // Initialize group
        group = Group.builder()
                .id(groupId)
                .school(school)
                .build();

        // Initialize collection
        collection = Collection.builder()
                .id(collectionId)
                .name("Test Collection")
                .user(user)
                .group(group)
                .visibility(Visibility.PUBLIC.name())
                .isDeleted(false)
                .build();

        // Initialize tag
        tag = Tag.builder()
                .id(tagId)
                .type("category")
                .value("test")
                .build();

        // Initialize prompt
        prompt = Prompt.builder()
                .id(promptId)
                .title("Test Prompt")
                .description("Description")
                .instruction("Instruction")
                .context("Context")
                .inputExample("Input")
                .outputFormat("Output")
                .constraints("Constraints")
                .user(user)
                .createdBy(userId)
                .updatedBy(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .isDeleted(false)
                .visibility(Visibility.PUBLIC.name())
                .collection(collection)
                .build();

        // Initialize UserPrincipal for different roles
        teacherPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role(Role.TEACHER.name())
                .schoolId(schoolId)
                .build();

        systemAdminPrincipal = UserPrincipal.builder()
                .userId(userId)
                .role(Role.SYSTEM_ADMIN.name())
                .schoolId(null)
                .build();
    }

    //================================================================//
    //=================CREATE STANDALONE PROMPT======================//
    @Test
    void createStandalonePrompt_Success() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test Prompt")
                .description("Description")
                .instruction("Instruction")
                .context("Context")
                .inputExample("Input")
                .outputFormat("Output")
                .constraints("Constraints")
                .visibility(Visibility.PUBLIC.name())
                .tagIds(List.of(tagId))
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(permissionService.canCreatePrompt(teacherPrincipal)).thenReturn(true);
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(tag));
        when(promptRepository.save(any(Prompt.class))).thenReturn(prompt);
        when(promptTagRepository.saveAll(anyList())).thenReturn(List.of());

        // Act
        DetailPromptResponse response = promptService.createStandalonePrompt(request, teacherPrincipal);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(promptId, response.getId(), "Prompt ID should match");
        assertEquals("Test Prompt", response.getTitle(), "Title should match");
        verify(userRepository).findById(userId);
        verify(promptRepository).save(any(Prompt.class));
        verify(promptTagRepository).saveAll(anyList());
        verifyNoInteractions(schoolRepository, groupRepository, groupMemberRepository, collectionRepository);
    }

    @Test
    void createStandalonePrompt_UserNotRegistered_ThrowsResourceNotFound() {
        // Arrange: Set up test data and mock behavior
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test Prompt")
                .description("Description")
                .instruction("Instruction")
                .context("Context")
                .inputExample("Input")
                .outputFormat("Output")
                .constraints("Constraints")
                .visibility(Visibility.PUBLIC.name())
                .tagIds(null)
                .build();
        UserPrincipal unregisteredUser = UserPrincipal.builder()
                .userId(UUID.randomUUID()) // Different userId not in database
                .role(Role.TEACHER.name()) // Use TEACHER role since STUDENT doesn't exist
                .schoolId(schoolId)
                .build();
        when(userRepository.findById(unregisteredUser.getUserId())).thenReturn(Optional.empty());

        // Act & Assert: Verify ResourceNotFoundException is thrown
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.createStandalonePrompt(request, unregisteredUser),
                "Should throw ResourceNotFoundException for unregistered user");

        // Assert: Verify interactions
        verify(userRepository).findById(unregisteredUser.getUserId());
        verifyNoInteractions(permissionService, promptRepository, tagRepository, promptTagRepository, schoolRepository, groupRepository, groupMemberRepository, collectionRepository);
    }

    @Test
    void createStandalonePrompt_InvalidVisibility() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test Prompt")
                .description("Description")
                .instruction("Instruction")
                .context("Context")
                .inputExample("Input")
                .outputFormat("Output")
                .constraints("Constraints")
                .visibility(Visibility.GROUP.name())
                .tagIds(null)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(permissionService.canCreatePrompt(teacherPrincipal)).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.createStandalonePrompt(request, teacherPrincipal),
                "Should throw InvalidInputException for GROUP visibility");
        verify(userRepository).findById(userId);
        verify(permissionService).canCreatePrompt(teacherPrincipal);
        verifyNoInteractions(promptRepository, tagRepository, promptTagRepository);
    }

    @Test
    void createStandalonePrompt_TagsNotFound() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test Prompt")
                .description("Description")
                .instruction("Instruction")
                .context("Context")
                .inputExample("Input")
                .outputFormat("Output")
                .constraints("Constraints")
                .visibility(Visibility.PUBLIC.name())
                .tagIds(List.of(tagId))
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(permissionService.canCreatePrompt(teacherPrincipal)).thenReturn(true);
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.createStandalonePrompt(request, teacherPrincipal),
                "Should throw ResourceNotFoundException for invalid tags");
        verify(userRepository).findById(userId);
        verify(permissionService).canCreatePrompt(teacherPrincipal);
        verify(tagRepository).findAllById(List.of(tagId));
        verifyNoInteractions(promptRepository, promptTagRepository);
    }
    //================================================================//
    //=====================GET MY PROMPTS=======================//
    @Test
    void getMyPrompts_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt), pageable, 1);
        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(promptPage);
        when(promptTagRepository.findByPromptId(promptId)).thenReturn(List.of(PromptTag.builder()
                .id(PromptTagId.builder().promptId(promptId).tagId(tagId).build())
                .prompt(prompt)
                .tag(tag)
                .build()));

        // Act
        PaginatedDetailPromptResponse response = promptService.getMyPrompts(teacherPrincipal, pageable);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(1, response.getContent().size(), "Should return one prompt");
        assertEquals(promptId, response.getContent().get(0).getId(), "Prompt ID should match");
        assertEquals("Test Prompt", response.getContent().get(0).getTitle(), "Prompt title should match");
        assertEquals("Test Collection", response.getContent().get(0).getCollectionName(), "Collection name should match");
        assertEquals(1, response.getTotalElements(), "Total elements should be 1");
        verify(promptRepository).findByUserIdAndIsDeletedFalse(userId, pageable);
        verify(promptTagRepository).findByPromptId(promptId);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, permissionService);
    }

    @Test
    void getMyPrompts_EmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Prompt> promptPage = new PageImpl<>(List.of(), pageable, 0);
        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(promptPage);

        // Act
        PaginatedDetailPromptResponse response = promptService.getMyPrompts(teacherPrincipal, pageable);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(0, response.getContent().size(), "Content should be empty");
        assertEquals(0, response.getTotalElements(), "Total elements should be 0");
        assertEquals(0, response.getPage(), "Page number should be 0");
        assertEquals(10, response.getSize(), "Page size should be 10");
        verify(promptRepository).findByUserIdAndIsDeletedFalse(userId, pageable);
        verifyNoInteractions(promptTagRepository, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, permissionService);
    }

    //================================================================//
    //=================GET NON-PRIVATE PROMPTS===================//
    @Test
    void getNonPrivatePrompts_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt), pageable, 1);
        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(promptPage);
        when(permissionService.canFilterPrompt(prompt, teacherPrincipal)).thenReturn(true);

        // Act
        PaginatedPromptResponse response = promptService.getNonPrivatePrompts(teacherPrincipal, pageable);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(1, response.getContent().size(), "Should return one prompt");
        assertEquals("Test Prompt", response.getContent().get(0).getTitle(), "Prompt title should match");
        assertEquals(1, response.getTotalElements(), "Total elements should be 1");
        verify(promptRepository).findAll(any(Specification.class), eq(pageable));
        verify(permissionService).canFilterPrompt(prompt, teacherPrincipal);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void getNonPrivatePrompts_EmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Prompt> promptPage = new PageImpl<>(List.of(), pageable, 0);
        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(promptPage);

        // Act
        PaginatedPromptResponse response = promptService.getNonPrivatePrompts(teacherPrincipal, pageable);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(0, response.getContent().size(), "Content should be empty");
        assertEquals(0, response.getTotalElements(), "Total elements should be 0");
        verify(promptRepository).findAll(any(Specification.class), eq(pageable));
        verifyNoInteractions(permissionService, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    //================================================================//
    //=================GET PROMPTS BY USER ID===================//
    @Test
    void getPromptsByUserId_Success_Self() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt), pageable, 1);
        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(promptPage);
        when(permissionService.canAccessPrompt(prompt, teacherPrincipal)).thenReturn(true);

        // Act
        PaginatedPromptResponse response = promptService.getPromptsByUserId(teacherPrincipal, pageable, userId);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(1, response.getContent().size(), "Should return one prompt");
        assertEquals("Test Prompt", response.getContent().get(0).getTitle(), "Prompt title should match");
        verify(promptRepository).findByUserIdAndIsDeletedFalse(userId, pageable);
        verify(permissionService).canAccessPrompt(prompt, teacherPrincipal);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void getPromptsByUserId_AccessDenied_NonAdmin() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        UUID otherUserId = UUID.randomUUID();
        when(permissionService.isAdmin(teacherPrincipal)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.getPromptsByUserId(teacherPrincipal, pageable, otherUserId),
                "Should throw AccessDeniedException for non-admin accessing another user's prompts");
        verify(permissionService).isAdmin(teacherPrincipal);
        verifyNoInteractions(promptRepository, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void getPromptsByUserId_Success_Admin() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        UUID otherUserId = UUID.randomUUID();
        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt), pageable, 1);
        when(permissionService.isAdmin(systemAdminPrincipal)).thenReturn(true);
        when(promptRepository.findByUserIdAndIsDeletedFalse(otherUserId, pageable)).thenReturn(promptPage);
        when(permissionService.canAccessPrompt(prompt, systemAdminPrincipal)).thenReturn(true);

        // Act
        PaginatedPromptResponse response = promptService.getPromptsByUserId(systemAdminPrincipal, pageable, otherUserId);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(1, response.getContent().size(), "Should return one prompt");
        verify(permissionService).isAdmin(systemAdminPrincipal);
        verify(promptRepository).findByUserIdAndIsDeletedFalse(otherUserId, pageable);
        verify(permissionService).canAccessPrompt(prompt, systemAdminPrincipal);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void getPromptsByUserId_NullUserId_ThrowsInvalidInput() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.getPromptsByUserId(teacherPrincipal, pageable, null),
                "Should throw InvalidInputException for null userId");
        verifyNoInteractions(promptRepository, permissionService, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    //================================================================//
    //===============GET PROMPTS BY COLLECTION ID===============//
    @Test
    void getPromptsByCollectionId_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt), pageable, 1);
        when(permissionService.canAccessCollection(teacherPrincipal, collectionId)).thenReturn(true);
        when(promptRepository.findByCollectionIdAndIsDeletedFalse(collectionId, pageable)).thenReturn(promptPage);
        when(permissionService.canAccessPrompt(prompt, teacherPrincipal)).thenReturn(true);

        // Act
        PaginatedPromptResponse response = promptService.getPromptsByCollectionId(teacherPrincipal, pageable, collectionId);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(1, response.getContent().size(), "Should return one prompt");
        assertEquals("Test Prompt", response.getContent().get(0).getTitle(), "Prompt title should match");
        verify(permissionService).canAccessCollection(teacherPrincipal, collectionId);
        verify(promptRepository).findByCollectionIdAndIsDeletedFalse(collectionId, pageable);
        verify(permissionService).canAccessPrompt(prompt, teacherPrincipal);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void getPromptsByCollectionId_AccessDenied() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(permissionService.canAccessCollection(teacherPrincipal, collectionId)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.getPromptsByCollectionId(teacherPrincipal, pageable, collectionId),
                "Should throw AccessDeniedException for unauthorized collection access");
        verify(permissionService).canAccessCollection(teacherPrincipal, collectionId);
        verifyNoInteractions(promptRepository, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void getPromptsByCollectionId_NullCollectionId_ThrowsInvalidInput() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.getPromptsByCollectionId(teacherPrincipal, pageable, null),
                "Should throw InvalidInputException for null collectionId");
        verifyNoInteractions(permissionService, promptRepository, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    //================================================================//
    //=====================FILTER PROMPTS=======================//
    @Test
    void filterPrompts_Success() {
        // Arrange: Set up test data and mock behavior
        Pageable pageable = PageRequest.of(0, 10);
        PromptFilterRequest request = PromptFilterRequest.builder()
                .includeDeleted(false)
                .collectionName(null)
                .tagTypes(null)
                .tagValues(null)
                .build();
        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt), pageable, 1);
        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(promptPage);
        //when(permissionService.canAccessPrompt(prompt, teacherPrincipal)).thenReturn(true);

        // Act
        PaginatedPromptResponse response = promptService.filterPrompts(request, teacherPrincipal, pageable);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(1, response.getContent().size(), "Should return one prompt");
        assertEquals("Test Prompt", response.getContent().get(0).getTitle(), "Prompt title should match");
        assertEquals(1, response.getTotalElements(), "Total elements should be 1");
        assertEquals(0, response.getPage(), "Page number should be 0");
        assertEquals(10, response.getSize(), "Page size should be 10");
        verify(promptRepository).findAll(any(Specification.class), eq(pageable));
        //verify(permissionService).canAccessPrompt(prompt, teacherPrincipal);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void filterPrompts_IncludeDeleted_AccessDenied_NonSystemAdmin() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        PromptFilterRequest request = PromptFilterRequest.builder()
                .includeDeleted(true)
                .build();
        when(permissionService.isSystemAdmin(teacherPrincipal)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.filterPrompts(request, teacherPrincipal, pageable),
                "Should throw AccessDeniedException for non-SYSTEM_ADMIN including deleted prompts");
        verify(permissionService).isSystemAdmin(teacherPrincipal);
        verifyNoInteractions(promptRepository, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void filterPrompts_CollectionNotFound() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        PromptFilterRequest request = PromptFilterRequest.builder()
                .includeDeleted(false)
                .collectionName("NonExistent")
                .build();
        when(collectionRepository.existsByNameIgnoreCase("NonExistent")).thenReturn(false);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.filterPrompts(request, teacherPrincipal, pageable),
                "Should throw ResourceNotFoundException for non-existent collection");
        verify(collectionRepository).existsByNameIgnoreCase("NonExistent");
        verifyNoInteractions(promptRepository, permissionService, userRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    //================================================================//
    //=====================GET PROMPT BY ID=====================//
    @Test
    void getPromptById_Success_Public() {
        // Arrange
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(promptTagRepository.findByPromptId(promptId)).thenReturn(List.of(PromptTag.builder()
                .id(PromptTagId.builder().promptId(promptId).tagId(tagId).build())
                .prompt(prompt)
                .tag(tag)
                .build()));

        // Act
        DetailPromptResponse response = promptService.getPromptById(promptId, teacherPrincipal);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(promptId, response.getId(), "Prompt ID should match");
        assertEquals("Test Prompt", response.getTitle(), "Prompt title should match");
        assertEquals("Test Collection", response.getCollectionName(), "Collection name should match");
        verify(promptRepository).findById(promptId);
        verify(promptTagRepository).findByPromptId(promptId);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, permissionService);
    }

    @Test
    void getPromptById_PromptNotFound() {
        // Arrange
        when(promptRepository.findById(promptId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.getPromptById(promptId, teacherPrincipal),
                "Should throw ResourceNotFoundException for non-existent prompt");
        verify(promptRepository).findById(promptId);
        verifyNoInteractions(promptTagRepository, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, permissionService);
    }

    @Test
    void getPromptById_DeletedPrompt_NonSystemAdmin() {
        // Arrange
        prompt.setIsDeleted(true);
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.isSystemAdmin(teacherPrincipal)).thenReturn(false);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.getPromptById(promptId, teacherPrincipal),
                "Should throw ResourceNotFoundException for deleted prompt by non-SYSTEM_ADMIN");
        verify(promptRepository).findById(promptId);
        verify(permissionService).isSystemAdmin(teacherPrincipal);
        verifyNoInteractions(promptTagRepository, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository);
    }

    @Test
    void getPromptById_PrivatePrompt_AccessDenied() {
        // Arrange: Set up test data and mock behavior
        UUID differentUserId = UUID.randomUUID(); // Different from prompt's createdBy
        UserPrincipal nonOwnerUser = UserPrincipal.builder()
                .userId(differentUserId)
                .role(Role.TEACHER.name())
                .schoolId(schoolId)
                .build();
        prompt.setVisibility(Visibility.PRIVATE.name());
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.isAdmin(nonOwnerUser)).thenReturn(false);

        // Act & Assert: Verify AccessDeniedException is thrown
        assertThrows(AccessDeniedException.class,
                () -> promptService.getPromptById(promptId, nonOwnerUser),
                "Should throw AccessDeniedException for unauthorized private prompt access");

        // Assert: Verify interactions
        verify(promptRepository).findById(promptId);
        verify(permissionService).isAdmin(nonOwnerUser);
        verifyNoInteractions(promptTagRepository, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository);
    }

    @Test
    void getPromptById_UnregisteredUser_ThrowsResourceNotFound() {
        // Arrange: Set up test data and mock behavior
        UserPrincipal unregisteredUser = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.TEACHER.name())
                .schoolId(schoolId)
                .build();
        prompt.setVisibility(Visibility.SCHOOL.name()); // Ensure SCHOOL visibility to trigger user lookup
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(userRepository.findById(prompt.getCreatedBy())).thenReturn(Optional.empty());

        // Act & Assert: Verify ResourceNotFoundException is thrown
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.getPromptById(promptId, unregisteredUser),
                "Should throw ResourceNotFoundException for unregistered prompt owner");

        // Assert: Verify interactions
        verify(promptRepository).findById(promptId);
        verify(userRepository).findById(prompt.getCreatedBy());
        verifyNoInteractions(promptTagRepository, permissionService, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository);
    }
    //================================================================//
    //=====================UPDATE PROMPT METADATA=======================//
    @Test
    void updatePromptMetadata_Success() {
        // Arrange
        UpdatePromptMetadataRequest request = UpdatePromptMetadataRequest.builder()
                .title("Updated Title")
                .description("Updated Description")
                .instruction("Updated Instruction")
                .context("Updated Context")
                .inputExample("Updated Input")
                .outputFormat("Updated Output")
                .constraints("Updated Constraints")
                .tagIds(List.of(tagId))
                .build();
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(teacherPrincipal, prompt)).thenReturn(true);
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(tag));
        when(promptRepository.save(any(Prompt.class))).thenReturn(prompt);
        when(promptTagRepository.findByPromptId(promptId)).thenReturn(List.of(PromptTag.builder()
                .id(PromptTagId.builder().promptId(promptId).tagId(tagId).build())
                .prompt(prompt)
                .tag(tag)
                .build()));

        // Act
        DetailPromptResponse response = promptService.updatePromptMetadata(promptId, request, teacherPrincipal);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(promptId, response.getId(), "Prompt ID should match");
        assertEquals("Updated Title", response.getTitle(), "Title should be updated");
        assertEquals("Updated Description", response.getDescription(), "Description should be updated");
        verify(promptRepository).findById(promptId);
        verify(permissionService).canEditPrompt(teacherPrincipal, prompt);
        verify(tagRepository).findAllById(List.of(tagId));
        verify(promptTagRepository).deleteByPromptId(promptId);
        verify(promptTagRepository).saveAll(anyList());
        verify(promptRepository).save(any(Prompt.class));
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository);
    }

    @Test
    void updatePromptMetadata_PromptNotFound_ThrowsResourceNotFound() {
        // Arrange
        UpdatePromptMetadataRequest request = UpdatePromptMetadataRequest.builder()
                .title("Updated Title")
                .build();
        when(promptRepository.findById(promptId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.updatePromptMetadata(promptId, request, teacherPrincipal),
                "Should throw ResourceNotFoundException for non-existent prompt");
        verify(promptRepository).findById(promptId);
        verifyNoInteractions(permissionService, tagRepository, promptTagRepository, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository);
    }

    @Test
    void updatePromptMetadata_AccessDenied() {
        // Arrange
        UpdatePromptMetadataRequest request = UpdatePromptMetadataRequest.builder()
                .title("Updated Title")
                .build();
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(teacherPrincipal, prompt)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.updatePromptMetadata(promptId, request, teacherPrincipal),
                "Should throw AccessDeniedException for unauthorized user");
        verify(promptRepository).findById(promptId);
        verify(permissionService).canEditPrompt(teacherPrincipal, prompt);
        verifyNoInteractions(tagRepository, promptTagRepository, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository);
    }

    @Test
    void updatePromptMetadata_TagsNotFound_ThrowsResourceNotFound() {
        // Arrange
        UpdatePromptMetadataRequest request = UpdatePromptMetadataRequest.builder()
                .tagIds(List.of(tagId))
                .build();
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(teacherPrincipal, prompt)).thenReturn(true);
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.updatePromptMetadata(promptId, request, teacherPrincipal),
                "Should throw ResourceNotFoundException for invalid tags");
        verify(promptRepository).findById(promptId);
        verify(permissionService).canEditPrompt(teacherPrincipal, prompt);
        verify(tagRepository).findAllById(List.of(tagId));
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository);
    }

    //================================================================//
    //=====================UPDATE PROMPT VISIBILITY=======================//
    @Test
    void updatePromptVisibility_Success() {
        // Arrange
        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility(Visibility.PUBLIC.name())
                .collectionId(null)
                .build();
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(teacherPrincipal, prompt)).thenReturn(true);
        when(promptRepository.save(any(Prompt.class))).thenReturn(prompt);
        when(promptTagRepository.findByPromptId(promptId)).thenReturn(List.of(PromptTag.builder()
                .id(PromptTagId.builder().promptId(promptId).tagId(tagId).build())
                .prompt(prompt)
                .tag(tag)
                .build()));

        // Act
        DetailPromptResponse response = promptService.updatePromptVisibility(promptId, request, teacherPrincipal);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(promptId, response.getId(), "Prompt ID should match");
        assertEquals(Visibility.PUBLIC.name(), response.getVisibility(), "Visibility should be updated");
        verify(promptRepository).findById(promptId);
        verify(permissionService).canEditPrompt(teacherPrincipal, prompt);
        verify(promptRepository).save(any(Prompt.class));
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository);
    }

    @Test
    void updatePromptVisibility_PromptNotFound_ThrowsResourceNotFound() {
        // Arrange
        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility(Visibility.PUBLIC.name())
                .build();
        when(promptRepository.findById(promptId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.updatePromptVisibility(promptId, request, teacherPrincipal),
                "Should throw ResourceNotFoundException for non-existent prompt");
        verify(promptRepository).findById(promptId);
        verifyNoInteractions(permissionService, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void updatePromptVisibility_AccessDenied() {
        // Arrange
        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility(Visibility.PUBLIC.name())
                .build();
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(teacherPrincipal, prompt)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.updatePromptVisibility(promptId, request, teacherPrincipal),
                "Should throw AccessDeniedException for unauthorized user");
        verify(promptRepository).findById(promptId);
        verify(permissionService).canEditPrompt(teacherPrincipal, prompt);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void updatePromptVisibility_InvalidVisibility_ThrowsInvalidInput() {
        // Arrange
        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility("INVALID")
                .build();
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(teacherPrincipal, prompt)).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.updatePromptVisibility(promptId, request, teacherPrincipal),
                "Should throw InvalidInputException for invalid visibility");
        verify(promptRepository).findById(promptId);
        verify(permissionService).canEditPrompt(teacherPrincipal, prompt);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void updatePromptVisibility_GroupVisibility_NotMember_ThrowsAccessDenied() {
        // Arrange
        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility(Visibility.GROUP.name())
                .collectionId(collectionId)
                .build();
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(teacherPrincipal, prompt)).thenReturn(true);
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(permissionService.canEditCollection(teacherPrincipal, collection)).thenReturn(true);
        when(permissionService.isGroupMember(teacherPrincipal, groupId)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.updatePromptVisibility(promptId, request, teacherPrincipal),
                "Should throw AccessDeniedException for non-group member");
        verify(promptRepository).findById(promptId);
        verify(permissionService).canEditPrompt(teacherPrincipal, prompt);
        verify(collectionRepository).findById(collectionId);
        verify(permissionService).canEditCollection(teacherPrincipal, collection);
        verify(permissionService).isGroupMember(teacherPrincipal, groupId);
        verifyNoInteractions(userRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    //================================================================//
    //=====================SOFT DELETE PROMPT=======================//
    @Test
    void softDeletePrompt_Success() {
        // Arrange
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(teacherPrincipal, prompt)).thenReturn(true);
        when(promptRepository.save(any(Prompt.class))).thenReturn(prompt);

        // Act
        promptService.softDeletePrompt(promptId, teacherPrincipal);

        // Assert
        verify(promptRepository).findById(promptId);
        verify(permissionService).canEditPrompt(teacherPrincipal, prompt);
        verify(promptRepository).save(argThat(p -> p.getIsDeleted()));
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void softDeletePrompt_PromptNotFound_ThrowsResourceNotFound() {
        // Arrange
        when(promptRepository.findById(promptId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.softDeletePrompt(promptId, teacherPrincipal),
                "Should throw ResourceNotFoundException for non-existent prompt");
        verify(promptRepository).findById(promptId);
        verifyNoInteractions(permissionService, userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void softDeletePrompt_AlreadyDeleted_NonSystemAdmin_ThrowsResourceNotFound() {
        // Arrange
        prompt.setIsDeleted(true);
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.isSystemAdmin(teacherPrincipal)).thenReturn(false);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.softDeletePrompt(promptId, teacherPrincipal),
                "Should throw ResourceNotFoundException for already deleted prompt by non-SYSTEM_ADMIN");
        verify(promptRepository).findById(promptId);
        verify(permissionService).isSystemAdmin(teacherPrincipal);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }

    @Test
    void softDeletePrompt_AccessDenied() {
        // Arrange
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(teacherPrincipal, prompt)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.softDeletePrompt(promptId, teacherPrincipal),
                "Should throw AccessDeniedException for unauthorized user");
        verify(promptRepository).findById(promptId);
        verify(permissionService).canEditPrompt(teacherPrincipal, prompt);
        verifyNoInteractions(userRepository, collectionRepository, groupRepository, groupMemberRepository, schoolRepository, tagRepository, promptTagRepository);
    }


    // Additional tests for other methods can be added here...
}