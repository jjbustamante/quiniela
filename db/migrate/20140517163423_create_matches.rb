class CreateMatches < ActiveRecord::Migration
  def change
    create_table :matches do |t|
      t.string :winner_name
      t.integer :score_t1
      t.integer :score_t2
      t.timestamp :match_date
      t.timestamps
      t.integer :winner_id
      t.integer :match_parent1_id
      t.integer :match_parent2_id
      t.references :round
      t.references :team1
      t.references :team2
      t.integer :played, :default => 0
      t.integer :editable, :default => 0
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

     Match.create :match_date => '2014-06-28 13:00:00', :team1_id => 33, :team2_id => 34,  :round_id => 2, :editable => 1  #1a-2b 49  
     Match.create :match_date => '2014-06-28 17:00:00', :team1_id => 35, :team2_id => 36,  :round_id => 2, :editable => 1  #1c-2d 50   
     Match.create :match_date => '2014-06-29 13:00:00', :team1_id => 41, :team2_id => 42,  :round_id => 2, :editable => 1  #1b-2a 51   
     Match.create :match_date => '2014-06-29 17:00:00', :team1_id => 43, :team2_id => 44,  :round_id => 2, :editable => 1  #1d-2c 52   
     Match.create :match_date => '2014-06-30 13:00:00', :team1_id => 37, :team2_id => 38,  :round_id => 2, :editable => 1  #1e-2f 53   
     Match.create :match_date => '2014-06-30 17:00:00', :team1_id => 39, :team2_id => 40,  :round_id => 2, :editable => 1  #1g-2h 54   
     Match.create :match_date => '2014-07-01 13:00:00', :team1_id => 45, :team2_id => 46,  :round_id => 2, :editable => 1  #1f-2e 55   
     Match.create :match_date => '2014-07-01 17:00:00', :team1_id => 47, :team2_id => 48,  :round_id => 2, :editable => 1  #1h-2g 56

     Match.create :match_date => '2014-07-04 13:00:00', :team1_id => 53, :team2_id => 54, :match_parent1_id => 49, :match_parent2_id => 50,  :round_id => 3, :editable => 1      
     Match.create :match_date => '2014-07-04 17:00:00', :team1_id => 49, :team2_id => 50, :match_parent1_id => 51, :match_parent2_id => 52,  :round_id => 3, :editable => 1 
     Match.create :match_date => '2014-07-05 13:00:00', :team1_id => 55, :team2_id => 56, :match_parent1_id => 53, :match_parent2_id => 54,  :round_id => 3, :editable => 1      
     Match.create :match_date => '2014-07-05 17:00:00', :team1_id => 51, :team2_id => 52, :match_parent1_id => 55, :match_parent2_id => 56,  :round_id => 3, :editable => 1 

     Match.create :match_date => '2014-07-08 17:00:00', :team1_id => 57, :team2_id => 58, :match_parent1_id => 57, :match_parent2_id => 58,  :round_id => 4, :editable => 1      
     Match.create :match_date => '2014-07-09 17:00:00', :team1_id => 59, :team2_id => 60, :match_parent1_id => 59, :match_parent2_id => 60,  :round_id => 4, :editable => 1

     Match.create :match_date => '2014-07-12 17:00:00', :team1_id => 61, :team2_id => 62, :match_parent1_id => 61, :match_parent2_id => 62,  :round_id => 5, :editable => 1

     Match.create :match_date => '2014-07-13 16:00:00', :team1_id => 63, :team2_id => 64, :match_parent1_id => 61, :match_parent2_id => 62,  :round_id => 6, :editable => 1
  end
end