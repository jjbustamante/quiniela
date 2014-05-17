class CreateBets < ActiveRecord::Migration
  def change
    create_table :bets do |t|
      t.integer :score_t1
      t.integer :score_t2
      t.timestamps
      t.integer :team1_id
      t.integer :team2_id
      t.integer :winner_id
      t.integer :match_id
    end
  end
end
