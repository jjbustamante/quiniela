class Round < ActiveRecord::Base
	has_many :matches, dependent: :destroy
  attr_accessible :name, :status, :start_date, :end_date, :round_type
end
