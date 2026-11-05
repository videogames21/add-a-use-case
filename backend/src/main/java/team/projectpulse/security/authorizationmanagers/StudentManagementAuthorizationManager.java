package team.projectpulse.security.authorizationmanagers;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import team.projectpulse.student.StudentSecurityService;

import java.util.function.Supplier;

/**
 * Guards changing one student's account status (UC-STU-deactivate-student), admitting an instructor assigned to the
 * student's course section or the course admin who owns its course (BR-section-scoped-access, BR-role-based-access).
 * A student, including the student herself, is refused.
 *
 * <p>Reads {@code studentId} and delegates to {@link StudentSecurityService#canManageStudent}.
 */
@Component
public class StudentManagementAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final StudentSecurityService studentSecurityService;


    public StudentManagementAuthorizationManager(StudentSecurityService studentSecurityService) {
        this.studentSecurityService = studentSecurityService;
    }

    @Override
    public @Nullable AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication, RequestAuthorizationContext context) {
        Integer studentId = PathVariables.readId(context, "studentId");
        if (studentId == null) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(
                this.studentSecurityService.canManageStudent(studentId));
    }

}
