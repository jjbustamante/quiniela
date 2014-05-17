CREATE TRIGGER update_players_score_trigger
AFTER UPDATE OF resultado_columna ON partidos_reales_tabla
FOR EACH ROW
EXECUTE PROCEDURE p_update_players_score(new.partido_id);


CREATE OR REPLACE FUNCTION p_update_players_score(partido_id partidos_reales_tabla.partido_id%TYPE)
RETURNS VOID AS $$
DECLARE
	ref refcursor;
	row_data BET%ROWTYPE;
	score integer;
	match MATCH%ROWTYPE;
BEGIN
	SELECT * into match 
	FROM MATCH
	WHERE MATCH.partido_id = partido_id;
	BEGIN
	FOR row_data IN SELECT * FROM BET
	WHERE BET.partido_id = partido_id LOOP
		score := 0;
		IF match.ronda_id = '2' THEN
			IF row_data.EQUIPO1 = match.equipo1 or row_data.EQUIPO1 = match.equipo2 THEN
				score := score + 1;
			END IF;
			IF row_data.EQUIPO2 = match.equipo1 or row_data.EQUIPO2 = match.equipo2 THEN
				score := score + 1;
			END IF;
			IF (row_data.EQUIPO1 = match.equipo1 AND row_data.resultado1 = match.resultado1) or (row_data.EQUIPO1 = match.equipo2 and row_data.resultado1 = match.resultado2) THEN
				score := score + 1;
			END IF;
			IF (row_data.EQUIPO2 = match.equipo1 AND row_data.resultado2 = match.resultado1) or (row_data.EQUIPO2 = match.equipo2 and row_data.resultado2 = match.resultado2) THEN
				score := score + 1;
			END IF;
			IF ((row_data.EQUIPO2 = match.equipo1 AND row_data.resultado2 = match.resultado1) or (row_data.EQUIPO2 = match.equipo2 and row_data.resultado2 = match.resultado2)) and
			 ((row_data.EQUIPO1 = match.equipo1 AND row_data.resultado1 = match.resultado1) or (row_data.EQUIPO1 = match.equipo2 and row_data.resultado1 = match.resultado2)) THEN
				score := score + 2;
			END IF;
		ELSE
			IF (row_data.EQUIPO1 = match.equipo1 AND row_data.resultado1 = match.resultado1) or (row_data.EQUIPO1 = match.equipo2 and row_data.resultado1 = match.resultado2) THEN
				score := score + 1;
			END IF;
			IF (row_data.EQUIPO2 = match.equipo1 AND row_data.resultado2 = match.resultado1) or (row_data.EQUIPO2 = match.equipo2 and row_data.resultado2 = match.resultado2) THEN
				score := score + 1;
			END IF;
			IF ((row_data.EQUIPO2 = match.equipo1 AND row_data.resultado2 = match.resultado1) or (row_data.EQUIPO2 = match.equipo2 and row_data.resultado2 = match.resultado2)) and
			 ((row_data.EQUIPO1 = match.equipo1 AND row_data.resultado1 = match.resultado1) or (row_data.EQUIPO1 = match.equipo2 and row_data.resultado1 = match.resultado2)) THEN
				score := score + 2;
			END IF;
		END IF;
		UPDATE quiniela as q set q.score = q.score + score where q.quiniela_id = row_data.quiniela_id;
	END LOOP;
	COMMIT;
	RETURN;
END;
$$ language 'plpgsql';