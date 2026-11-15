package com.seal.oa.oa;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.system.SystemService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobileControllerTest {
    ActivityController activity;
    SystemService system;
    JdbcTemplate jdbc;
    MobileController controller;
    Map<String,Object> task;
    @BeforeEach void setup() {
        activity=mock(ActivityController.class);system=mock(SystemService.class);
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:mobile_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE oa_mobile_message_read (tenant_id BIGINT,user_id BIGINT,message_key VARCHAR(64),read_time TIMESTAMP,PRIMARY KEY(tenant_id,user_id,message_key))");
        controller=new MobileController(activity,system,jdbc);
        task=new LinkedHashMap<>(Map.of("id",1,"taskId","task-1","instanceId","instance-1","title","客户跟进"));
        when(activity.waitingTasks(anyInt(),anyInt(),anyMap())).thenReturn(ApiResult.page(List.of(task),1));
        user(10L);
    }
    @AfterEach void cleanup(){AuthContext.clear();}
    void user(long id){AuthContext.set(new AuthContext.CurrentUser(id,"mobile-test"));when(system.requireCurrentUser()).thenReturn(new SystemService.UserAccount(id,1L,1L,"mobile-test","测试","","","","","0"));}
    @SuppressWarnings("unchecked") Map<String,Object> first(){return ((List<Map<String,Object>>)controller.tasks("todo",1,20).get("rows")).get(0);}
    @Test void readReceiptsAreIdempotentAndIsolatedByUser() {
        String key=MobileController.key("todo",task);
        assertEquals(false,first().get("read"));controller.read("todo",key);controller.read("todo",key);
        assertEquals(true,first().get("read"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oa_mobile_message_read",Integer.class));
        user(11L);assertEquals(false,first().get("read"));
    }
    @Test void inaccessibleOrCompletedTasksCannotBeOpenedOrMarkedRead() {
        String key=MobileController.key("todo",task);
        when(activity.waitingTasks(anyInt(),anyInt(),anyMap())).thenReturn(ApiResult.page(List.of(),0));
        assertThrows(ApiException.class,()->controller.detail("todo",key));
        assertThrows(ApiException.class,()->controller.read("todo",key));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oa_mobile_message_read",Integer.class));
    }
    @Test void detailFindsTasksBeyondFirstPageWithoutTrustingClientPayload() {
        when(activity.waitingTasks(1,100,Map.of())).thenReturn(ApiResult.page(List.of(Map.of("id",2)),101));
        when(activity.waitingTasks(2,100,Map.of())).thenReturn(ApiResult.page(List.of(task),101));
        assertEquals("客户跟进",((Map<?,?>)controller.detail("todo",MobileController.key("todo",task)).get("data")).get("title"));
    }
    @Test void invalidKindsAndIdentifiersAreRejected() {
        assertThrows(ApiException.class,()->controller.tasks("all-users",1,20));
        assertThrows(ApiException.class,()->controller.detail("todo","NaN"));
    }
}
