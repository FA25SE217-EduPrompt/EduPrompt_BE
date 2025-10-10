package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.group.CreateGroupRequest;
import SEP490.EduPrompt.dto.request.group.UpdateGroupRequest;
import SEP490.EduPrompt.dto.request.groupMember.AddGroupMembersRequest;
import SEP490.EduPrompt.dto.request.groupMember.RemoveGroupMemberRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.repo.GroupRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.group.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Slf4j
public class GroupController {
    private final GroupService groupService;

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<?> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Creating collection by user: {}", currentUser.getUserId());
        return ResponseDto.success(groupService.createGroup(request, currentUser));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN', 'TEACHER')")
    public ResponseDto<?> updateGroup(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGroupRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Updating group {} by user: {}", id, currentUser.getUserId());
        return ResponseDto.success(groupService.updateGroup(id, request, currentUser));
    }

    @PostMapping("/{groupId}/members")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN', 'TEACHER')")
    public ResponseDto<?> addMembersToGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody AddGroupMembersRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Adding members to group {} by user: {}", groupId, currentUser.getUserId());
        return ResponseDto.success(groupService.addMembersToGroup(groupId, request, currentUser));
    }

    @DeleteMapping("/{groupId}/members")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN', 'TEACHER')")
    public ResponseDto<?> removeMemberFromGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody RemoveGroupMemberRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Removing member from group {} by user: {}", groupId, currentUser.getUserId());
        groupService.removeMemberFromGroup(groupId, request, currentUser);
        return ResponseDto.success("Member removed successfully");
    }

    @GetMapping("/{groupId}/members")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN', 'TEACHER')")
    public ResponseDto<?> getMembersByGroupId(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Pageable pageable = PageRequest.of(page, size);
        log.info("Retrieving members for group {} by user: {}", groupId, currentUser.getUserId());
        return ResponseDto.success(groupService.getMembersByGroupId(groupId, currentUser, pageable));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN', 'TEACHER')")
    public ResponseDto<?> getGroupById(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Retrieving group {} by user: {}", groupId, currentUser.getUserId());
        return ResponseDto.success(groupService.getGroupById(groupId, currentUser));
    }
}
