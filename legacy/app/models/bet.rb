class Bet < ActiveRecord::Base
  # attr_accessible :title, :body
  belongs_to :match
  belongs_to :quiniela
  belongs_to :team1, :class_name => "Team"
  belongs_to :team2, :class_name => "Team"
  attr_accessible :match_id, :team1_id, :team2_id, :score_t1, :score_t2, :quiniela_id,:winner_id
end
