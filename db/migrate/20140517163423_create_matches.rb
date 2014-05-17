class CreateMatches < ActiveRecord::Migration
  def change
    create_table :matches do |t|
      t.string :winner_name
      t.integer :score_t1
      t.integer :score_t2
      t.date :match_date
      t.timestamps
      t.integer :team1_id
      t.integer :team2_id
      t.integer :winner_id
      t.integer :round_id
    end
    Round.create :match_date => '2014-06-12 17:00:00', :team1_id => 1, :team2_id => 2,  :round_id => 1    
  end
end