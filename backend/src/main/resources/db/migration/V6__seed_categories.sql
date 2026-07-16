-- Seed the six launch categories (docs/mvp-scope.md §1). Fixed UUIDs so all environments match.

INSERT INTO categories (id, name, slug, active)
VALUES ('c0000000-0000-4000-8000-000000000001', 'Concerts',    'concerts',    true),
       ('c0000000-0000-4000-8000-000000000002', 'Workshops',   'workshops',   true),
       ('c0000000-0000-4000-8000-000000000003', 'Conferences', 'conferences', true),
       ('c0000000-0000-4000-8000-000000000004', 'Seminars',    'seminars',    true),
       ('c0000000-0000-4000-8000-000000000005', 'Meetups',     'meetups',     true),
       ('c0000000-0000-4000-8000-000000000006', 'Festivals',   'festivals',   true);
