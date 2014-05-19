class Match < ActiveRecord::Base
	belongs_to :round
	belongs_to :team1, :class_name => "Team"
	belongs_to :team2, :class_name => "Team"
  attr_accessible :match_date, :team1_id, :team2_id, :round_id,:score_t1,:score_t2,:played,:winner_id, :winner_name, :editable, :match_parent1_id, :match_parent2_id
end
