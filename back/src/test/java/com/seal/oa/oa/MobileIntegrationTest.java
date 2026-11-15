package com.seal.oa.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:umm_mobile_integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class MobileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    String owner,other;
    @BeforeEach void setup() throws Exception {
        for(long id: new long[]{9801,9802}) jdbc.update("""
            INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,password)
            SELECT ?,1,?,?,password FROM sys_user WHERE user_id=4
            """,id,"mobile-test-"+id,"移动测试"+id);
        jdbc.update("INSERT INTO oa_todo(user_id,title,business_type,business_id,task_id) VALUES(9801,'移动权限提醒','mobileTest',1,'mobile-task-9801')");
        owner=login("mobile-test-9801");other=login("mobile-test-9802");
    }
    @Test void endpointsRequireLoginAndKeepReadReceiptsWithinVisibleTasks() throws Exception {
        assertEquals(401,call(null,"/mobile/tasks/todo",false).path("code").asInt());
        var page=call(owner,"/mobile/tasks/todo",false);assertEquals(200,page.path("code").asInt());
        var row=page.path("rows").get(0);assertEquals("移动权限提醒",row.path("title").asText());
        String key=row.path("mobileKey").asText(),url="/mobile/tasks/todo/"+key;
        assertEquals(0,call(other,"/mobile/tasks/todo",false).path("total").asInt());
        assertEquals(404,call(other,url,false).path("code").asInt());
        assertEquals(404,call(other,url+"/read",true).path("code").asInt());
        assertEquals(200,call(owner,url+"/read",true).path("code").asInt());
        assertTrue(call(owner,"/mobile/tasks/todo",false).path("rows").get(0).path("read").asBoolean());
        jdbc.update("UPDATE oa_todo SET status='done' WHERE task_id='mobile-task-9801'");
        assertEquals(404,call(owner,url,false).path("code").asInt());
    }
    String login(String username)throws Exception {
        var res=mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username",username,"password","admin123123")))).andReturn().getResponse();
        var json=mapper.readTree(res.getContentAsString(StandardCharsets.UTF_8));assertEquals(200,json.path("code").asInt());return "Bearer "+json.path("token").asText();
    }
    JsonNode call(String token,String url,boolean write)throws Exception {
        var request=write?post(url).contentType(MediaType.APPLICATION_JSON).content("{}"):get(url);
        if(token!=null)request.header("Authorization",token);
        return mapper.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
