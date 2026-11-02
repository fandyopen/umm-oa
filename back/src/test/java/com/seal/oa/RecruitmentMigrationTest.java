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
