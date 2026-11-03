package com.seal.oa;

import com.seal.oa.common.LocalDatabaseBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:umm_oa_recruitment_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
class RecruitmentMigrationTest {
    @Autowired LocalDatabaseBootstrap bootstrap;
    @Autowired JdbcTemplate jdbc;

    @Test void startupRepairsLegacyIconsAndPreservesCustomIcons() throws Exception {
        long[] ids = {341300, 341301, 341302, 341303};
        String[] legacy = {"team", "solution", "contacts", "branches"};
        String[] expected = {"iconfont-tuanduiguanli", "iconfont-zhiwei", "iconfont-tuandui", "iconfont-zixiangmukanban"};
        for (int i = 0; i < ids.length; i++) jdbc.update("UPDATE sys_module SET icon=? WHERE module_id=?", legacy[i], ids[i]);
        bootstrap.run(null);
        bootstrap.run(null);
        for (int i = 0; i < ids.length; i++) assertEquals(expected[i], jdbc.queryForObject("SELECT icon FROM sys_module WHERE module_id=?", String.class, ids[i]));
        jdbc.update("UPDATE sys_module SET icon='iconfont-zuzhijiagou' WHERE module_id=341300");
        bootstrap.run(null);
        assertEquals("iconfont-zuzhijiagou", jdbc.queryForObject("SELECT icon FROM sys_module WHERE module_id=341300", String.class));
    }

    @Test void legacyDraftsAreRetiredOnceWithoutSubmittingOrChangingOtherStages() throws Exception {
        long customer=jdbc.queryForObject("SELECT MIN(id) FROM oa_merchant",Long.class);
        jdbc.update("INSERT INTO oa_recruit_job (customer_id,owner_user_id,title,code,status,demand_status,create_by) VALUES (?,1,'历史推荐升级测试','MIGRATION-APPLICATION-DRAFT','recruiting','approved',1)",customer);
        long job=jdbc.queryForObject("SELECT id FROM oa_recruit_job WHERE code='MIGRATION-APPLICATION-DRAFT'",Long.class);
        try {
            for(String state:new String[]{"available","archived","contacting"}) {
                jdbc.update("INSERT INTO oa_recruit_talent (owner_user_id,name,phone,status,create_by) VALUES (1,?,NULL,?,1)","历史升级-"+state,state);
                long talent=jdbc.queryForObject("SELECT id FROM oa_recruit_talent WHERE name=?",Long.class,"历史升级-"+state);
                boolean submitted="contacting".equals(state);
                jdbc.update("INSERT INTO oa_recruit_application (job_id,talent_id,consultant_id,stage,recommendation,submitted_date,feedback_due_date,feedback_status) VALUES (?,?,1,?,'保留原推荐说明',?,?,?)",job,talent,submitted?"recommended":"draft",submitted?java.time.LocalDate.of(2026,1,2):null,submitted?java.time.LocalDate.of(2026,1,5):null,submitted?"pending":null);
            }
            long draft=jdbc.queryForObject("SELECT MIN(id) FROM oa_recruit_application WHERE job_id=? AND stage='draft'",Long.class,job);
            jdbc.update("INSERT INTO oa_recruit_event (entity_type,entity_id,event_type,content,operator_id) VALUES ('application',?,'create','原创建日志',1)",draft);
            bootstrap.run(null);bootstrap.run(null);
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_application WHERE job_id=? AND stage='draft'",Integer.class,job));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_application WHERE job_id=? AND stage='withdrawn' AND submitted_date IS NULL AND feedback_due_date IS NULL AND revision=1 AND recommendation='保留原推荐说明'",Integer.class,job));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_application WHERE job_id=? AND stage='recommended' AND submitted_date='2026-01-02' AND feedback_due_date='2026-01-05' AND revision=0",Integer.class,job));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_event WHERE entity_type='application' AND entity_id IN (SELECT id FROM oa_recruit_application WHERE job_id=?) AND event_type='retire_recommendation_draft' AND operator_id=0",Integer.class,job));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_event WHERE entity_type='application' AND entity_id=? AND event_type='create' AND content='原创建日志'",Integer.class,draft));
            assertEquals("recruiting",jdbc.queryForObject("SELECT status FROM oa_recruit_job WHERE id=?",String.class,job));
        } finally {
            jdbc.update("DELETE FROM oa_recruit_event WHERE entity_type='application' AND entity_id IN (SELECT id FROM oa_recruit_application WHERE job_id=?)",job);
            jdbc.update("DELETE FROM oa_recruit_application WHERE job_id=?",job);
            jdbc.update("DELETE FROM oa_recruit_job WHERE id=?",job);
            jdbc.update("DELETE FROM oa_recruit_talent WHERE name LIKE '历史升级-%'");
        }
    }

    @Test void repeatedStartupPreservesEditedMenusAndRecruitmentRecords() throws Exception {
        jdbc.update("UPDATE sys_module SET module_name='招聘职位配置' WHERE module_id=341301");
        jdbc.update("INSERT INTO oa_recruit_talent (owner_user_id,name,phone,create_by) VALUES (1,'升级保留档案','13800001999',1)");
        bootstrap.run(null);
        bootstrap.run(null);
        assertEquals("招聘职位配置", jdbc.queryForObject("SELECT module_name FROM sys_module WHERE module_id=341301", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_talent WHERE phone='13800001999'", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_module WHERE parent_id=341300", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_job", Integer.class));
    }
}
