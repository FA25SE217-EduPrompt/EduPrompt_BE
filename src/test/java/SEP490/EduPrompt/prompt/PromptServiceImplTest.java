package SEP490.EduPrompt.prompt;

import SEP490.EduPrompt.dto.request.prompt.*;
import SEP490.EduPrompt.dto.response.prompt.DetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedDetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedPromptResponse;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.client.QuotaExceededException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import SEP490.EduPrompt.service.prompt.PromptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock private PromptRepository promptRepository;
    @Mock private PromptViewLogRepository promptViewLogRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserQuotaRepository userQuotaRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private TagRepository tagRepository;
    @Mock private PromptTagRepository promptTagRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private UserRepository userRepository;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private PromptServiceImpl promptService;

    @Captor
    private ArgumentCaptor<Prompt> promptCaptor;

    @Captor
    private ArgumentCaptor<List<PromptTag>> promptTagCaptor;

    @Captor
    private ArgumentCaptor<UserQuota> userQuotaCaptor;

    // --- Helper Data ---
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID schoolId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final String EMAIL = "teacher@test.com";

    private UserPrincipal currentUser;
    private User user;
    private UserQuota userQuota;

    @BeforeEach
    void setUp() {
        // Common setup for UserPrincipal and User entity
        currentUser = UserPrincipal.builder()
                .userId(userId)
                .email(EMAIL)
                .role("TEACHER")
                .schoolId(schoolId)
                .build();

        user = User.builder()
                .id(userId)
                .email(EMAIL)
                .firstName("Test")
                .lastName("User")
                .build();

        userQuota = UserQuota.builder()
                .userId(userId)
                .promptActionRemaining(10)
                .quotaResetDate(Instant.now().plusSeconds(3600))
                .build();
    }

    // ======================================================================//
    // ====================== CREATE STANDALONE PROMPT ======================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Create Standalone - Success with valid data and tags")
    void createStandalonePrompt_WhenValidRequest_ShouldSavePromptAndDecrementQuota() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Math Prompt")
                .visibility("PUBLIC")
                .tagIds(List.of(UUID.randomUUID()))
                .build();

        Tag tag = Tag.builder()
                .id(UUID.randomUUID())
                .type("SUBJECT")
                .value("Math")
                .build();

        // 1. Mock User & Quota Lookup
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));

        // 2. Mock Permissions
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        // 3. Mock Tag Lookup
        when(tagRepository.findAllById(request.getTagIds())).thenReturn(List.of(tag));

        // 4. Mock Save (Return prompt with ID)
        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> {
            Prompt p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        // Act
        DetailPromptResponse response = promptService.createStandalonePrompt(request, currentUser);

        // Assert
        assertNotNull(response);
        assertEquals("Math Prompt", response.getTitle());
        assertEquals("PUBLIC", response.getVisibility());

        // Verify Quota Decrement
        verify(userQuotaRepository).save(userQuotaCaptor.capture());
        assertEquals(9, userQuotaCaptor.getValue().getPromptActionRemaining());

        // Verify Prompt Save
        verify(promptRepository).save(promptCaptor.capture());
        Prompt savedPrompt = promptCaptor.getValue();
        assertEquals(userId, savedPrompt.getCreatedBy());
        assertNull(savedPrompt.getCollection()); // Standalone

        // Verify Tag Save
        verify(promptTagRepository).saveAll(promptTagCaptor.capture());
        assertFalse(promptTagCaptor.getValue().isEmpty());
    }

    @Test
    @DisplayName("Case 2: Create Standalone - Fail - User Not Found")
    void createStandalonePrompt_WhenUserNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));

        verify(promptRepository, never()).save(any());
    }

    @Test
    @DisplayName("Case 3: Create Standalone - Fail - No Subscription (Quota Missing)")
    void createStandalonePrompt_WhenNoQuota_ShouldThrowResourceNotFoundException() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));
    }

    @Test
    @DisplayName("Case 4: Create Standalone - Fail - Permission Denied")
    void createStandalonePrompt_WhenPermissionDenied_ShouldThrowAccessDeniedException() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));

        when(permissionService.canCreatePrompt(currentUser)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));
    }

    @Test
    @DisplayName("Case 5: Create Standalone - Fail - Quota Exceeded")
    void createStandalonePrompt_WhenQuotaExceeded_ShouldThrowQuotaExceededException() {
        // Arrange
        userQuota.setPromptActionRemaining(0); // Set to 0

        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        // Act & Assert
        assertThrows(QuotaExceededException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));
    }

    @Test
    @DisplayName("Case 6: Create Standalone - Fail - Invalid Visibility (GROUP)")
    void createStandalonePrompt_WhenVisibilityGroup_ShouldThrowInvalidInputException() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .visibility("GROUP")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));
    }

    @Test
    @DisplayName("Case 7: Create Standalone - Fail - Invalid Visibility (SCHOOL without Id)")
    void createStandalonePrompt_WhenVisibilitySchoolAndNoSchoolId_ShouldThrowInvalidInputException() {
        // Arrange
        UserPrincipal userWithoutSchool = UserPrincipal.builder()
                .userId(userId)
                .schoolId(null) // Null School ID
                .build();

        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .visibility("SCHOOL")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(userWithoutSchool)).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.createStandalonePrompt(request, userWithoutSchool));
    }

    @Test
    @DisplayName("Case 8: Create Standalone - Fail - Tags Not Found")
    void createStandalonePrompt_WhenTagsMissing_ShouldThrowResourceNotFoundException() {
        // Arrange
        CreatePromptRequest request = CreatePromptRequest.builder()
                .title("Test")
                .visibility("PUBLIC")
                .tagIds(List.of(UUID.randomUUID()))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        // Return empty list for tags
        when(tagRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.createStandalonePrompt(request, currentUser));
    }

    // ======================================================================//
    // ==================== CREATE PROMPT IN COLLECTION =====================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Create In Collection - Success - Valid Collection & Perms")
    void createPromptInCollection_WhenValid_ShouldSavePromptLinkedToCollection() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(collectionId)
                .visibility("PRIVATE")
                .title("Collection Prompt")
                .build();

        Collection collection = Collection.builder()
                .id(collectionId)
                .user(user)
                .isDeleted(false)
                .build();

        // 1. Mock User (getReferenceById) & Quota
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));

        // 2. Mock Permissions
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        // 3. Mock Collection Lookup
        when(collectionRepository.findByIdAndUserId(collectionId, userId))
                .thenReturn(Optional.of(collection));

        // 4. Mock Save
        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> {
            Prompt p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        // Act
        DetailPromptResponse response = promptService.createPromptInCollection(request, currentUser);

        // Assert
        assertNotNull(response);
        assertEquals("Collection Prompt", response.getTitle());

        // Verify Permission Check for Visibility
        verify(permissionService).validateCollectionVisibility(collection, "PRIVATE");

        // Verify Save with Collection
        verify(promptRepository).save(promptCaptor.capture());
        assertEquals(collection, promptCaptor.getValue().getCollection());
    }

    @Test
    @DisplayName("Case 2: Create In Collection - Fail - Collection Not Found")
    void createPromptInCollection_WhenCollectionNotFound_ShouldThrowException() {
        // Arrange
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(UUID.randomUUID())
                .visibility("PRIVATE")
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        when(collectionRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.createPromptInCollection(request, currentUser));
    }

    @Test
    @DisplayName("Case 3: Create In Collection - Fail - Collection Deleted")
    void createPromptInCollection_WhenCollectionDeleted_ShouldThrowInvalidActionException() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(collectionId)
                .visibility("PRIVATE")
                .build();

        Collection collection = Collection.builder()
                .id(collectionId)
                .isDeleted(true)
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);

        when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        // Act & Assert
        assertThrows(InvalidActionException.class,
                () -> promptService.createPromptInCollection(request, currentUser));
    }

    @Test
    @DisplayName("Case 4: Create In Collection - Fail - Group Visibility & Not Member")
    void createPromptInCollection_WhenGroupVisibilityButNotMember_ShouldThrowAccessDenied() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(collectionId)
                .visibility("GROUP")
                .build();

        Group group = Group.builder()
                .id(groupId)
                .build();

        Collection collection = Collection.builder()
                .id(collectionId)
                .group(group)
                .isDeleted(false)
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);
        when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // Mock Membership Check -> False
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.createPromptInCollection(request, currentUser));
    }

    @Test
    @DisplayName("Case 5: Create In Collection - Fail - School Visibility Mismatch")
    void createPromptInCollection_WhenSchoolMismatch_ShouldThrowInvalidInputException() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(collectionId)
                .visibility("SCHOOL")
                .build();

        User otherUser = User.builder()
                .schoolId(UUID.randomUUID()) // Different School ID
                .build();

        Collection collection = Collection.builder()
                .id(collectionId)
                .user(otherUser) // Owned by someone in diff school
                .isDeleted(false)
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);
        when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.createPromptInCollection(request, currentUser));
    }

    @Test
    @DisplayName("Case 6: Create In Collection - Fail - Collection Visibility Violation")
    void createPromptInCollection_WhenCollectionVisibilityPreventsAction_ShouldThrowException() {
        // Arrange
        UUID collectionId = UUID.randomUUID();
        CreatePromptCollectionRequest request = CreatePromptCollectionRequest.builder()
                .collectionId(collectionId)
                .visibility("PUBLIC")
                .build();

        Collection collection = Collection.builder()
                .id(collectionId)
                .isDeleted(false)
                .build();

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userQuotaRepository.findByUserId(userId)).thenReturn(Optional.of(userQuota));
        when(permissionService.canCreatePrompt(currentUser)).thenReturn(true);
        when(collectionRepository.findByIdAndUserId(collectionId, userId)).thenReturn(Optional.of(collection));

        // Mock Validation Exception from Service
        doThrow(new IllegalArgumentException("Collection is Private"))
                .when(permissionService).validateCollectionVisibility(collection, "PUBLIC");

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> promptService.createPromptInCollection(request, currentUser));
    }

    // ======================================================================//
    // =========================== GET MY PROMPTS ===========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get My Prompts - Success - Returns Prompts With Tags")
    void getMyPrompts_WhenPromptsExistWithTags_ShouldReturnMappedData() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        UUID promptId = UUID.randomUUID();

        // Setup User and Collection for mapping check
        User promptOwner = User.builder().firstName("John").lastName("Doe").build();
        Collection collection = Collection.builder().name("Math Coll").build();

        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Calculus 101")
                .instruction("Solve integrals")
                .userId(userId)
                .user(promptOwner)
                .collection(collection)
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Tag tag = Tag.builder().id(UUID.randomUUID()).type("SUBJECT").value("Math").build();
        PromptTag promptTag = PromptTag.builder()
                .prompt(prompt)
                .tag(tag)
                .build();

        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt));

        // Mocks
        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(promptPage);
        when(promptTagRepository.findByPromptIdIn(List.of(promptId))).thenReturn(List.of(promptTag));

        // Act
        PaginatedDetailPromptResponse response = promptService.getMyPrompts(currentUser, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());

        // Verify Field Mapping
        DetailPromptResponse result = response.getContent().get(0);
        assertEquals(promptId, result.getId());
        assertEquals("Calculus 101", result.getTitle());
        assertEquals("John Doe", result.getFullName());
        assertEquals("Math Coll", result.getCollectionName());

        // Verify Tags Mapping
        assertEquals(1, result.getTags().size());
        assertEquals("Math", result.getTags().get(0).getValue());
    }

    @Test
    @DisplayName("Case 2: Get My Prompts - Success - No Prompts Found (Empty Page)")
    void getMyPrompts_WhenNoPrompts_ShouldReturnEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Return empty page
        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(Page.empty());

        // Act
        PaginatedDetailPromptResponse response = promptService.getMyPrompts(currentUser, pageable);

        // Assert
        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());

        // Verify tag repo was NOT called (optimization check) or called with empty list
        // Depending on impl details, usually stream maps to empty list.
        // If the code calls findByPromptIdIn(emptyList), verification is:
        verify(promptTagRepository).findByPromptIdIn(Collections.emptyList());
    }

    @Test
    @DisplayName("Case 3: Get My Prompts - Success - Prompts Exist But Have No Tags")
    void getMyPrompts_WhenPromptsHaveNoTags_ShouldReturnEmptyTagList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        UUID promptId = UUID.randomUUID();

        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Untitled")
                .userId(userId)
                .user(user)
                .build();

        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt));

        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(promptPage);

        // Return empty tags list
        when(promptTagRepository.findByPromptIdIn(List.of(promptId))).thenReturn(Collections.emptyList());

        // Act
        PaginatedDetailPromptResponse response = promptService.getMyPrompts(currentUser, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getContent().size());

        DetailPromptResponse result = response.getContent().get(0);
        assertNotNull(result.getTags());
        assertTrue(result.getTags().isEmpty());
    }

    @Test
    @DisplayName("Case 4: Get My Prompts - Success - Handle Null User and Collection (Edge Case)")
    void getMyPrompts_WhenPromptUserAndCollectionNull_ShouldHandleGracefully() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        UUID promptId = UUID.randomUUID();

        // Prompt with NULL User and NULL Collection
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Orphan Prompt")
                .userId(userId)
                .user(null)      // <--- Null
                .collection(null) // <--- Null
                .build();

        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt));

        when(promptRepository.findByUserIdAndIsDeletedFalse(userId, pageable)).thenReturn(promptPage);
        when(promptTagRepository.findByPromptIdIn(List.of(promptId))).thenReturn(Collections.emptyList());

        // Act
        PaginatedDetailPromptResponse response = promptService.getMyPrompts(currentUser, pageable);

        // Assert
        DetailPromptResponse result = response.getContent().get(0);

        // Verify fallback logic
        assertEquals("Unknown", result.getFullName()); // Should default to "Unknown"
        assertNull(result.getCollectionName());        // Should be null
    }

    // ======================================================================//
    // ====================== GET NON PRIVATE PROMPTS =======================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Non-Private - Success - Returns Allowed Prompts")
    void getNonPrivatePrompts_WhenPromptsExistAndAuthorized_ShouldReturnList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // 1. Create Prompts with different visibilities
        Prompt publicPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .title("Public Prompt")
                .visibility(Visibility.PUBLIC.name())
                .build();

        Prompt schoolPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .title("School Prompt")
                .visibility(Visibility.SCHOOL.name())
                .build();

        List<Prompt> dbPrompts = List.of(publicPrompt, schoolPrompt);
        Page<Prompt> promptPage = new PageImpl<>(dbPrompts);

        // 2. Mock DB Retrieval (Specification is used, so we match any spec)
        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(promptPage);

        // 3. Mock Permission Service (In-memory filtering)
        // Public should be allowed
        when(permissionService.canFilterPrompt(publicPrompt, currentUser)).thenReturn(true);
        // School prompt allowed (assume same school)
        when(permissionService.canFilterPrompt(schoolPrompt, currentUser)).thenReturn(true);

        // Act
        PaginatedPromptResponse response = promptService.getNonPrivatePrompts(currentUser, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getContent().size()); // Both returned
        assertEquals("Public Prompt", response.getContent().get(0).getTitle());
        assertEquals("School Prompt", response.getContent().get(1).getTitle());
    }

    @Test
    @DisplayName("Case 2: Get Non-Private - Success - Filters Out Unauthorized Prompts")
    void getNonPrivatePrompts_WhenSomeUnauthorized_ShouldFilterThemOut() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        Prompt publicPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .visibility(Visibility.PUBLIC.name())
                .build();

        Prompt differentSchoolPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .visibility(Visibility.SCHOOL.name())
                .build();

        List<Prompt> dbPrompts = List.of(publicPrompt, differentSchoolPrompt);
        Page<Prompt> promptPage = new PageImpl<>(dbPrompts);

        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(promptPage);

        // Mock Permission Service: Allow Public, Deny Different School
        when(permissionService.canFilterPrompt(publicPrompt, currentUser)).thenReturn(true);
        when(permissionService.canFilterPrompt(differentSchoolPrompt, currentUser)).thenReturn(false);

        // Act
        PaginatedPromptResponse response = promptService.getNonPrivatePrompts(currentUser, pageable);

        // Assert
        assertEquals(1, response.getContent().size()); // Only 1 returned
        assertEquals(publicPrompt.getId(), response.getContent().get(0).getId());
    }

    @Test
    @DisplayName("Case 3: Get Non-Private - Success - Empty Database Result")
    void getNonPrivatePrompts_WhenNoPromptsInDB_ShouldReturnEmptyList() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());

        // Act
        PaginatedPromptResponse response = promptService.getNonPrivatePrompts(currentUser, pageable);

        // Assert
        assertTrue(response.getContent().isEmpty());
        // Verify permission service was never called since list was empty
        verify(permissionService, never()).canFilterPrompt(any(), any());
    }

    // ======================================================================//
    // ========================= GET PROMPT BY ID ===========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Get Prompt By ID - Success - Valid Prompt with Tags")
    void getPromptById_WhenFoundAndAuthorized_ShouldReturnDetailsWithTags() {
        // Arrange
        UUID promptId = UUID.randomUUID();

        // Mock User & Collection for mapping
        User owner = User.builder().firstName("Alice").lastName("Smith").build();
        Collection collection = Collection.builder().name("Physics Set").build();

        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Detailed Prompt")
                .instruction("Do this")
                .isDeleted(false)
                .user(owner)
                .collection(collection)
                .build();

        Tag tag = Tag.builder().id(UUID.randomUUID()).type("LEVEL").value("Hard").build();
        PromptTag promptTag = PromptTag.builder().tag(tag).build();

        // 1. Mock Find
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));

        // 2. Mock Access Check (Success)
        doNothing().when(permissionService).validatePromptAccess(prompt, currentUser);

        // 3. Mock Tags
        when(promptTagRepository.findByPromptId(promptId)).thenReturn(List.of(promptTag));

        // Act
        DetailPromptResponse response = promptService.getPromptById(promptId, currentUser);

        // Assert
        assertNotNull(response);
        assertEquals(promptId, response.getId());
        assertEquals("Detailed Prompt", response.getTitle());
        assertEquals("Alice Smith", response.getFullName());
        assertEquals("Physics Set", response.getCollectionName());

        // Check Tags
        assertEquals(1, response.getTags().size());
        assertEquals("Hard", response.getTags().get(0).getValue());
    }

    @Test
    @DisplayName("Case 2: Get Prompt By ID - Fail - Prompt Not Found In DB")
    void getPromptById_WhenIdNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        when(promptRepository.findById(promptId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.getPromptById(promptId, currentUser));
    }

    @Test
    @DisplayName("Case 3: Get Prompt By ID - Fail - Prompt Soft Deleted (Standard User)")
    void getPromptById_WhenDeletedAndUserNotAdmin_ShouldThrowResourceNotFoundException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .isDeleted(true) // <--- Deleted
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));

        // Not Admin
        when(permissionService.isSystemAdmin(currentUser)).thenReturn(false);

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> promptService.getPromptById(promptId, currentUser));

        // Ensure message implies deletion or not found
        assertTrue(ex.getMessage().contains("deleted"));
    }

    @Test
    @DisplayName("Case 4: Get Prompt By ID - Success - Prompt Soft Deleted (System Admin)")
    void getPromptById_WhenDeletedButUserIsAdmin_ShouldReturnPrompt() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Deleted Prompt")
                .isDeleted(true) // <--- Deleted
                .user(user)
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));

        // IS Admin
        when(permissionService.isSystemAdmin(currentUser)).thenReturn(true);

        // Access Check Mock
        doNothing().when(permissionService).validatePromptAccess(prompt, currentUser);

        // Tags Mock
        when(promptTagRepository.findByPromptId(promptId)).thenReturn(Collections.emptyList());

        // Act
        DetailPromptResponse response = promptService.getPromptById(promptId, currentUser);

        // Assert
        assertNotNull(response);
        assertEquals("Deleted Prompt", response.getTitle());
    }

    @Test
    @DisplayName("Case 5: Get Prompt By ID - Fail - Access Denied (Service Check Fails)")
    void getPromptById_WhenPermissionServiceDenies_ShouldThrowAccessDeniedException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .isDeleted(false)
                .visibility(Visibility.PRIVATE.name())
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));

        // Mock permission check failing
        doThrow(new AccessDeniedException("This prompt is private"))
                .when(permissionService).validatePromptAccess(prompt, currentUser);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.getPromptById(promptId, currentUser));
    }

    @Test
    @DisplayName("Case 6: Get Prompt By ID - Success - Null User/Collection Handling")
    void getPromptById_WhenPromptHasNullRelations_ShouldHandleGracefully() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Orphan Prompt")
                .user(null)         // Null User
                .collection(null)   // Null Collection
                .isDeleted(false)
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        doNothing().when(permissionService).validatePromptAccess(prompt, currentUser);
        when(promptTagRepository.findByPromptId(promptId)).thenReturn(Collections.emptyList());

        // Act
        DetailPromptResponse response = promptService.getPromptById(promptId, currentUser);

        // Assert
        assertEquals("Unknown", response.getFullName());
        assertNull(response.getCollectionName());
    }

    // ======================================================================//
    // ====================== UPDATE PROMPT METADATA ========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Update Metadata - Success - Full Update with Tags")
    void updatePromptMetadata_WhenValidFullRequest_ShouldUpdateAllFields() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UpdatePromptMetadataRequest request = UpdatePromptMetadataRequest.builder()
                .title("Updated Title")
                .description("Updated Desc")
                .instruction("Updated Instr")
                .context("Updated Context")
                .inputExample("Updated In")
                .outputFormat("Updated Out")
                .constraints("Updated Const")
                .tagIds(List.of(UUID.randomUUID()))
                .build();

        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Old Title")
                .userId(userId)
                .user(user)
                .build();

        Tag tag = Tag.builder().id(UUID.randomUUID()).value("New Tag").build();

        // 1. Mock Find
        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));

        // 2. Mock Permission
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);

        // 3. Mock Tags
        when(tagRepository.findAllById(request.getTagIds())).thenReturn(List.of(tag));

        // 4. Mock Save
        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        DetailPromptResponse response = promptService.updatePromptMetadata(promptId, request, currentUser);

        // Assert
        assertEquals("Updated Title", response.getTitle());
        assertEquals("Updated Desc", response.getDescription());

        // Verify Prompt Updated fields
        assertEquals("Updated Instr", prompt.getInstruction());
        assertEquals("Updated Const", prompt.getConstraints());
        assertEquals(userId, prompt.getUpdatedBy());

        // Verify Tag Updates (Delete old, Save new)
        verify(promptTagRepository).deleteByPromptId(promptId);
        verify(promptTagRepository).saveAll(promptTagCaptor.capture());
        assertEquals(1, promptTagCaptor.getValue().size());
        assertEquals(tag.getId(), promptTagCaptor.getValue().get(0).getTag().getId());
    }

    @Test
    @DisplayName("Case 2: Update Metadata - Success - Partial Update (Only Title)")
    void updatePromptMetadata_WhenPartialRequest_ShouldUpdateOnlyProvidedFields() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UpdatePromptMetadataRequest request = UpdatePromptMetadataRequest.builder()
                .title("New Title Only")
                .build(); // Other fields null

        Prompt prompt = Prompt.builder()
                .id(promptId)
                .title("Old Title")
                .description("Old Desc") // Should stay
                .user(user)
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);
        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        DetailPromptResponse response = promptService.updatePromptMetadata(promptId, request, currentUser);

        // Assert
        assertEquals("New Title Only", prompt.getTitle());
        assertEquals("Old Desc", prompt.getDescription()); // Unchanged

        // Verify Tags were NOT touched because tagIds was null
        verify(promptTagRepository, never()).deleteByPromptId(any());
    }

    @Test
    @DisplayName("Case 3: Update Metadata - Success - Empty Tag List (Clear Tags)")
    void updatePromptMetadata_WhenEmptyTagList_ShouldRemoveAllTags() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UpdatePromptMetadataRequest request = UpdatePromptMetadataRequest.builder()
                .tagIds(Collections.emptyList()) // Empty list explicitly
                .build();

        Prompt prompt = Prompt.builder().id(promptId).user(user).build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);
        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        promptService.updatePromptMetadata(promptId, request, currentUser);

        // Assert
        verify(promptTagRepository).deleteByPromptId(promptId);
        verify(promptTagRepository, never()).saveAll(any()); // No new tags to save
    }

    @Test
    @DisplayName("Case 4: Update Metadata - Fail - Prompt Not Found")
    void updatePromptMetadata_WhenPromptNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UpdatePromptMetadataRequest request = UpdatePromptMetadataRequest.builder().build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.updatePromptMetadata(promptId, request, currentUser));
    }

    @Test
    @DisplayName("Case 5: Update Metadata - Fail - Access Denied")
    void updatePromptMetadata_WhenAccessDenied_ShouldThrowAccessDeniedException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UpdatePromptMetadataRequest request = UpdatePromptMetadataRequest.builder().build();
        Prompt prompt = Prompt.builder().id(promptId).build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.updatePromptMetadata(promptId, request, currentUser));
    }

    @Test
    @DisplayName("Case 6: Update Metadata - Fail - Tags Not Found")
    void updatePromptMetadata_WhenTagsNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UpdatePromptMetadataRequest request = UpdatePromptMetadataRequest.builder()
                .tagIds(List.of(UUID.randomUUID()))
                .build();
        Prompt prompt = Prompt.builder().id(promptId).build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);
        when(tagRepository.findAllById(anyList())).thenReturn(Collections.emptyList()); // No tags found

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.updatePromptMetadata(promptId, request, currentUser));
    }

    // ======================================================================//
    // ====================== UPDATE PROMPT VISIBILITY ======================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Update Visibility - Success - Simple Change (PUBLIC)")
    void updatePromptVisibility_WhenSimpleChange_ShouldUpdateVisibility() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility("PUBLIC")
                .build();

        Prompt prompt = Prompt.builder()
                .id(promptId)
                .visibility("PRIVATE")
                .user(user)
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);
        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        DetailPromptResponse response = promptService.updatePromptVisibility(promptId, request, currentUser);

        // Assert
        assertEquals("PUBLIC", response.getVisibility());
    }

    @Test
    @DisplayName("Case 2: Update Visibility - Success - Auto-Remove From Collection")
    void updatePromptVisibility_WhenCollectionValidationFails_ShouldRemoveFromCollection() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility("PRIVATE")
                .build();

        Collection collection = Collection.builder().id(UUID.randomUUID()).build();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .visibility("PUBLIC")
                .collection(collection) // Currently in collection
                .user(user)
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);

        // Mock Validation Failure -> triggers catch block
        doThrow(new IllegalArgumentException("Collection cannot contain private prompts"))
                .when(permissionService).validateCollectionVisibility(collection, "PRIVATE");

        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        DetailPromptResponse response = promptService.updatePromptVisibility(promptId, request, currentUser);

        // Assert
        assertEquals("PRIVATE", response.getVisibility());
        assertNull(response.getCollectionName()); // Should be removed from collection
        assertNull(prompt.getCollection());
    }

    @Test
    @DisplayName("Case 3: Update Visibility - Success - GROUP (Move to Collection)")
    void updatePromptVisibility_WhenMovingToGroupCollection_ShouldUpdateSuccess() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UUID newCollectionId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility("GROUP")
                .collectionId(newCollectionId) // Moving here
                .build();

        Prompt prompt = Prompt.builder().id(promptId).user(user).build(); // Standalone initially

        Collection newCollection = Collection.builder()
                .id(newCollectionId)
                .groupId(groupId) // Has Group
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);

        when(collectionRepository.findById(newCollectionId)).thenReturn(Optional.of(newCollection));

        // Checks
        when(permissionService.canEditCollection(currentUser, newCollection)).thenReturn(true);
        when(permissionService.isGroupMember(currentUser, groupId)).thenReturn(true);
        doNothing().when(permissionService).validateCollectionVisibility(newCollection, "GROUP");

        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        promptService.updatePromptVisibility(promptId, request, currentUser);

        // Assert
        assertEquals("GROUP", prompt.getVisibility());
        assertEquals(newCollection, prompt.getCollection());
    }

    @Test
    @DisplayName("Case 4: Update Visibility - Fail - Invalid Enum Value")
    void updatePromptVisibility_WhenInvalidEnum_ShouldThrowInvalidInputException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility("INVALID_ENUM")
                .build();

        Prompt prompt = Prompt.builder().id(promptId).build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.updatePromptVisibility(promptId, request, currentUser));
    }

    @Test
    @DisplayName("Case 5: Update Visibility - Fail - GROUP without Collection")
    void updatePromptVisibility_WhenGroupNoCollection_ShouldThrowInvalidInputException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility("GROUP")
                .collectionId(null) // No new collection
                .build();

        Prompt prompt = Prompt.builder()
                .id(promptId)
                .collection(null) // No existing collection
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.updatePromptVisibility(promptId, request, currentUser));
    }

    @Test
    @DisplayName("Case 6: Update Visibility - Fail - GROUP Collection has No Group")
    void updatePromptVisibility_WhenGroupCollectionHasNoGroupId_ShouldThrowException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UUID collId = UUID.randomUUID();
        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility("GROUP")
                .collectionId(collId)
                .build();

        Prompt prompt = Prompt.builder().id(promptId).build();
        Collection collection = Collection.builder().id(collId).groupId(null).build(); // No Group

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);
        when(collectionRepository.findById(collId)).thenReturn(Optional.of(collection));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> promptService.updatePromptVisibility(promptId, request, currentUser));
    }

    @Test
    @DisplayName("Case 7: Update Visibility - Fail - Not Group Member")
    void updatePromptVisibility_WhenNotGroupMember_ShouldThrowAccessDenied() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UUID collId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility("GROUP")
                .collectionId(collId)
                .build();

        Prompt prompt = Prompt.builder().id(promptId).build();
        Collection collection = Collection.builder().id(collId).groupId(groupId).build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);
        when(collectionRepository.findById(collId)).thenReturn(Optional.of(collection));

        when(permissionService.canEditCollection(currentUser, collection)).thenReturn(true);
        // Fail here
        when(permissionService.isGroupMember(currentUser, groupId)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.updatePromptVisibility(promptId, request, currentUser));
    }

    @Test
    @DisplayName("Case 8: Update Visibility - Fail - SCHOOL (User No School)")
    void updatePromptVisibility_WhenSchoolAndUserNoSchool_ShouldThrowInvalidInputException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UserPrincipal noSchoolUser = UserPrincipal.builder().userId(userId).schoolId(null).build();

        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility("SCHOOL")
                .build();

        Prompt prompt = Prompt.builder().id(promptId).build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(noSchoolUser, prompt)).thenReturn(true);

        // Act & Assert
        assertThrows(InvalidInputException.class,
                () -> promptService.updatePromptVisibility(promptId, request, noSchoolUser));
    }

    @Test
    @DisplayName("Case 9: Update Visibility - Fail - Adding to Collection Access Denied")
    void updatePromptVisibility_WhenAddingToCollectionDenied_ShouldThrowAccessDenied() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        UUID collId = UUID.randomUUID();

        UpdatePromptVisibilityRequest request = UpdatePromptVisibilityRequest.builder()
                .visibility("PUBLIC")
                .collectionId(collId)
                .build();

        Prompt prompt = Prompt.builder().id(promptId).build();
        Collection collection = Collection.builder().id(collId).build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);
        when(collectionRepository.findById(collId)).thenReturn(Optional.of(collection));

        // Fail: User cannot edit target collection
        when(permissionService.canEditCollection(currentUser, collection)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.updatePromptVisibility(promptId, request, currentUser));
    }

    // ======================================================================//
    // ========================== FILTER PROMPTS ============================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Filter Prompts - Success - Valid Request Returns Results")
    void filterPrompts_WhenRequestIsValid_ShouldReturnPaginatedResponse() {
        // Arrange
        PromptFilterRequest request = new PromptFilterRequest(
                userId, "CollName", null, null, null, null, "Title", false
        );
        Pageable pageable = PageRequest.of(0, 10);

        Prompt prompt = Prompt.builder()
                .id(UUID.randomUUID())
                .title("Filtered Prompt")
                .user(user)
                .build();

        Page<Prompt> promptPage = new PageImpl<>(List.of(prompt));

        // 1. Mock Validation Checks (All pass)
        when(collectionRepository.existsByNameIgnoreCase("CollName")).thenReturn(true);

        // 2. Mock Repository Find
        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(promptPage);

        // Act
        PaginatedPromptResponse response = promptService.filterPrompts(request, currentUser, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals("Filtered Prompt", response.getContent().get(0).getTitle());

        // Verify Validation was called
        verify(collectionRepository).existsByNameIgnoreCase("CollName");
    }

    @Test
    @DisplayName("Case 2: Filter Prompts - Fail - Include Deleted but Not Admin")
    void filterPrompts_WhenIncludeDeletedAndNotAdmin_ShouldThrowAccessDeniedException() {
        // Arrange
        PromptFilterRequest request = new PromptFilterRequest(
                null, null, null, null, null, null, null, true // True = Include Deleted
        );
        Pageable pageable = PageRequest.of(0, 10);

        when(permissionService.isSystemAdmin(currentUser)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.filterPrompts(request, currentUser, pageable));
    }

    @Test
    @DisplayName("Case 3: Filter Prompts - Fail - Collection Not Found")
    void filterPrompts_WhenCollectionNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        PromptFilterRequest request = new PromptFilterRequest(
                null, "Unknown Collection", null, null, null, null, null, false
        );
        Pageable pageable = PageRequest.of(0, 10);

        when(collectionRepository.existsByNameIgnoreCase("Unknown Collection")).thenReturn(false);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.filterPrompts(request, currentUser, pageable));
    }

    @Test
    @DisplayName("Case 4: Filter Prompts - Fail - School Not Found")
    void filterPrompts_WhenSchoolNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        PromptFilterRequest request = new PromptFilterRequest(
                null, null, null, null, "Unknown School", null, null, false
        );
        Pageable pageable = PageRequest.of(0, 10);

        when(schoolRepository.existsByNameIgnoreCase("Unknown School")).thenReturn(false);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.filterPrompts(request, currentUser, pageable));
    }

    @Test
    @DisplayName("Case 5: Filter Prompts - Fail - Group Not Found")
    void filterPrompts_WhenGroupNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        PromptFilterRequest request = new PromptFilterRequest(
                null, null, null, null, null, "Unknown Group", null, false
        );
        Pageable pageable = PageRequest.of(0, 10);

        when(groupRepository.existsByNameIgnoreCase("Unknown Group")).thenReturn(false);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.filterPrompts(request, currentUser, pageable));
    }

    @Test
    @DisplayName("Case 6: Filter Prompts - Fail - Tag Types Not Found")
    void filterPrompts_WhenTagTypesMissing_ShouldThrowResourceNotFoundException() {
        // Arrange
        List<String> tagTypes = List.of("SUBJECT", "LEVEL");
        PromptFilterRequest request = new PromptFilterRequest(
                null, null, tagTypes, null, null, null, null, false
        );
        Pageable pageable = PageRequest.of(0, 10);

        Tag existingTag = Tag.builder().type("SUBJECT").build();
        // Only returns SUBJECT, missing LEVEL
        when(tagRepository.findAllByTypeIn(tagTypes)).thenReturn(List.of(existingTag));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.filterPrompts(request, currentUser, pageable));
    }

    @Test
    @DisplayName("Case 7: Filter Prompts - Fail - Tag Values Not Found")
    void filterPrompts_WhenTagValuesMissing_ShouldThrowResourceNotFoundException() {
        // Arrange
        List<String> tagValues = List.of("Math", "History");
        PromptFilterRequest request = new PromptFilterRequest(
                null, null, null, tagValues, null, null, null, false
        );
        Pageable pageable = PageRequest.of(0, 10);

        Tag existingTag = Tag.builder().value("Math").build();
        // Only returns Math, missing History
        when(tagRepository.findAllByValueIn(tagValues)).thenReturn(List.of(existingTag));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.filterPrompts(request, currentUser, pageable));
    }

    @Test
    @DisplayName("Case 8: Filter Prompts - Success - Empty Result Set")
    void filterPrompts_WhenNoMatches_ShouldReturnEmptyPage() {
        // Arrange
        PromptFilterRequest request = new PromptFilterRequest(
                null, null, null, null, null, null, "NonExistent", false
        );
        Pageable pageable = PageRequest.of(0, 10);

        when(promptRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());

        // Act
        PaginatedPromptResponse response = promptService.filterPrompts(request, currentUser, pageable);

        // Assert
        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());
    }

    // ======================================================================//
    // ======================== SOFT DELETE PROMPT ==========================//
    // ======================================================================//

    @Test
    @DisplayName("Case 1: Soft Delete - Success - Mark Prompt as Deleted")
    void softDeletePrompt_WhenValid_ShouldSetIsDeletedTrue() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .isDeleted(false)
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);
        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        promptService.softDeletePrompt(promptId, currentUser);

        // Assert
        assertTrue(prompt.getIsDeleted());
        assertNotNull(prompt.getDeletedAt());
        verify(promptRepository).save(prompt);
    }

    @Test
    @DisplayName("Case 2: Soft Delete - Fail - Prompt Not Found")
    void softDeletePrompt_WhenPromptNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        when(promptRepository.findById(promptId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> promptService.softDeletePrompt(promptId, currentUser));
    }

    @Test
    @DisplayName("Case 3: Soft Delete - Fail - Already Deleted (Standard User)")
    void softDeletePrompt_WhenAlreadyDeletedAndNotAdmin_ShouldThrowResourceNotFoundException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .isDeleted(true) // Already deleted
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.isSystemAdmin(currentUser)).thenReturn(false);

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> promptService.softDeletePrompt(promptId, currentUser));

        assertEquals("Prompt not found or already deleted", ex.getMessage());
    }

    @Test
    @DisplayName("Case 4: Soft Delete - Success - Already Deleted (System Admin)")
    void softDeletePrompt_WhenAlreadyDeletedButIsAdmin_ShouldSucceed() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .isDeleted(true)
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.isSystemAdmin(currentUser)).thenReturn(true); // Is Admin
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(true);
        when(promptRepository.save(any(Prompt.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        promptService.softDeletePrompt(promptId, currentUser);

        // Assert
        verify(promptRepository).save(prompt);
        assertTrue(prompt.getIsDeleted());
    }

    @Test
    @DisplayName("Case 5: Soft Delete - Fail - Access Denied")
    void softDeletePrompt_WhenNoEditPermission_ShouldThrowAccessDeniedException() {
        // Arrange
        UUID promptId = UUID.randomUUID();
        Prompt prompt = Prompt.builder()
                .id(promptId)
                .isDeleted(false)
                .build();

        when(promptRepository.findById(promptId)).thenReturn(Optional.of(prompt));
        when(permissionService.canEditPrompt(currentUser, prompt)).thenReturn(false); // Denied

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> promptService.softDeletePrompt(promptId, currentUser));

        verify(promptRepository, never()).save(any());
    }
}
