package SEP490.EduPrompt.service.teacherProfile;


import SEP490.EduPrompt.dto.request.teacherProfile.CreateTeacherProfileRequest;
import SEP490.EduPrompt.dto.request.teacherProfile.UpdateTeacherProfileRequest;
import SEP490.EduPrompt.dto.response.teacherProfile.TeacherProfileResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;

import java.util.UUID;

public interface TeacherProfileService {
    TeacherProfileResponse createProfile(CreateTeacherProfileRequest request, UserPrincipal currentUser);
    TeacherProfileResponse updateProfile(UpdateTeacherProfileRequest request, UserPrincipal currentUser);
    TeacherProfileResponse getMyProfile(UserPrincipal currentUser);
    TeacherProfileResponse getProfileByUserId(UUID userId, UserPrincipal currentUser);
}