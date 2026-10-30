-- MySQL 8：统一部门主管模型，一部门仅保留一名主管。

START TRANSACTION;

INSERT INTO sys_dept_principal (dept_id, user_id)
SELECT l.dept_id, MIN(l.user_id)
FROM sys_dept_leader l
LEFT JOIN sys_dept_principal p ON p.dept_id = l.dept_id
WHERE p.dept_id IS NULL
GROUP BY l.dept_id;

DELETE p1
FROM sys_dept_principal p1
JOIN sys_dept_principal p2
  ON p1.dept_id = p2.dept_id AND p1.user_id > p2.user_id;

UPDATE sys_dept d
JOIN sys_dept_principal p ON p.dept_id = d.dept_id
JOIN sys_user u ON u.user_id = p.user_id
SET d.leader = u.user_name,
    d.update_time = CURRENT_TIMESTAMP;

DROP TABLE IF EXISTS sys_dept_leader;
ALTER TABLE sys_dept_principal DROP PRIMARY KEY, ADD PRIMARY KEY (dept_id);

COMMIT;
