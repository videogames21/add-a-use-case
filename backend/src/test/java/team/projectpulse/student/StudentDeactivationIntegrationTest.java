package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.evaluation.PeerEvaluationRepository;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.instructor.InstructorRepository;
import team.projectpulse.system.StatusCode;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student, FR-SEC-active-account: who may deactivate a student (extension 2a), and what a
 * deactivated account can no longer do.
 *
 * <p>Seed data: John (student) is in the 2023-2024 course section of the Senior Design course. Bill Gates is an
 * instructor assigned to that course section; Bingyang Wei is both the course's admin and an assigned instructor;
 * Tim Cook is the admin of a different course and is assigned to none of its sections.
 */
@DisplayName("Integration tests for deactivating a student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    InstructorRepository instructorRepository;

    @Autowired
    PeerEvaluationRepository peerEvaluationRepository;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    Student john;


    @BeforeEach
    void setUp() {
        this.john = this.studentRepository.findAll().stream()
                .filter(student -> student.getEmail().equals("j.smith@abc.edu"))
                .findFirst().orElseThrow();
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private Instructor instructor(String email) {
        return this.instructorRepository.findAll().stream().filter(i -> i.getEmail().equals(email)).findFirst().orElseThrow();
    }

    private void assertJohnStillActive() {
        this.entityManager.flush();
        this.entityManager.clear();
        assertThat(this.studentRepository.findById(this.john.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Main: an assigned instructor deactivates a student, who stays on her team with her work kept")
    void assignedInstructorDeactivatesStudent() throws Exception {
        String token = login("b.gates@abc.edu");
        Integer teamId = this.john.getTeam().getTeamId();
        long evaluationsBefore = this.peerEvaluationRepository.count();

        this.mockMvc.perform(post(this.baseUrl + "/students/" + this.john.getId() + "/deactivate").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.data.id").value(this.john.getId()))
                .andExpect(jsonPath("$.data.enabled").value(false));

        this.entityManager.flush();
        this.entityManager.clear();
        Student stored = this.studentRepository.findById(this.john.getId()).orElseThrow();
        assertThat(stored.isEnabled()).isFalse();
        assertThat(stored.getTeam().getTeamId()).isEqualTo(teamId); // not removed from her team
        assertThat(this.peerEvaluationRepository.count()).isEqualTo(evaluationsBefore); // her work is kept
    }

    @Test
    @DisplayName("Main: the course admin who owns the course but is not assigned to the section deactivates a student")
    void owningCourseAdminNotAssignedToSectionDeactivatesStudent() throws Exception {
        Instructor bingyang = instructor("b.wei@abc.edu");
        this.john.getSection().removeInstructor(bingyang); // still the course admin, no longer assigned to the section
        this.entityManager.flush();
        String token = login("b.wei@abc.edu");

        this.mockMvc.perform(post(this.baseUrl + "/students/" + this.john.getId() + "/deactivate").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @DisplayName("3a: deactivating an already deactivated student is a 409 and changes nothing")
    void secondDeactivationIsAConflict() throws Exception {
        String token = login("b.gates@abc.edu");
        this.mockMvc.perform(post(this.baseUrl + "/students/" + this.john.getId() + "/deactivate").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        this.mockMvc.perform(post(this.baseUrl + "/students/" + this.john.getId() + "/deactivate").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("This student is already deactivated."));

        this.entityManager.flush();
        this.entityManager.clear();
        assertThat(this.studentRepository.findById(this.john.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("3a: the deactivation is one conditional update, so a second attempt changes no row")
    void conditionalUpdateChangesARowOnlyOnce() {
        // The race is decided by this statement: whichever request runs it first gets 1, any other gets 0 and the
        // service turns that into the 409. Two sequential calls exercise the same database-side guard.
        assertThat(this.studentRepository.deactivateIfEnabled(this.john.getId())).isEqualTo(1);
        assertThat(this.studentRepository.deactivateIfEnabled(this.john.getId())).isEqualTo(0);
        assertThat(this.studentRepository.deactivateIfEnabled(-1)).isEqualTo(0); // no such student

        assertThat(this.studentRepository.findById(this.john.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("The teammate list omits a deactivated student")
    void teammateListOmitsDeactivatedStudent() throws Exception {
        String instructorToken = login("b.gates@abc.edu");
        String ericToken = login("e.hudson@abc.edu"); // John's teammate
        Integer teamId = this.john.getTeam().getTeamId();
        this.mockMvc.perform(post(this.baseUrl + "/students/" + this.john.getId() + "/deactivate").header(HttpHeaders.AUTHORIZATION, instructorToken))
                .andExpect(status().isOk());

        this.mockMvc.perform(get(this.baseUrl + "/students/teams/" + teamId).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, ericToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + this.john.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$.data.length()").value(2)); // Eric and Jerry remain
    }

    @Test
    @DisplayName("The section's weekly peer evaluation report omits a deactivated student, and evaluations are kept")
    void sectionReportOmitsDeactivatedStudent() throws Exception {
        String token = login("b.gates@abc.edu");
        Integer sectionId = this.john.getSection().getSectionId();
        long evaluationsBefore = this.peerEvaluationRepository.count();

        // Baseline: while she is active she is listed, together with other students
        Set<Integer> before = reportStudentIds(sectionId, token);
        assertThat(before).contains(this.john.getId()).hasSizeGreaterThan(1);

        this.mockMvc.perform(post(this.baseUrl + "/students/" + this.john.getId() + "/deactivate").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        // Afterward the report is exactly the same students minus her
        Set<Integer> expected = new HashSet<>(before);
        expected.remove(this.john.getId());
        assertThat(reportStudentIds(sectionId, token)).isEqualTo(expected);

        assertThat(this.peerEvaluationRepository.count()).isEqualTo(evaluationsBefore);
    }

    private Set<Integer> reportStudentIds(Integer sectionId, String token) throws Exception {
        String body = this.mockMvc.perform(get(this.baseUrl + "/evaluations/sections/" + sectionId + "/week/2023-W31").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JSONArray averages = new JSONObject(body).getJSONObject("data").getJSONArray("peerEvaluationAverages");
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < averages.length(); i++) {
            ids.add(averages.getJSONObject(i).getInt("studentId"));
        }
        return ids;
    }

    @Test
    @DisplayName("2a: an instructor who is not assigned to the student's course section is forbidden")
    void instructorOfAnotherSectionIsForbidden() throws Exception {
        String token = login("t.cook@abc.edu"); // admin of another course, assigned to none of this one's sections

        this.mockMvc.perform(post(this.baseUrl + "/students/" + this.john.getId() + "/deactivate").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        assertJohnStillActive();
    }

    @Test
    @DisplayName("2a: an unauthenticated caller is refused")
    void unauthenticatedCallerIsRefused() throws Exception {
        this.mockMvc.perform(post(this.baseUrl + "/students/" + this.john.getId() + "/deactivate"))
                .andExpect(status().isUnauthorized());

        assertJohnStillActive();
    }

    @Test
    @DisplayName("FR-SEC-active-account: a deactivated student cannot log in with valid credentials")
    void deactivatedStudentCannotLogIn() throws Exception {
        this.john.setEnabled(false);
        this.studentRepository.saveAndFlush(this.john);

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-SEC-active-account: a token issued before deactivation gets 401 afterward")
    void tokenIssuedBeforeDeactivationIsRefused() throws Exception {
        String token = login("j.smith@abc.edu");
        this.mockMvc.perform(get(this.baseUrl + "/students/" + this.john.getId()).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()); // the token works while she is active

        this.john.setEnabled(false);
        this.studentRepository.saveAndFlush(this.john);

        this.mockMvc.perform(get(this.baseUrl + "/students/" + this.john.getId()).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

}
