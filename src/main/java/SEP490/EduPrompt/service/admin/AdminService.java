package SEP490.EduPrompt.service.admin;

import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.request.school.CreateSchoolRequest;
import SEP490.EduPrompt.dto.request.schoolAdmin.BulkAssignTeachersRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.CreateSchoolSubscriptionRequest;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.dto.response.school.CreateSchoolResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.BulkAssignTeachersResponse;
import SEP490.EduPrompt.dto.response.systemAdmin.SchoolSubscriptionResponse;

import java.util.UUID;

public interface AdminService {
    SchoolSubscriptionResponse createSchoolSubscription(
            UUID schoolId, CreateSchoolSubscriptionRequest request);

    BulkAssignTeachersResponse bulkAssignTeachersToSchool(
            UUID adminUserId, BulkAssignTeachersRequest request);

    RegisterResponse createSchoolAdminAccount(RegisterRequest registerRequest);

    CreateSchoolResponse createSchool(CreateSchoolRequest request);
}
