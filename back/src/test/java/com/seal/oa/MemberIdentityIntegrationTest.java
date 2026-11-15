package com.seal.oa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.auth.JwtService;
import com.seal.oa.common.ApiException;
import com.seal.oa.hr.HrEmployeeService;
import com.seal.oa.recruitment.RecruitmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:member_identity_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class MemberIdentityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired HrEmployeeService employees;
    @Autowired RecruitmentService recruitment;

    @AfterEach void clear() { AuthContext.clear(); }

    @ParameterizedTest
    @CsvSource({
            "/system/user/list, /rows, userId",
            "/system/user/list/queryAll, /rows, userId",
            "/project/hr/options, /data/users, userId",
            "/project/hr/employees, /data, userId",
            "/project/hr/reports/employees, /data, userId",
            "/project/recruitment/options, /data/users, id",
            "/project/commission/options, /data/people, id",
            "/project/crm/finance/options, /data/owners, id",
            "/project/merchant/owner/options, /data, userId"
    })
    void memberDirectoriesExcludeSystemIdentitiesAndKeepRealManagers(String path, String pointer, String key) throws Exception {
        // A managerial account is still an employee. Only fixed system identities are excluded.
        jdbc.update("UPDATE sys_user SET is_admin='1' WHERE user_id=4");
        var result = mvc.perform(get(path).param("pageSize", "200").header("Authorization", "Bearer " + jwt.createToken(1L, "13610316814")))
                .andReturn().getResponse().getContentAsString();
        var body = mapper.readTree(result);
        assertEquals(200, body.path("code").asInt(), result);
        var people = body.at(pointer);
        assertTrue(people.isArray(), result);
        assertFalse(people.isEmpty(), result);
        boolean managerFound = false;
        for (var person : people) {
            assertNotEquals(1L, person.path(key).asLong(), path);
            managerFound |= person.path(key).asLong() == 4L;
        }
        assertTrue(managerFound, path + " must retain ordinary managerial employees");
    }

    @Test
    void systemAccountsCannotBecomeEmployeesEvenIfLegacyArchivesExist() {
        AuthContext.set(new AuthContext.CurrentUser(1L, "13610316814"));
        employees.ensure(1L);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_employee WHERE user_id=1", Integer.class));
        jdbc.update("INSERT INTO oa_hr_employee(user_id,employee_no) VALUES(1,'EMP1')");
        assertThrows(ApiException.class, () -> employees.detail(1L));
        assertThrows(ApiException.class, () -> recruitment.member(1L));
        assertTrue(employees.list(java.util.Map.of()).stream().noneMatch(row -> ((Number) row.get("userId")).longValue() == 1L));
    }
}
