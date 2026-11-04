package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:umm_oa_business_stages;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class BusinessStageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    String admin;

    @BeforeEach
    void login() throws Exception {
        JsonNode response = call(post("/login"), Map.of("username", "13610316814", "password", "admin123123"), 200);
        admin = "Bearer " + response.path("token").asText();
    }

    @Test
    void creationStartsAtFirstNodeAndRecordsItsOwnTimeAndEvent() throws Exception {
        long id = create("STAGE-CREATE");
        JsonNode opportunity = detail(id);
        assertEquals("初步接洽", opportunity.path("stage").asText());
        assertEquals("初步接洽", opportunity.path("businessState").asText());
        JsonNode records = opportunity.path("sealBusinessStages");
        assertEquals(1, records.size());
        assertEquals("初次接洽记录", records.get(0).path("remark").asText());
        assertFalse(records.get(0).path("recordTime").asText().isBlank());
    }

    @Test
    void advancementRejectsSkippingRollbackDuplicatesAndInvalidEventsWithoutChangingHistory() throws Exception {
        String code = "STAGE-ORDER";
        long id = create(code);
        for (String target : List.of("初步接洽", "方案报价", "商务谈判", "赢单", "输单", "未知阶段")) {
            advance(code, target, 400);
            assertEquals(1, detail(id).path("sealBusinessStages").size());
        }
        call(post("/project/business/stage/insert"), Map.of("businessCode", code, "state", "需求确认"), 400);
        advance(code, "需求确认", 200);
        advance(code, "需求确认", 400);
        advance(code, "初步接洽", 400);
        advance(code, "商务谈判", 400);
        JsonNode opportunity = detail(id);
        assertEquals("需求确认", opportunity.path("stage").asText());
        assertEquals(2, opportunity.path("sealBusinessStages").size());
    }

    @Test
    void advancementUsesBusinessIdWhenCodesAreDuplicated() throws Exception {
        String code = "STAGE-DUPLICATE-CODE";
        long firstId = create(code);
        long secondId = create(code);

        call(post("/project/business/stage/insert"), Map.of(
                "businessId", secondId, "state", "需求确认", "recordTime", "2026-11-03 10:00:00",
                "remark", "指定商机推进", "attachment", ""
        ), 200);

        assertEquals("初步接洽", detail(firstId).path("stage").asText());
        assertEquals("需求确认", detail(secondId).path("stage").asText());
        call(post("/project/business/stage/insert"), Map.of(
                "businessCode", code, "state", "需求确认", "recordTime", "2026-11-03 10:00:00",
                "remark", "编码无法唯一定位", "attachment", ""
        ), 400);
    }

    @Test
    void bothNegotiationOutcomesAreTerminalAndAllNodeRecordsRemainReadable() throws Exception {
        for (String outcome : List.of("赢单", "输单")) {
            String code = "STAGE-END-" + outcome;
            long id = create(code);
            for (String target : List.of("需求确认", "方案报价", "商务谈判", outcome)) {
                advance(code, target, 200);
            }
            JsonNode saved = detail(id);
            assertEquals(outcome, saved.path("stage").asText());
            JsonNode records = saved.path("sealBusinessStages");
            assertEquals(5, records.size());
            assertEquals("初次接洽记录", records.get(0).path("remark").asText());
            assertEquals("方案报价活动", records.get(2).path("remark").asText());
            assertEquals("2026-11-03 10:00:00", records.get(2).path("recordTime").asText());
            assertEquals("报价.pdf", mapper.readTree(records.get(2).path("attachment").asText()).get(0).path("name").asText());
            for (String target : List.of("初步接洽", "商务谈判", "赢单", "输单")) {
                advance(code, target, 400);
            }
            assertEquals(saved, detail(id));
        }
    }

    @Test
    void ordinaryEditingCannotChangeStageOrReplaceNodeHistory() throws Exception {
        String code = "STAGE-EDIT";
        long id = create(code);
        advance(code, "需求确认", 200);
        JsonNode before = detail(id);
        call(put("/project/business"), Map.of("id", id, "stage", "初步接洽"), 400);
        call(put("/project/business"), Map.of("id", id, "businessState", "赢单"), 400);
        call(put("/project/business"), Map.of("id", id, "name", "可修改名称", "sealBusinessStages", List.of()), 200);
        JsonNode after = detail(id);
        assertEquals("可修改名称", after.path("name").asText());
        assertEquals(before.path("stage"), after.path("stage"));
        assertEquals(before.path("sealBusinessStages"), after.path("sealBusinessStages"));
    }

    @Test
    void opportunityOwnersAndDataScopeUseTheCustomerSalesDepartment() throws Exception {
        JsonNode owners = call(get("/project/business/owner/options"), null, 200).path("data");
        assertTrue(owners.size() > 0);
        long ownerId = owners.get(0).path("userId").asLong();
        long departmentId = owners.get(0).path("deptId").asLong();
        assertEquals(326L, departmentId);

        long id = call(post("/project/business"), Map.of(
                "name", "销售部负责人商机", "ownerUserId", ownerId
        ), 200).path("data").asLong();
        JsonNode opportunity = detail(id);
        assertEquals(ownerId, opportunity.path("ownerUserId").asLong());
        assertEquals(departmentId, opportunity.path("deptId").asLong());

        call(post("/project/business"), Map.of(
                "name", "非销售部负责人商机", "ownerUserId", 1
        ), 400);
    }

    private long create(String code) throws Exception {
        return call(post("/project/business"), Map.of(
                "name", "阶段推进测试", "code", code, "stage", "赢单",
                "remark", "初次接洽记录", "sealBusinessStages", List.of(Map.of("state", "赢单"))
        ), 200).path("data").asLong();
    }

    private void advance(String code, String target, int expected) throws Exception {
        call(post("/project/business/stage/insert"), Map.of(
                "businessCode", code, "state", target, "recordTime", "2026-11-03 10:00:00",
                "remark", target + "活动", "attachment", "[{\"name\":\"报价.pdf\",\"url\":\"/profile/quote.pdf\"}]"
        ), expected);
    }

    private JsonNode detail(long id) throws Exception {
        return call(get("/project/business/" + id), null, 200).path("data");
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        if (admin != null) request.header("Authorization", admin);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        JsonNode result = mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(expected, result.path("code").asInt(), result.toString());
        return result;
    }
}
