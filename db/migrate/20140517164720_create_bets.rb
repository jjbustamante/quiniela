class CreateBets < ActiveRecord::Migration
  def change
    create_table :bets do |t|
      t.integer :score_t1
      t.integer :score_t2
      t.timestamps
      t.integer :winner_id
      t.references :match_id
      t.references :quiniela
      t.references :team1
      t.references :team2
    end
  end
end
