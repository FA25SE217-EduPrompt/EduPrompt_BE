package SEP490.EduPrompt.service.admin;

import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.request.school.CreateSchoolRequest;
import SEP490.EduPrompt.dto.request.school.SchoolEmailRequest;
import SEP490.EduPrompt.dto.request.schoolAdmin.RemoveTeacherFromSchoolRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.CreateSchoolSubscriptionRequest;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.dto.response.school.CreateSchoolResponse;
import SEP490.EduPrompt.dto.response.school.SchoolWithEmailsResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.SchoolAdminTeacherResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.SchoolSubscriptionUsageResponse;
import SEP490.EduPrompt.dto.response.systemAdmin.SchoolSubscriptionResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminService {
    SchoolSubscriptionResponse createSchoolSubscription(
            UUID schoolId, CreateSchoolSubscriptionRequest request);

//    BulkAssignTeachersResponse bulkAssignTeachersToSchool(
//            UUID adminUserId, BulkAssignTeachersRequest request);

    RegisterResponse createSchoolAdminAccount(RegisterRequest registerRequest);

    CreateSchoolResponse createSchool(CreateSchoolRequest request);

    SchoolSubscriptionUsageResponse getSubscriptionUsage(UUID adminUserId);

    Page<SchoolAdminTeacherResponse> getTeachersInSchool(UUID adminUserId, Pageable pageable);

    void removeTeacherFromSchool(UUID adminUserId, RemoveTeacherFromSchoolRequest request);

    SchoolWithEmailsResponse addEmailsToSchool(UserPrincipal currentUser, SchoolEmailRequest request);
}
