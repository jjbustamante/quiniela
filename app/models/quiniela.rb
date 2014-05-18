class Quiniela < ActiveRecord::Base
	belongs_to :user
  # attr_accessible :title, :body
  has_many :bets, dependent: :destroy
  accepts_nested_attributes_for :bets
  belongs_to :user
  attr_accessible :name, :points, :user_id
end
