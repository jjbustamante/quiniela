class Team < ActiveRecord::Base
  # attr_accessible :title, :body
  has_many :matches, dependent: :destroy
  attr_accessible :name, :code, :group
end
