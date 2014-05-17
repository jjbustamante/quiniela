class Match < ActiveRecord::Base
	belongs_to :round
	belongs_to :team1, :class_name => "Team"
	belongs_to :team2, :class_name => "Team"
  attr_accessible :match_date, :team1_id, :team2_id, :round_id
end
