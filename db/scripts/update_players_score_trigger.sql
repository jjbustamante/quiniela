
CREATE OR REPLACE FUNCTION update_players_score()
RETURNS trigger AS $update_players_score$
DECLARE
	ref refcursor;
	row_data bets%ROWTYPE;
	score integer;
	match matches%ROWTYPE;
BEGIN
	BEGIN
		FOR row_data IN SELECT * FROM bets
		WHERE bets.match_id = new.id LOOP
			score := 0;
			IF new.ronda_id <> 1 THEN
				IF row_data.team1_id = new.team1_id or row_data.team1_id = new.team2_id THEN
					score := score + 1;
				END IF;
				IF row_data.team2_id = new.team1_id or row_data.team2_id = new.team2_id THEN
					score := score + 1;
				END IF;
				IF (row_data.team1_id = new.team1_id AND row_data.score_t1 = new.score_t1) or (row_data.team1_id = new.team2_id and row_data.score_t1 = new.score_t2) THEN
					score := score + 1;
				END IF;
				IF (row_data.team2_id = new.team1_id AND row_data.score_t2 = new.score_t1) or (row_data.team2_id = new.team2_id and row_data.score_t2 = new.score_t2) THEN
					score := score + 1;
				END IF;
				IF ((row_data.team2_id = new.team1_id AND row_data.score_t2 = new.score_t1) or (row_data.team2_id = new.team2_id and row_data.score_t2 = new.score_t2)) and
				 ((row_data.team1_id = new.team1_id AND row_data.score_t1 = new.score_t1) or (row_data.team1_id = new.team2_id and row_data.score_t1 = new.score_t2)) THEN
					score := score + 2;
				END IF;
				IF old.score_t1 <> null or old.score_t2 <> null THEN
					IF row_data.team1_id = old.team1_id or row_data.team1_id = old.team2_id THEN
						score := score - 1;
					END IF;
					IF row_data.team2_id = old.team1_id or row_data.team2_id = old.team2_id THEN
						score := score - 1;
					END IF;
					IF (row_data.team1_id = old.team1_id AND row_data.score_t1 = old.score_t1) or (row_data.team1_id = old.team2_id and row_data.score_t1 = old.score_t2) THEN
						score := score - 1;
					END IF;
					IF (row_data.team2_id = old.team1_id AND row_data.score_t2 = old.score_t1) or (row_data.team2_id = old.team2_id and row_data.score_t2 = old.score_t2) THEN
						score := score - 1;
					END IF;
					IF ((row_data.team2_id = old.team1_id AND row_data.score_t2 = old.score_t1) or (row_data.team2_id = old.team2_id and row_data.score_t2 = old.score_t2)) and
					 ((row_data.team1_id = old.team1_id AND row_data.score_t1 = old.score_t1) or (row_data.team1_id = old.team2_id and row_data.score_t1 = old.score_t2)) THEN
						score := score - 2;
					END IF;
				END IF;
			ELSE
				IF (row_data.team1_id = new.team1_id AND row_data.score_t1 = new.score_t1) or (row_data.team1_id = new.team2_id and row_data.score_t1 = new.score_t2) THEN
					score := score + 1;
				END IF;
				IF (row_data.team2_id = new.team1_id AND row_data.score_t2 = new.score_t1) or (row_data.team2_id = new.team2_id and row_data.score_t2 = new.score_t2) THEN
					score := score + 1;
				END IF;
				IF ((row_data.team2_id = new.team1_id AND row_data.score_t2 = new.score_t1) or (row_data.team2_id = new.team2_id and row_data.score_t2 = new.score_t2)) and
				 ((row_data.team1_id = new.team1_id AND row_data.score_t1 = new.score_t1) or (row_data.team1_id = new.team2_id and row_data.score_t1 = new.score_t2)) THEN
					score := score + 2;
				END IF;
				IF old.score_t1 <> null or old.score_t2 <> null THEN
					IF (row_data.team1_id = old.team1_id AND row_data.score_t1 = old.score_t1) or (row_data.team1_id = old.team2_id and row_data.score_t1 = old.score_t2) THEN
						score := score - 1;
					END IF;
					IF (row_data.team2_id = old.team1_id AND row_data.score_t2 = old.score_t1) or (row_data.team2_id = old.team2_id and row_data.score_t2 = old.score_t2) THEN
						score := score - 1;
					END IF;
					IF ((row_data.team2_id = old.team1_id AND row_data.score_t2 = old.score_t1) or (row_data.team2_id = old.team2_id and row_data.score_t2 = old.score_t2)) and
					 ((row_data.team1_id = old.team1_id AND row_data.score_t1 = old.score_t1) or (row_data.team1_id = old.team2_id and row_data.score_t1 = old.score_t2)) THEN
						score := score - 2;
					END IF;
				END IF;
			END IF;
			UPDATE quinielas as q set q.points = q.points + score where q.id = row_data.quiniela_id;
		END LOOP;
	COMMIT;
	RETURN new;
EXCEPTION WHEN others THEN 

    raise notice 'The transaction is in an uncommittable state. '
                 'Transaction was rolled back';

    raise notice '% %', SQLERRM, SQLSTATE;
END;
$update_players_score$
language 'plpgsql';


CREATE TRIGGER update_players_score
BEFORE UPDATE ON matches
FOR EACH ROW
EXECUTE PROCEDURE update_players_score();

