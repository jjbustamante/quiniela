ALTER TABLE matches DISABLE TRIGGER update_players_score;

update matches set score_t1=null,score_t2=null,match_date='2014-06-30 11:30:00',winner_id=null,team1_id=19,team2_id=24,played=0 where id=51;
update matches set score_t1=2,score_t2=1,match_date='2014-06-29 11:30:00',winner_id=6,team1_id=6,team2_id=3,played=1 where id=53;

update matches set score_t1=null,score_t2=null,match_date='2014-06-30 15:30:00',winner_id=null,team1_id=25,team2_id=30,played=0 where id=52;
update matches set score_t1=1,score_t2=1,match_date='2014-06-29 15:30:00',winner_id=14,team1_id=14,team2_id=10,played=1 where id=54;

update matches set score_t1=null,score_t2=null,match_date='2014-07-05 11:30:00',match_parent1_id=53,match_parent2_id=54,winner_id=null,team1_id=55,team2_id=56,played=0 where id=58;
update matches set score_t1=null,score_t2=null,match_date='2014-07-04 15:30:00',match_parent1_id=51,match_parent2_id=52,winner_id=null,team1_id=49,team2_id=50,played=0 where id=59;

update bets set match_id=100 where match_id=53;
update bets set match_id=53 where match_id=51;
update bets set match_id=51 where match_id=100;

update bets set match_id=100 where match_id=54;
update bets set match_id=54 where match_id=52;
update bets set match_id=52 where match_id=100;

update bets set match_id=100 where match_id=59;
update bets set match_id=59 where match_id=58;
update bets set match_id=58 where match_id=100;

delete from bets where match_id=61 or match_id=62 or match_id=63 or match_id=64;

ALTER TABLE matches ENABLE TRIGGER update_players_score;