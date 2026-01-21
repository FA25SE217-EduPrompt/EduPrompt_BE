package SEP490.EduPrompt.service.user;

import SEP490.EduPrompt.dto.response.user.UserSchoolResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserService {
    List<UserSchoolResponse> getUsersInSameSchool(UserPrincipal currentUser);
}
