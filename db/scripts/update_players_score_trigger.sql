
CREATE OR REPLACE FUNCTION update_players_score() RETURNS TRIGGER AS $$
DECLARE
	row_data bets%ROWTYPE;
	score integer;
BEGIN
		FOR row_data IN SELECT * FROM bets
		WHERE bets.match_id = new.id LOOP
			score := 0;
			-- No es primera ronda
			IF new.round_id <> 1 THEN
				-- Si No son octavos de final y Aciertas el equipo 3 puntos
				IF new.round_id <> 2 THEN
					IF row_data.team1_id = new.team1_id or row_data.team1_id = new.team2_id THEN
						score := score + 3;
					END IF;
					IF row_data.team2_id = new.team1_id or row_data.team2_id = new.team2_id THEN
						score := score + 3;
					END IF;
				END IF;
				
				-- Acertar el Score dar 2 puntos
				IF (row_data.team1_id = new.team1_id AND row_data.score_t1 = new.score_t1) or (row_data.team1_id = new.team2_id and row_data.score_t1 = new.score_t2) THEN
					score := score + 2;
				END IF;
				IF (row_data.team2_id = new.team1_id AND row_data.score_t2 = new.score_t1) or (row_data.team2_id = new.team2_id and row_data.score_t2 = new.score_t2) THEN
					score := score + 2;
				END IF;
				
				IF (new.winner_id = row_data.winner_id) THEN 
					score := score + 3;
				END IF;
				
				IF (new.score_t1 = new.score_t2) AND 
					(row_data.score_t1 = row_data.score_t2) AND
					((row_data.team1_id = new.team1_id AND row_data.team2_id = new.team2_id ) OR
					(row_data.team1_id = new.team2_id AND row_data.team2_id = new.team1_id )) THEN
					score := score + 3;
				END IF;
				
				IF old.score_t1 IS NOT NULL or old.score_t2 IS NOT NULL THEN
				
					IF old.round_id <> 2 THEN
						IF row_data.team1_id = old.team1_id or row_data.team1_id = old.team2_id THEN
							score := score - 3;
						END IF;
						IF row_data.team2_id = old.team1_id or row_data.team2_id = old.team2_id THEN
							score := score - 3;
						END IF;
					END IF;
					
					-- Acertar el Score dar 2 puntos
					IF (row_data.team1_id = old.team1_id AND row_data.score_t1 = old.score_t1) or (row_data.team1_id = old.team2_id and row_data.score_t1 = old.score_t2) THEN
						score := score - 2;
					END IF;
					IF (row_data.team2_id = old.team1_id AND row_data.score_t2 = old.score_t1) or (row_data.team2_id = old.team2_id and row_data.score_t2 = old.score_t2) THEN
						score := score - 2;
					END IF;
					
					IF (old.winner_id = row_data.winner_id) THEN 
						score := score - 3;
					END IF;
					
					IF (old.score_t1 = old.score_t2) AND 
						(row_data.score_t1 = row_data.score_t2) AND
						((row_data.team1_id = old.team1_id AND row_data.team2_id = old.team2_id ) OR
						(row_data.team1_id = old.team2_id AND row_data.team2_id = old.team1_id )) THEN
							score := score - 3;
					END IF;

				END IF;
			ELSE
				IF (row_data.team1_id = new.team1_id AND row_data.score_t1 = new.score_t1) or (row_data.team1_id = new.team2_id and row_data.score_t1 = new.score_t2) THEN
					score := score + 2;
				END IF;
				IF (row_data.team2_id = new.team1_id AND row_data.score_t2 = new.score_t1) or (row_data.team2_id = new.team2_id and row_data.score_t2 = new.score_t2) THEN
					score := score + 2;
				END IF;

				IF (row_data.score_t1 > row_data.score_t2) THEN
					IF (new.score_t1 > new.score_t2) and (row_data.team1_id = new.team1_id) THEN
						score := score + 3;
					END IF;
					IF (new.score_t1 < new.score_t2) and (row_data.team1_id = new.team2_id) THEN
						score := score + 3;
					END IF;
				END IF;
				IF (row_data.score_t1 < row_data.score_t2) THEN
					IF (new.score_t1 > new.score_t2) and (row_data.team2_id = new.team1_id) THEN
						score := score + 3;
					END IF;
					IF (new.score_t1 < new.score_t2) and (row_data.team2_id = new.team2_id) THEN
						score := score + 3;
					END IF;
				END IF;
				IF (row_data.score_t1 = row_data.score_t2) THEN
					IF (new.score_t1 = new.score_t2) THEN
						score := score + 3;
					END IF;
				END IF;

				IF old.score_t1 IS NOT NULL or old.score_t2 IS NOT NULL THEN
					IF (row_data.team1_id = old.team1_id AND row_data.score_t1 = old.score_t1) or (row_data.team1_id = old.team2_id and row_data.score_t1 = old.score_t2) THEN
						score := score - 2;
					END IF;
					IF (row_data.team2_id = old.team1_id AND row_data.score_t2 = old.score_t1) or (row_data.team2_id = old.team2_id and row_data.score_t2 = old.score_t2) THEN
						score := score - 2;
					END IF;
					

					IF (row_data.score_t1 > row_data.score_t2) THEN
						IF (old.score_t1 > old.score_t2) and (row_data.team1_id = old.team1_id) THEN
							score := score - 3;
						END IF;
						IF (old.score_t1 < old.score_t2) and (row_data.team1_id = old.team2_id) THEN
							score := score - 3;
						END IF;
					END IF;
					IF (row_data.score_t1 < row_data.score_t2) THEN
						IF (old.score_t1 > old.score_t2) and (row_data.team2_id = old.team1_id) THEN
							score := score - 3;
						END IF;
						IF (old.score_t1 < old.score_t2) and (row_data.team2_id = old.team2_id) THEN
							score := score - 3;
						END IF;
					END IF;
					IF (row_data.score_t1 = row_data.score_t2) THEN
						IF (old.score_t1 = old.score_t2) THEN
							score := score - 3;
						END IF;
					END IF;

				END IF;
			END IF;

			UPDATE quinielas set points = coalesce(points,0) + score where id = row_data.quiniela_id;
		END LOOP;
	RETURN new;
EXCEPTION WHEN others THEN 

    raise notice 'The transaction is in an uncommittable state. Transaction was rolled back';

    raise notice '% %', SQLERRM, SQLSTATE;
    RETURN old;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER update_players_score ON matches;

CREATE TRIGGER update_players_score
BEFORE UPDATE OF score_t1, score_t2 ON matches
FOR EACH ROW
EXECUTE PROCEDURE update_players_score();

