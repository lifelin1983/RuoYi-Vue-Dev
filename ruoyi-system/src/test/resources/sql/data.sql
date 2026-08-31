-- 测试数据。主键刻意从 100 起，避免与 insert 用例中 H2 自增生成的主键（从 1 开始）冲突。

-- 产品树：100 为父节点，101/102 为其子节点
insert into sys_product (product_id, parent_id, product_name, order_num, status)
values (100, 0, '模具管理系统', 1, '0');
insert into sys_product (product_id, parent_id, product_name, order_num, status)
values (101, 100, '冲压模', 1, '0');
insert into sys_product (product_id, parent_id, product_name, order_num, status)
values (102, 100, '注塑模', 2, '1');

-- 学生
insert into sys_student (student_id, student_name, student_age, student_hobby, student_sex, student_status, student_birthday)
values (100, '张三', 20, '0', '0', '0', date '2006-01-15');
insert into sys_student (student_id, student_name, student_age, student_hobby, student_sex, student_status, student_birthday)
values (101, '李四', 22, '1', '1', '0', date '2004-06-20');
insert into sys_student (student_id, student_name, student_age, student_hobby, student_sex, student_status, student_birthday)
values (102, '王五', 30, '2', '0', '1', date '1996-11-03');
