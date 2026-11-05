package team.projectpulse.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer>, JpaSpecificationExecutor<Student> {

    // Find all students in a section
    List<Student> findBySectionSectionId(Integer sectionId);

    List<Student> findByTeamTeamId(Integer teamId);

    // A team's active students: a deactivated student owes and receives no peer evaluation (BR-submission-owed)
    List<Student> findByTeamTeamIdAndEnabledTrue(Integer teamId);

    /**
     * Atomically flips {@code enabled} from true to false. The already-deactivated check and the write are one
     * statement, so of two concurrent requests exactly one gets 1 back and the other gets 0 (UC-STU-deactivate-student
     * extension 3a). Clears the persistence context so a following read is not served a stale entity.
     *
     * @return rows changed: 1, or 0 if the student does not exist or is already deactivated
     */
    @Modifying(clearAutomatically = true)
    @Query("update Student s set s.enabled = false where s.id = :studentId and s.enabled = true")
    int deactivateIfEnabled(@Param("studentId") Integer studentId);
}
