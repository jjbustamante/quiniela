class Quiniela < ActiveRecord::Base
	belongs_to :user
  # attr_accessible :title, :body
  has_many :bets, dependent: :destroy
  belongs_to :user
  attr_accessible :name, :points, :user_id
end
