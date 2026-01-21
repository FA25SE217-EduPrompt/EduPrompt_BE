package SEP490.EduPrompt.service.user;

import SEP490.EduPrompt.dto.response.user.UserSchoolResponse;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public List<UserSchoolResponse> getUsersInSameSchool(UserPrincipal currentUser) {
        UUID schoolId = currentUser.getSchoolId();
        if (schoolId == null) {
            throw new InvalidInputException("You do not belong to any school");
        }

        List<User> users = userRepository.findBySchoolIdAndIsActiveTrue(schoolId);

        return users.stream()
                .map(user -> new UserSchoolResponse(
                        user.getId(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail(),
                        user.getRole(),
                        user.getIsActive()
                ))
                .collect(Collectors.toList());
    }
}
