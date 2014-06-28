update matches set match_date='2014-07-13 12:30:00', round_id=6, team1_id=63, team2_id=64 where id=63;
update matches set match_date='2014-07-12 15:30:00', round_id=5, team1_id=61, team2_id=62 where id=64;

INSERT INTO matches(id, match_date, created_at, updated_at,  round_id, team1_id, team2_id, played, editable) VALUES (65,  'now', 'now', 'now', 6, 63, 64, 0, 1);

update bets set match_id = 65 where math_id = 63;
update bets set match_id = 63 where math_id = 64;
update bets set match_id = 64 where math_id = 65;

delete from matches where id = 65


