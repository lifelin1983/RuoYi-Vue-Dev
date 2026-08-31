-- 测试用库表（H2 / MODE=MySQL）
-- 与 docs/plans/current-sprint.md 中规划的建表语句保持结构一致，
-- 但去掉 ENGINE / COMMENT 等 MySQL 专有语法，以兼容 H2。
-- 一旦业务表 SQL 正式入库，本文件应改为直接引用 sql/ 下的脚本。

drop table if exists sys_product;
create table sys_product
(
    product_id   bigint      not null auto_increment,
    parent_id    bigint      default 0,
    product_name varchar(30) default '',
    order_num    int         default 0,
    status       char(1)     default '0',
    create_by    varchar(64) default '',
    create_time  timestamp   default current_timestamp,
    update_by    varchar(64) default '',
    update_time  timestamp   default current_timestamp,
    remark       varchar(500),
    primary key (product_id)
);

drop table if exists sys_student;
create table sys_student
(
    student_id       bigint      not null auto_increment,
    student_name     varchar(30) default '',
    student_age      int,
    student_hobby    char(1),
    student_sex      char(1),
    student_status   char(1)     default '0',
    student_birthday date,
    create_by        varchar(64) default '',
    create_time      timestamp   default current_timestamp,
    update_by        varchar(64) default '',
    update_time      timestamp   default current_timestamp,
    remark           varchar(500),
    primary key (student_id)
);
