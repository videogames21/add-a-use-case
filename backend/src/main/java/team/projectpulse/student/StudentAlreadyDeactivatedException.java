package team.projectpulse.student;

/**
 * Thrown when a student who is already deactivated is deactivated again (UC-STU-deactivate-student, extension 3a).
 * {@code ExceptionHandlerAdvice} maps it to {@code 409 CONFLICT}: the request is well-formed, but the student's state
 * conflicts with it.
 */
public class StudentAlreadyDeactivatedException extends RuntimeException {

    public StudentAlreadyDeactivatedException() {
        super("This student is already deactivated.");
    }

}
