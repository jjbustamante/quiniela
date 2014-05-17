class Round < ActiveRecord::Base
  attr_accessible :name, :status, :start_date, :end_date, :round_type
end
