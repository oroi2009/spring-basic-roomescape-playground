INSERT INTO members (name, email, password, role)
VALUES ('어드민', 'admin@email.com', 'password', 'ADMIN'),
       ('브라운', 'brown@email.com', 'password', 'USER');

INSERT INTO themes (name, description, deleted)
VALUES ('테마1', '테마1입니다.', false),
       ('테마2', '테마2입니다.', false),
       ('테마3', '테마3입니다.', false);

INSERT INTO times (time_value, deleted)
VALUES ('10:00', false),
       ('12:00', false),
       ('14:00', false),
       ('16:00', false),
       ('18:00', false),
       ('20:00', false);

INSERT INTO reservations (member_id, name, date, time_id, theme_id)
VALUES (1, '', '2024-03-01', 1, 1),
       (1, '', '2024-03-01', 2, 2),
       (1, '', '2024-03-01', 3, 3),
       (2, '브라운', '2024-03-01', 1, 2);
