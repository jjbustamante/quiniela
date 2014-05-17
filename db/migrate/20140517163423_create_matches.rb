class CreateMatches < ActiveRecord::Migration
  def change
    create_table :matches do |t|
      t.string :winner_name
      t.integer :score_t1
      t.integer :score_t2
      t.timestamp :match_date
      t.timestamps
      t.integer :winner_id
      t.integer :match_parent_id
      t.belongs_to :round
      t.belongs_to :team1
      t.belongs_to :team2
    end
    Match.create :match_date => '2014-06-12 17:00:00', :team1_id => 1, :team2_id => 2,  :round_id => 1
    Match.create :match_date => '2014-06-13 13:00:00', :team1_id => 3, :team2_id => 4,  :round_id => 1    
    Match.create :match_date => '2014-06-13 16:00:00', :team1_id => 5, :team2_id => 6,  :round_id => 1    
    Match.create :match_date => '2014-06-13 18:00:00', :team1_id => 7, :team2_id => 8,  :round_id => 1    
    Match.create :match_date => '2014-06-14 13:00:00', :team1_id => 9, :team2_id => 10,  :round_id => 1    
    Match.create :match_date => '2014-06-14 16:00:00', :team1_id => 13, :team2_id => 14,  :round_id => 1    
    Match.create :match_date => '2014-06-14 18:00:00', :team1_id => 15, :team2_id => 16,  :round_id => 1    
    Match.create :match_date => '2014-06-14 22:00:00', :team1_id => 11, :team2_id => 12,  :round_id => 1    
    Match.create :match_date => '2014-06-15 13:00:00', :team1_id => 17, :team2_id => 18,  :round_id => 1    
    Match.create :match_date => '2014-06-15 16:00:00', :team1_id => 19, :team2_id => 20,  :round_id => 1    
    Match.create :match_date => '2014-06-15 19:00:00', :team1_id => 21, :team2_id => 22,  :round_id => 1    
    Match.create :match_date => '2014-06-16 13:00:00', :team1_id => 25, :team2_id => 26,  :round_id => 1    
    Match.create :match_date => '2014-06-16 16:00:00', :team1_id => 23, :team2_id => 24,  :round_id => 1    
    Match.create :match_date => '2014-06-16 19:00:00', :team1_id => 27, :team2_id => 28,  :round_id => 1    
    Match.create :match_date => '2014-06-17 13:00:00', :team1_id => 29, :team2_id => 30,  :round_id => 1    
    Match.create :match_date => '2014-06-17 16:00:00', :team1_id => 1, :team2_id => 3,  :round_id => 1    
    Match.create :match_date => '2014-06-17 18:00:00', :team1_id => 31, :team2_id => 32,  :round_id => 1    
    Match.create :match_date => '2014-06-18 13:00:00', :team1_id => 8, :team2_id => 6,  :round_id => 1    
    Match.create :match_date => '2014-06-18 16:00:00', :team1_id => 5, :team2_id => 7,  :round_id => 1    
    Match.create :match_date => '2014-06-18 18:00:00', :team1_id => 4, :team2_id => 2,  :round_id => 1    
    Match.create :match_date => '2014-06-19 13:00:00', :team1_id => 9, :team2_id => 11,  :round_id => 1    
    Match.create :match_date => '2014-06-19 16:00:00', :team1_id => 13, :team2_id => 15,  :round_id => 1    
    Match.create :match_date => '2014-06-19 19:00:00', :team1_id => 12, :team2_id => 10,  :round_id => 1    
    Match.create :match_date => '2014-06-20 13:00:00', :team1_id => 14, :team2_id => 16,  :round_id => 1    
    Match.create :match_date => '2014-06-20 16:00:00', :team1_id => 17, :team2_id => 19,  :round_id => 1    
    Match.create :match_date => '2014-06-20 19:00:00', :team1_id => 20, :team2_id => 18,  :round_id => 1    
    
    Match.create :match_date => '2014-06-21 13:00:00', :team1_id => 21, :team2_id => 23,  :round_id => 1    
    Match.create :match_date => '2014-06-21 16:00:00', :team1_id => 25, :team2_id => 27,  :round_id => 1    
    Match.create :match_date => '2014-06-21 18:00:00', :team1_id => 24, :team2_id => 22,  :round_id => 1 

    Match.create :match_date => '2014-06-22 13:00:00', :team1_id => 29, :team2_id => 31,  :round_id => 1    
    Match.create :match_date => '2014-06-22 16:00:00', :team1_id => 32, :team2_id => 30,  :round_id => 1    
    Match.create :match_date => '2014-06-22 18:00:00', :team1_id => 28, :team2_id => 26,  :round_id => 1     
    
    Match.create :match_date => '2014-06-23 13:00:00', :team1_id => 6, :team2_id => 7,  :round_id => 1    
    Match.create :match_date => '2014-06-23 13:00:00', :team1_id => 8, :team2_id => 5,  :round_id => 1    
    Match.create :match_date => '2014-06-23 17:00:00', :team1_id => 4, :team2_id => 1,  :round_id => 1     
    Match.create :match_date => '2014-06-23 17:00:00', :team1_id => 2, :team2_id => 3,  :round_id => 1 

    Match.create :match_date => '2014-06-24 13:00:00', :team1_id => 16, :team2_id => 13,  :round_id => 1    
    Match.create :match_date => '2014-06-24 13:00:00', :team1_id => 14, :team2_id => 15,  :round_id => 1    
    Match.create :match_date => '2014-06-24 16:00:00', :team1_id => 12, :team2_id => 9,  :round_id => 1     
    Match.create :match_date => '2014-06-24 17:00:00', :team1_id => 10, :team2_id => 11,  :round_id => 1    

    Match.create :match_date => '2014-06-25 13:00:00', :team1_id => 24, :team2_id => 21,  :round_id => 1    
    Match.create :match_date => '2014-06-25 13:00:00', :team1_id => 23, :team2_id => 22,  :round_id => 1    
    Match.create :match_date => '2014-06-25 16:00:00', :team1_id => 20, :team2_id => 17,  :round_id => 1     
    Match.create :match_date => '2014-06-25 17:00:00', :team1_id => 18, :team2_id => 19,  :round_id => 1    

     Match.create :match_date => '2014-06-26 13:00:00', :team1_id => 26, :team2_id => 27,  :round_id => 1    
    Match.create :match_date => '2014-06-26 13:00:00', :team1_id => 28, :team2_id => 25,  :round_id => 1    
    Match.create :match_date => '2014-06-26 17:00:00', :team1_id => 32, :team2_id => 29,  :round_id => 1     
    Match.create :match_date => '2014-06-26 17:00:00', :team1_id => 30, :team2_id => 31,  :round_id => 1     

   
  end
end